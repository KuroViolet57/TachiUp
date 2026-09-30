package com.tachiup.data

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import com.tachiup.util.Logger
import java.security.MessageDigest

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
                signatures = signaturesOf(pm, info.packageName),
            )
        }
        return result.sortedBy { it.label.lowercase() }
    }

    /**
     * SHA-256 fingerprints of the package's signing certificates, computed the way Mihon/Komikku
     * do when deciding whether an extension is trusted.
     */
    private fun signaturesOf(pm: PackageManager, pkg: String): List<String> = try {
        val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
            when {
                signingInfo == null -> null
                signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners
                else -> signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
        }
        signatures.orEmpty().map { sha256(it.toByteArray()) }
    } catch (e: Exception) {
        Logger.w("Couldn't read signatures of $pkg", e)
        emptyList()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val PKG_PREFIX = "eu.kanade.tachiyomi.extension"
        private const val METADATA_EXT = "tachiyomi.extension"
        private const val METADATA_EXT_ANIME = "tachiyomi.animeextension"
        private const val METADATA_EXT_CLASS = "tachiyomi.extension.class"
    }
}
