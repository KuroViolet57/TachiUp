package com.tachiup.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/** Network access to extension stores, APKs and GitHub issues. */
@OptIn(ExperimentalSerializationApi::class)
class RepoApi(
    private val client: OkHttpClient = defaultClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Resolves [ExtensionRepo.storeUrl] to the store's current index and reads its extensions. */
    suspend fun fetchStore(repo: ExtensionRepo): ExtensionStore = withContext(Dispatchers.IO) {
        resolveStore(repo, repo.storeUrl, hops = 0)
    }

    /**
     * Follows the same steps as Mihon/Komikku: a legacy extension list leads to the repo.json next
     * to it, a repo.json with `index_v2` leads to the protobuf index, and anything that isn't JSON
     * is decoded as a protobuf store.
     */
    private fun resolveStore(repo: ExtensionRepo, url: String, hops: Int): ExtensionStore {
        check(hops < MAX_HOPS) { "Too many redirects while resolving ${repo.name}'s index" }
        val bytes = getBytes(url)
        return when (bytes.firstOrNull()) {
            '['.code.toByte() -> {
                require(url.endsWith(LEGACY_INDEX)) { "Legacy extension list must end with $LEGACY_INDEX: $url" }
                resolveStore(repo, url.removeSuffix(LEGACY_INDEX) + LEGACY_REPO, hops + 1)
            }
            '{'.code.toByte() -> {
                val repoJson = json.decodeFromString<LegacyRepoJson>(bytes.decodeToString())
                val indexV2 = repoJson.indexV2
                if (indexV2 != null) {
                    resolveStore(repo, indexV2, hops + 1)
                } else {
                    val baseUrl = url.substringBeforeLast('/')
                    val indexUrl = baseUrl + LEGACY_INDEX
                    val extensions = json.decodeFromString<List<LegacyExtension>>(getBytes(indexUrl).decodeToString())
                    repoJson.toExtensionStore(repo, indexUrl, extensions.map { it.toRepoExtension(baseUrl) })
                }
            }
            else -> {
                val store = ProtoBuf.decodeFromByteArray<ProtoStore>(bytes)
                val list = store.extensionList
                    ?: store.extensionListUrl?.let { ProtoBuf.decodeFromByteArray<ProtoExtensionList>(getBytes(it)) }
                    ?: error("${repo.name}'s index has no extension list")
                store.toExtensionStore(repo, url, list)
            }
        }
    }

    suspend fun fetchIssues(repo: ExtensionRepo): List<GithubIssue> = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/${repo.githubRepo}/issues?state=open&per_page=25"
        val body = getBytes(url).decodeToString()
        json.decodeFromString<List<GithubIssue>>(body)
            .filter { it.pullRequest == null } // exclude PRs that the issues endpoint also returns
    }

    /** Downloads an extension's APK into [cacheDir] and returns the file. */
    suspend fun downloadApk(ext: RepoExtension, cacheDir: File): File = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(ext.apkUrl)
            .header("User-Agent", USER_AGENT)
            .build()
        val out = File(cacheDir, "${ext.pkg}-${ext.versionCode}.apk")
        val partial = File(cacheDir, "${out.name}.part")
        try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) error("Download failed: HTTP ${resp.code}")
                resp.body!!.byteStream().use { input ->
                    partial.outputStream().use { output -> input.copyTo(output) }
                }
            }
            if (!partial.renameTo(out)) error("Couldn't save ${ext.apkFileName}")
        } finally {
            partial.delete()
        }
        out
    }

    /** GETs [url], transparently un-gzipping bodies that are gzip files (as index.pb is). */
    private fun getBytes(url: String): ByteArray {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code} for $url")
            val bytes = resp.body!!.bytes()
            val gzipped = bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
            return if (gzipped) GZIPInputStream(bytes.inputStream()).use { it.readBytes() } else bytes
        }
    }

    companion object {
        private const val USER_AGENT = "TachiUp"
        private const val LEGACY_INDEX = "/index.min.json"
        private const val LEGACY_REPO = "/repo.json"
        private const val MAX_HOPS = 4

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
