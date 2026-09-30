package com.tachiup.install

import com.tachiup.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File

/**
 * Silent installer that pipes an APK to `pm install` through a privileged
 * process provided by Shizuku. Requires the Shizuku service to be running and
 * the permission to be granted.
 */
object ShizukuInstaller {

    fun isAvailable(): Boolean = try {
        Shizuku.pingBinder() && !Shizuku.isPreV11()
    } catch (_: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    /**
     * Installs [apk]. If the installed copy can't be replaced in place (different signing key, or
     * a higher version code from another source) and [pkg] is known, automatically uninstalls the
     * existing extension and retries (the silent path is intended for power users, so this keeps
     * updates working and lets foreign-signed builds be swapped for the store's).
     */
    suspend fun install(apk: File, pkg: String?): InstallResult = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext InstallResult.Failure("Shizuku is not running")
        if (!hasPermission()) return@withContext InstallResult.Failure("Shizuku permission not granted")

        val first = runInstall(apk)
        if (first is InstallResult.Success) return@withContext first

        val message = (first as? InstallResult.Failure)?.message.orEmpty()
        val conflict = when {
            isSignatureMismatch(message) -> "signature mismatch"
            message.contains("INSTALL_FAILED_VERSION_DOWNGRADE") -> "installed version is newer"
            else -> null
        }
        if (pkg != null && conflict != null) {
            Logger.w("Shizuku: $conflict for $pkg, uninstalling and retrying")
            val uninstall = runCommand(arrayOf("pm", "uninstall", pkg))
            Logger.i("Shizuku: pm uninstall $pkg -> exit=${uninstall.exit} ${uninstall.combined().trim()}")
            return@withContext runInstall(apk)
        }
        first
    }

    suspend fun uninstall(pkg: String): InstallResult = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext InstallResult.Failure("Shizuku is not running")
        if (!hasPermission()) return@withContext InstallResult.Failure("Shizuku permission not granted")
        val r = runCommand(arrayOf("pm", "uninstall", pkg))
        Logger.i("Shizuku: pm uninstall $pkg -> exit=${r.exit} ${r.combined().trim()}")
        if (r.exit == 0 && r.stdout.contains("Success")) {
            InstallResult.Success
        } else {
            InstallResult.Failure(r.combined().trim().ifBlank { "pm uninstall exited ${r.exit}" })
        }
    }

    private fun runInstall(apk: File): InstallResult {
        return try {
            val size = apk.length()
            Logger.d("Shizuku: pm install -r -S $size (${apk.name})")
            val process = newProcess(arrayOf("pm", "install", "-r", "-S", size.toString()))
            process.outputStream.use { out ->
                apk.inputStream().use { input -> input.copyTo(out) }
                out.flush()
            }
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exit = process.waitFor()
            val combined = (stdout + stderr).trim()
            Logger.i("Shizuku: pm install exit=$exit ${combined.ifBlank { "(no output)" }}")
            if (exit == 0 && stdout.contains("Success")) {
                InstallResult.Success
            } else {
                InstallResult.Failure(combined.ifBlank { "pm install exited $exit" })
            }
        } catch (t: Throwable) {
            Logger.e("Shizuku: install threw", t)
            InstallResult.Failure(t.message ?: "Shizuku install failed")
        }
    }

    private data class CmdResult(val exit: Int, val stdout: String, val stderr: String) {
        fun combined() = (stdout + stderr)
    }

    private fun runCommand(cmd: Array<String>): CmdResult = try {
        val p = newProcess(cmd)
        val out = p.inputStream.bufferedReader().readText()
        val err = p.errorStream.bufferedReader().readText()
        CmdResult(p.waitFor(), out, err)
    } catch (t: Throwable) {
        Logger.e("Shizuku: command ${cmd.joinToString(" ")} threw", t)
        CmdResult(-1, "", t.message ?: "")
    }

    private fun isSignatureMismatch(message: String): Boolean =
        message.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE") ||
            message.contains("signatures do not match") ||
            message.contains("INCONSISTENT_CERTIFICATES")

    /** Shizuku.newProcess is a restricted API; reach it reflectively. */
    private fun newProcess(cmd: Array<String>): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(null, cmd, null, null) as Process
    }
}
