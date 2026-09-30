@file:OptIn(ExperimentalSerializationApi::class)

package com.tachiup.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/*
 * Wire formats of an extension store, mirroring what Mihon (and Komikku, which follows it) read.
 *
 * Current stores publish a protobuf index (usually gzipped), linked from the legacy repo.json
 * through `index_v2`. Keiyoushi's old index.min.json only lists "Outdated App" placeholders now.
 * Fields TachiUp doesn't need are left out; protobuf skips unknown fields.
 */

@Serializable
internal class ProtoStore(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val badgeLabel: String = "",
    @ProtoNumber(3) val signingKey: String = "",
    @ProtoNumber(4) val contact: ProtoContact? = null,
    @ProtoNumber(101) val extensionList: ProtoExtensionList? = null,
    @ProtoNumber(102) val extensionListUrl: String? = null,
)

@Serializable
internal class ProtoContact(
    @ProtoNumber(1) val website: String = "",
    @ProtoNumber(2) val discord: String? = null,
)

@Serializable
internal class ProtoExtensionList(
    @ProtoNumber(1) val extensions: List<ProtoExtension> = emptyList(),
)

@Serializable
internal class ProtoExtension(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val packageName: String = "",
    @ProtoNumber(3) val resources: ProtoResources = ProtoResources(),
    @ProtoNumber(4) val extensionLib: String = "",
    @ProtoNumber(5) val versionCode: Long = 0,
    @ProtoNumber(6) val versionName: String = "",
    // Kept as a raw number so an unknown future rating can't fail the whole index.
    @ProtoNumber(7) val contentWarning: Int = 0,
    @ProtoNumber(8) val sources: List<ProtoSource> = emptyList(),
)

@Serializable
internal class ProtoResources(
    @ProtoNumber(1) val apkUrl: String = "",
    @ProtoNumber(2) val iconUrl: String = "",
)

@Serializable
internal class ProtoSource(
    @ProtoNumber(1) val id: Long = 0,
    @ProtoNumber(2) val name: String = "",
    @ProtoNumber(3) val language: String = "",
    @ProtoNumber(4) val homeUrl: String = "",
)

/** Legacy `repo.json`: store metadata, optionally pointing at the protobuf index. */
@Serializable
internal class LegacyRepoJson(
    @SerialName("index_v2") val indexV2: String? = null,
    val meta: Meta,
) {
    @Serializable
    class Meta(
        val name: String,
        val shortName: String? = null,
        val website: String = "",
        val signingKeyFingerprint: String = "",
    )
}

/** Entry of a legacy `index.min.json`. */
@Serializable
internal class LegacyExtension(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String = "",
    val code: Long = 0,
    val version: String,
    val nsfw: Int = 0,
    val sources: List<LegacySource>? = null,
) {
    @Serializable
    class LegacySource(
        val id: String = "",
        val lang: String = "",
        val name: String = "",
        val baseUrl: String = "",
    )
}

internal fun ProtoStore.toExtensionStore(
    repo: ExtensionRepo,
    indexUrl: String,
    list: ProtoExtensionList,
) = ExtensionStore(
    repo = repo,
    name = name.ifBlank { repo.name },
    badgeLabel = badgeLabel,
    signingKey = signingKey.lowercase(),
    website = contact?.website.orEmpty(),
    discord = contact?.discord,
    indexUrl = indexUrl,
    extensions = list.extensions.map { it.toRepoExtension() },
)

internal fun ProtoExtension.toRepoExtension(): RepoExtension {
    val langs = sources.map { it.language }.toSet()
    return RepoExtension(
        name = name,
        pkg = packageName,
        apkUrl = resources.apkUrl,
        iconUrl = resources.iconUrl,
        libVersion = extensionLib,
        versionCode = versionCode,
        versionName = versionName,
        lang = if (langs.size == 1) langs.first() else "all",
        contentWarning = when (contentWarning) {
            2 -> ContentWarning.MIXED
            3 -> ContentWarning.NSFW
            else -> ContentWarning.SAFE
        },
        sources = sources.map { RepoSource(it.id, it.name, it.language, it.homeUrl) },
    )
}

internal fun LegacyRepoJson.toExtensionStore(
    repo: ExtensionRepo,
    indexUrl: String,
    extensions: List<RepoExtension>,
) = ExtensionStore(
    repo = repo,
    name = meta.name,
    badgeLabel = meta.shortName ?: meta.name,
    signingKey = meta.signingKeyFingerprint.lowercase(),
    website = meta.website,
    discord = null,
    indexUrl = indexUrl,
    extensions = extensions,
)

internal fun LegacyExtension.toRepoExtension(baseUrl: String) = RepoExtension(
    name = name.removePrefix("Tachiyomi: "),
    pkg = pkg,
    apkUrl = "$baseUrl/apk/$apk",
    iconUrl = "$baseUrl/icon/$pkg.png",
    libVersion = version.substringBeforeLast('.'),
    versionCode = code,
    versionName = version,
    lang = lang,
    contentWarning = if (nsfw == 1) ContentWarning.NSFW else ContentWarning.SAFE,
    sources = sources.orEmpty().map {
        RepoSource(it.id.toLongOrNull() ?: 0, it.name, it.lang, it.baseUrl)
    },
)
