package com.tachiup.data

import android.content.Context
import android.content.Intent
import android.net.Uri

/** A Mihon-family reader (Komikku, Mihon, …) that can register extension stores. */
data class ReaderApp(val pkg: String, val label: String)

/**
 * Mihon and its forks only trust an extension automatically when its signing key matches a store
 * added in the app; anything else needs a manual "Trust", repeated after every update. Adding the
 * store once through the app's `tachiyomi://add-repo` deep link removes that for every extension
 * signed with the store's key.
 */
object ReaderApps {

    /** Listed first when installed; any other app handling the deep link follows. */
    private val PREFERRED = listOf("app.komikku.beta", "app.komikku", "app.mihon")

    fun addStoreUri(storeUrl: String): Uri =
        Uri.parse("tachiyomi://add-repo").buildUpon().appendQueryParameter("url", storeUrl).build()

    fun find(context: Context): List<ReaderApp> {
        val pm = context.packageManager
        val probe = Intent(Intent.ACTION_VIEW, addStoreUri(Repos.KEIYOUSHI.storeUrl))
        @Suppress("DEPRECATION")
        val handlers = pm.queryIntentActivities(probe, 0)
        return handlers
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .map { pkg ->
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg)
                ReaderApp(pkg, label)
            }
            .sortedWith(
                compareBy<ReaderApp> { PREFERRED.indexOf(it.pkg).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }
                    .thenBy { it.label.lowercase() },
            )
    }

    /** Opens [app]'s "add extension store" confirmation for [repo]. */
    fun addStoreIntent(app: ReaderApp, repo: ExtensionRepo): Intent =
        Intent(Intent.ACTION_VIEW, addStoreUri(repo.storeUrl))
            .setPackage(app.pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
