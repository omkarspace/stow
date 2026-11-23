package com.cleansaver.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

data class ReleaseUpdateInfo(
    val latestVersion: String,
    val releaseUrl: String,
    val isUpdateAvailable: Boolean
)

class AppUpdateChecker(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    companion object {
        private const val LATEST_RELEASE_API =
            "https://api.github.com/repos/omkarspace/cleansaver/releases/latest"

        fun isNewerVersion(latestTag: String, currentVersion: String): Boolean {
            val latest = latestTag.trimStart('v', 'V').split(".").map { it.toIntOrNull() ?: 0 }
            val current = currentVersion.trimStart('v', 'V').split(".").map { it.toIntOrNull() ?: 0 }
            val maxLen = maxOf(latest.size, current.size)
            for (i in 0 until maxLen) {
                val l = latest.getOrElse(i) { 0 }
                val c = current.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
            return false
        }
    }

    suspend fun checkForUpdate(currentVersion: String): Result<ReleaseUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(LATEST_RELEASE_API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "CleanSaver-Android")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    return@withContext Result.success(
                        ReleaseUpdateInfo(currentVersion, "https://github.com/omkarspace/cleansaver/releases", false)
                    )
                }
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("HTTP ${response.code}"))
                }
                val body = response.body?.string() ?: throw IllegalStateException("Empty body")
                val obj = json.parseToJsonElement(body).jsonObject
                val tag = obj["tag_name"]?.jsonPrimitive?.content ?: currentVersion
                val url = obj["html_url"]?.jsonPrimitive?.content
                    ?: "https://github.com/omkarspace/cleansaver/releases"
                Result.success(
                    ReleaseUpdateInfo(
                        latestVersion = tag.trimStart('v', 'V'),
                        releaseUrl = url,
                        isUpdateAvailable = isNewerVersion(tag, currentVersion)
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

