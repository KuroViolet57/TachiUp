package com.tachiup.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Installer that uses the platform PackageInstaller. This surfaces the system
 * confirmation dialog (unless the app is a device owner), so a successful
 * commit returns [InstallResult.PendingUserAction].
 */
object SessionInstaller {

    private const val ACTION = "com.tachiup.INSTALL_RESULT"

    suspend fun install(context: Context, apk: File): InstallResult = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL,
            )
            val sessionId = pm.createSession(params)
            pm.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { input -> input.copyTo(out) }
                    session.fsync(out)
                }
                val intent = Intent(ACTION).setPackage(context.packageName)
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
            InstallResult.PendingUserAction
        } catch (t: Throwable) {
            InstallResult.Failure(t.message ?: "Install failed")
        }
    }

    /** Registered for the lifetime of the app to forward the confirmation UI. */
    class ResultReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE,
            )
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                confirm?.let { context.startActivity(it) }
            }
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
