package com.tachiup.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A single source declared inside an extension entry of a repo index. */
@Serializable
data class RepoSource(
    val name: String = "",
    val lang: String = "",
    val id: String = "",
    val baseUrl: String = "",
)

/** One extension entry as listed in a repo's index.min.json. */
@Serializable
data class RepoExtension(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String = "",
    @SerialName("code") val code: Int = 0,
    val version: String,
    val nsfw: Int = 0,
    val sources: List<RepoSource> = emptyList(),
)

/** Definition of a Tachiyomi extension repository. */
data class ExtensionRepo(
    val key: String,
    val name: String,
    val baseUrl: String,
    val discordUrl: String,
    val githubRepo: String, // "owner/repo" for the GitHub API
) {
    val indexUrl: String get() = "$baseUrl/index.min.json"
    fun apkUrl(apk: String): String = "$baseUrl/apk/$apk"
}

/** An extension already installed on the device. */
data class InstalledExtension(
    val pkg: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
)

enum class UpdateState { UPDATE_AVAILABLE, UP_TO_DATE, NOT_IN_REPO }

/** Joined view: an installed extension matched against the repositories. */
data class ExtensionStatus(
    val pkg: String,
    val label: String,
    val installedVersion: String,
    val repoExtension: RepoExtension?,
    val repo: ExtensionRepo?,
) {
    val state: UpdateState
        get() = when {
            repoExtension == null -> UpdateState.NOT_IN_REPO
            repoExtension.version != installedVersion -> UpdateState.UPDATE_AVAILABLE
            else -> UpdateState.UP_TO_DATE
        }

    val latestVersion: String? get() = repoExtension?.version
}

/** An extension available in a repo, shown in the browse/catalog screen. */
data class CatalogEntry(
    val repo: ExtensionRepo,
    val ext: RepoExtension,
    val installedVersion: String?,
) {
    val pkg: String get() = ext.pkg
    val installed: Boolean get() = installedVersion != null
    val hasUpdate: Boolean get() = installedVersion != null && installedVersion != ext.version
    val nsfw: Boolean get() = ext.nsfw == 1
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
