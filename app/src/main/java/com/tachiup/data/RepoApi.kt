package com.tachiup.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** Network access to repository indexes, APKs and GitHub issues. */
class RepoApi(
    private val client: OkHttpClient = defaultClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchIndex(repo: ExtensionRepo): List<RepoExtension> = withContext(Dispatchers.IO) {
        val body = get(repo.indexUrl)
        json.decodeFromString<List<RepoExtension>>(body)
    }

    suspend fun fetchIssues(repo: ExtensionRepo): List<GithubIssue> = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/${repo.githubRepo}/issues?state=open&per_page=25"
        val body = get(url)
        json.decodeFromString<List<GithubIssue>>(body)
            .filter { it.pullRequest == null } // exclude PRs that the issues endpoint also returns
    }

    /** Downloads an APK to the given cache directory and returns the file. */
    suspend fun downloadApk(
        repo: ExtensionRepo,
        ext: RepoExtension,
        cacheDir: File,
    ): File = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(repo.apkUrl(ext.apk)).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("Download failed: HTTP ${resp.code}")
            val out = File(cacheDir, ext.apk)
            resp.body!!.byteStream().use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            out
        }
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "TachiUp")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code} for $url")
            return resp.body!!.string()
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
