package com.tachiup.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Content rating a store declares for an extension. */
enum class ContentWarning { SAFE, MIXED, NSFW }

/** A single source declared inside an extension entry of a store. */
data class RepoSource(
    val id: Long,
    val name: String,
    val lang: String,
    val baseUrl: String,
)

/** One extension listed by an extension store. */
data class RepoExtension(
    val name: String,
    val pkg: String,
    val apkUrl: String,
    val iconUrl: String,
    val libVersion: String,
    val versionCode: Long,
    val versionName: String,
    val lang: String,
    val contentWarning: ContentWarning,
    val sources: List<RepoSource>,
) {
    /** File name the APK is saved under while downloading. */
    val apkFileName: String
        get() = apkUrl.substringBefore('?').substringAfterLast('/').ifBlank { "$pkg.apk" }
}

/** Definition of an extension store TachiUp reads from. */
data class ExtensionRepo(
    val key: String,
    val name: String,
    /**
     * URL the store is registered under in Mihon/Komikku. TachiUp resolves it the same way
     * those apps do (legacy `index.min.json` → `repo.json` → `index_v2`), so it keeps working
     * when the store moves its index.
     */
    val storeUrl: String,
    val discordUrl: String,
    val githubRepo: String, // "owner/repo" for the GitHub API
)

/** A store after resolving its index: its metadata and the extensions it currently lists. */
data class ExtensionStore(
    val repo: ExtensionRepo,
    val name: String,
    val badgeLabel: String,
    /** SHA-256 of the store's signing certificate, lowercase hex. */
    val signingKey: String,
    val website: String,
    val discord: String?,
    /** The index the extension list was actually read from. */
    val indexUrl: String,
    val extensions: List<RepoExtension>,
)

/** An extension already installed on the device. */
data class InstalledExtension(
    val pkg: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    /** SHA-256 fingerprints of the signing certificates, in the same format as [ExtensionStore.signingKey]. */
    val signatures: List<String>,
)

enum class UpdateState { UPDATE_AVAILABLE, FOREIGN_SIGNATURE, UP_TO_DATE, NOT_IN_REPO }

/** Joined view: an installed extension matched against the stores. */
data class ExtensionStatus(
    val installed: InstalledExtension,
    val repoExtension: RepoExtension?,
    val store: ExtensionStore?,
) {
    val pkg: String get() = installed.pkg
    val label: String get() = installed.label
    val installedVersion: String get() = installed.versionName
    val latestVersion: String? get() = repoExtension?.versionName

    /**
     * True when the installed APK is signed with a different key than its store. Mihon/Komikku
     * only trust extensions automatically when their key matches a store added in the app, so
     * these keep asking to be trusted until they're replaced with the store's build.
     */
    val foreignSigner: Boolean
        get() = store != null && store.signingKey.isNotBlank() &&
            installed.signatures.isNotEmpty() && store.signingKey !in installed.signatures

    val state: UpdateState
        get() = when {
            repoExtension == null -> UpdateState.NOT_IN_REPO
            repoExtension.versionCode > installed.versionCode -> UpdateState.UPDATE_AVAILABLE
            foreignSigner -> UpdateState.FOREIGN_SIGNATURE
            else -> UpdateState.UP_TO_DATE
        }
}

/** An extension available in a store, shown in the browse/catalog screen. */
data class CatalogEntry(
    val store: ExtensionStore,
    val ext: RepoExtension,
    val installed: InstalledExtension?,
) {
    val pkg: String get() = ext.pkg
    val isInstalled: Boolean get() = installed != null
    val installedVersion: String? get() = installed?.versionName
    val hasUpdate: Boolean get() = installed != null && ext.versionCode > installed.versionCode
    val nsfw: Boolean get() = ext.contentWarning == ContentWarning.NSFW
}

/** A GitHub issue surfaced in the community screen. */
@Serializable
data class GithubIssue(
    val number: Int = 0,
    val title: String = "",
    val state: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val comments: Int = 0,
    @SerialName("pull_request") val pullRequest: PullRequestRef? = null,
)

@Serializable
data class PullRequestRef(val url: String = "")
