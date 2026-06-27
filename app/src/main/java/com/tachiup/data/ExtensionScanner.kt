package com.tachiup.data

import android.content.Context
import android.content.pm.PackageManager

/** Scans installed packages for Tachiyomi/Mihon extensions. */
class ExtensionScanner(private val context: Context) {

    fun scan(): List<InstalledExtension> {
        val pm = context.packageManager
        val flags = PackageManager.GET_META_DATA
        val packages = pm.getInstalledPackages(flags)
        val result = mutableListOf<InstalledExtension>()

        for (info in packages) {
            val appInfo = info.applicationInfo ?: continue
            val meta = appInfo.metaData
            val isExtension = (meta != null && (
                meta.containsKey(METADATA_EXT) ||
                    meta.containsKey(METADATA_EXT_ANIME) ||
                    meta.containsKey(METADATA_EXT_CLASS)
                )) || info.packageName.startsWith(PKG_PREFIX)

            if (!isExtension) continue

            val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
                .getOrDefault(info.packageName)
                .removePrefix("Tachiyomi: ")
                .removePrefix("Aniyomi: ")

            @Suppress("DEPRECATION")
            val versionCode = info.longVersionCode

            result += InstalledExtension(
                pkg = info.packageName,
                label = label,
                versionName = info.versionName ?: "?",
                versionCode = versionCode,
            )
        }
        return result.sortedBy { it.label.lowercase() }
    }

    companion object {
        private const val PKG_PREFIX = "eu.kanade.tachiyomi.extension"
        private const val METADATA_EXT = "tachiyomi.extension"
        private const val METADATA_EXT_ANIME = "tachiyomi.animeextension"
        private const val METADATA_EXT_CLASS = "tachiyomi.extension.class"
    }
}
