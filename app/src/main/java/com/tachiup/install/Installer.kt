package com.tachiup.install

import android.content.Context
import java.io.File

/** Routes installs to Shizuku (silent) when requested/available, else the platform installer. */
class Installer(private val context: Context) {

    suspend fun install(apk: File, preferShizuku: Boolean): InstallResult {
        if (preferShizuku && ShizukuInstaller.isAvailable()) {
            if (!ShizukuInstaller.hasPermission()) {
                return InstallResult.Failure("Grant Shizuku permission in Settings first")
            }
            return ShizukuInstaller.install(apk)
        }
        return SessionInstaller.install(context, apk)
    }
}
