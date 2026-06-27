package com.tachiup.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import com.tachiup.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Installer that uses the platform PackageInstaller. This surfaces the system
 * confirmation dialog (unless the app is privileged), so a successful commit
 * returns [InstallResult.PendingUserAction]; the real outcome is reported
 * asynchronously through [ResultReceiver] into the [Logger].
 */
object SessionInstaller {

    private const val ACTION = "com.tachiup.INSTALL_RESULT"
    private const val EXTRA_LABEL = "com.tachiup.LABEL"

    suspend fun install(context: Context, apk: File, label: String): InstallResult =
        withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL,
                )
                val sessionId = pm.createSession(params)
                Logger.d("Session: created session $sessionId for $label")
                pm.openSession(sessionId).use { session ->
                    session.openWrite("base.apk", 0, apk.length()).use { out ->
                        apk.inputStream().use { input -> input.copyTo(out) }
                        session.fsync(out)
                    }
                    val intent = Intent(ACTION)
                        .setPackage(context.packageName)
                        .putExtra(EXTRA_LABEL, label)
                    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        android.app.PendingIntent.FLAG_MUTABLE
                    } else {
                        0
                    }
                    val pi = android.app.PendingIntent.getBroadcast(
                        context, sessionId, intent, flags,
                    )
                    session.commit(pi.intentSender)
                }
                Logger.i("Session: committed install for $label (awaiting result)")
                InstallResult.PendingUserAction
            } catch (t: Throwable) {
                Logger.e("Session: install failed for $label", t)
                InstallResult.Failure(t.message ?: "Install failed")
            }
        }

    /** Registered for the lifetime of the app to forward the confirmation UI and log results. */
    class ResultReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val label = intent.getStringExtra(EXTRA_LABEL) ?: "extension"
            val status = intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE,
            )
            val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    Logger.i("Session: confirmation required for $label — opening installer")
                    @Suppress("DEPRECATION")
                    val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    if (confirm == null) {
                        Logger.e("Session: no confirmation intent provided for $label")
                    } else {
                        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(confirm) }
                            .onFailure { Logger.e("Session: failed to launch installer for $label", it) }
                    }
                }
                PackageInstaller.STATUS_SUCCESS ->
                    Logger.i("✓ Session: $label installed")
                else -> {
                    val name = statusName(status)
                    Logger.e("✗ Session: $label failed — $name${message?.let { ": $it" } ?: ""}")
                    if (status == PackageInstaller.STATUS_FAILURE_CONFLICT ||
                        message?.contains("signatures do not match") == true ||
                        message?.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE") == true
                    ) {
                        Logger.w(
                            "Hint: $label is already installed with a different signing key. " +
                                "Uninstall the existing extension first, or enable Shizuku silent install " +
                                "(it can reinstall automatically).",
                        )
                    }
                }
            }
        }

        private fun statusName(status: Int): String = when (status) {
            PackageInstaller.STATUS_FAILURE -> "FAILURE"
            PackageInstaller.STATUS_FAILURE_ABORTED -> "ABORTED"
            PackageInstaller.STATUS_FAILURE_BLOCKED -> "BLOCKED"
            PackageInstaller.STATUS_FAILURE_CONFLICT -> "CONFLICT"
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "INCOMPATIBLE"
            PackageInstaller.STATUS_FAILURE_INVALID -> "INVALID"
            PackageInstaller.STATUS_FAILURE_STORAGE -> "STORAGE"
            else -> "status $status"
        }

        companion object {
            fun register(context: Context) {
                val filter = IntentFilter(ACTION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(
                        ResultReceiver(), filter, Context.RECEIVER_NOT_EXPORTED,
                    )
                } else {
                    @Suppress("UnspecifiedRegisterReceiverFlag")
                    context.registerReceiver(ResultReceiver(), filter)
                }
            }
        }
    }
}
