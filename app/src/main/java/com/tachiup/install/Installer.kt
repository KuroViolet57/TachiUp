package com.tachiup.install

import android.content.Context
import com.tachiup.util.Logger
import java.io.File

/** Routes installs to Shizuku (silent) when requested/available, else the platform installer. */
class Installer(private val context: Context) {

    suspend fun install(
        apk: File,
        pkg: String,
        label: String,
        preferShizuku: Boolean,
    ): InstallResult {
        if (preferShizuku) {
            if (!ShizukuInstaller.isAvailable()) {
                Logger.w("Shizuku requested but not running — falling back to system installer")
            } else if (!ShizukuInstaller.hasPermission()) {
                return InstallResult.Failure("Grant Shizuku permission in Settings first")
            } else {
                Logger.i("Installing $label via Shizuku (silent)")
                return ShizukuInstaller.install(apk, pkg)
            }
        }
        Logger.i("Installing $label via system package installer")
        return SessionInstaller.install(context, apk, label)
    }

    /** Silent uninstall via Shizuku. The system path is handled by the Activity. */
    suspend fun uninstallSilent(pkg: String, label: String): InstallResult {
        Logger.i("Uninstalling $label via Shizuku")
        return ShizukuInstaller.uninstall(pkg)
    }
}
