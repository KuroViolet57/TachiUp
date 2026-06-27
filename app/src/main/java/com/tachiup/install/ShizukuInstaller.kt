package com.tachiup.install

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

    suspend fun install(apk: File): InstallResult = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext InstallResult.Failure("Shizuku is not running")
        if (!hasPermission()) return@withContext InstallResult.Failure("Shizuku permission not granted")

        try {
            val size = apk.length()
            val process = newProcess(arrayOf("sh", "-c", "pm install -r -S $size"))
            process.outputStream.use { out ->
                apk.inputStream().use { input -> input.copyTo(out) }
                out.flush()
            }
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exit = process.waitFor()
            if (exit == 0 && (stdout.contains("Success") || stderr.isBlank())) {
                InstallResult.Success
            } else {
                InstallResult.Failure((stderr + stdout).trim().ifBlank { "pm install exited $exit" })
            }
        } catch (t: Throwable) {
            InstallResult.Failure(t.message ?: "Shizuku install failed")
        }
    }

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
