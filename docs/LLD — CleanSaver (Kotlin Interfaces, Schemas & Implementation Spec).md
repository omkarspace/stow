# **Low-Level Design (LLD): CleanSaver — Kotlin Interfaces, Schemas & Implementation Spec**

# **1\. Document Metadata & Companion References**

| Metadata Parameter | Specification / Attribute Value |
| :---- | :---- |
| **Product Target** | CleanSaver Android App (v1.0 MVP) |
| **Companion Documents** | [PRD — CleanSaver](https://docs.google.com/document/d/11YUxMT8xowaLOTbgOOzCz0vrOwdQ1ynONCfhKctrkjg/edit) · [HLD — CleanSaver](https://docs.google.com/document/d/1sEdd_CITJfeSgRrRVk7t7s9OZ8CbyxwZ_XMDhCT0tYM/edit) |
| **Core Technical Stack** | Kotlin 2.0 · Jetpack Compose (Material 3\) · OkHttp 4 · Kotlinx Serialization · Room (KSP) · DataStore Preferences · WorkManager |
| **Dependency Injection** | Manual Dependency Injection via Application-Scoped `AppContainer` (Zero Reflection/Codegen Overhead to maintain APK size \< 5.0 MB) |
| **Target SDK / Min SDK** | API 35 (Android 15\) / API 29 (Android 10\) |
| **Release Target Date** | November 22, 2026 |

---

# **2\. Gradle Dependency Bill of Materials & Build Configuration**

## **2.1 Dependency Matrix & Exclusion Rationale**

| Group / Artifact | Version | Architectural Purpose | Exclusion Rationale / Alternatives |
| :---- | :---- | :---- | :---- |
| `androidx.compose:compose-bom` | `2024.10.01` | Bill of Materials for Compose UI components | Standardizes UI dependencies. |
| `androidx.compose.material3:material3` | `1.3.1` | M3 UI Components & Tokens | Custom lightweight design system. |
| `com.squareup.okhttp3:okhttp` | `4.12.0` | HTTP Client for Config fetching & Media Streaming | Chosen over Retrofit to eliminate reflection overhead and reduce binary size. |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | `1.7.3` | Multiplatform JSON Parser for Config & API payload | Excludes Moshi/Gson. Compiler plugin eliminates runtime reflection. |
| `androidx.room:room-runtime` | `2.6.1` | SQLite ORM Abstraction | Excludes heavy ORMs. |
| `androidx.room:room-ktx` | `2.6.1` | Coroutines & Flow extension bindings for Room | Provides reactive database queries. |
| `androidx.room:room-compiler` | `2.6.1` | KSP Symbol Processor for DAO generation | Generates code at compile time. |
| `androidx.datastore:datastore-preferences` | `1.1.1` | Key-Value Storage for App Settings & Remote Config cache | Replaces SharedPreferences with thread-safe Coroutine Flow API. |
| `androidx.work:work-runtime-ktx` | `2.9.1` | Deferred Background Download Worker Manager | Handlers execution during app backgrounding. |
| `io.coil-kt:coil-compose` | `2.7.0` | Disk-cached async image loader for UI thumbnails | Minimal overhead compared to Glide. Uses OkHttp pipeline. |
| `org.junit.jupiter:junit-jupiter` | `5.11.3` | Unit Testing Framework | Replaces JUnit 4 for modern parameterized testing. |
| `com.squareup.okhttp3:mockwebserver` | `4.12.0` | Mock HTTP Server for local network testing | Simulates network scenarios during integration tests. |

*Note on Exclusions:* Heavy frameworks including Google Firebase Analytics, AdMob SDK, Hilt/Dagger, and Retrofit are strictly excluded to ensure compliance with privacy constraints and to guarantee the release APK size remains well under the 5 MB upper bound.

## **2.2 Gradle Build Configuration (app/build.gradle.kts)**

```
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.cleansaver.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cleansaver.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug") // Replaced with production key in CI/CD pipeline
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    composeCompiler {
        // Kotlin 2.0 Compose Compiler Gradle plugin; no kotlinCompilerExtensionVersion needed.
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
        )
    }
}
```

---

# **3\. Domain Layer Models, Sealed Errors & Use Case Contracts**

## **3.1 Domain Models & Sealed Hierarchies**

```
package com.cleansaver.app.domain.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

enum class MediaType {
    VIDEO,
    IMAGE
}

enum class UrlKind {
    REEL,
    POST
}

@Keep
@Serializable
data class ParsedUrl(
    val rawUrl: String,
    val canonicalUrl: String,
    val shortcode: String,
    val kind: UrlKind
)

@Keep
@Serializable
data class MediaCandidate(
    val shortcode: String,
    val slideIndex: Int,
    val mediaType: MediaType,
    val downloadUrl: String,
    val thumbnailUrl: String?,
    val width: Int?,
    val height: Int?
)

sealed interface AppError {
    data object InvalidUrl : AppError
    data object StoriesNotSupported : AppError
    data object PrivateAccountNotSupported : AppError
    data object PostNotFound : AppError
    data object NetworkRateLimited : AppError
    data object ParserOutdated : AppError
    data object NoInternet : AppError
    data class StorageError(val reason: String) : AppError
    data class Unknown(val message: String) : AppError
}

sealed interface DownloadProgress {
    data object Queued : DownloadProgress
    data class Running(val bytesRead: Long, val totalBytes: Long, val percent: Int) : DownloadProgress
    data class Completed(val mediaStoreUri: String, val fileName: String) : DownloadProgress
    data class Failed(val error: AppError) : DownloadProgress
    data object Cancelled : DownloadProgress
}
```

## **3.2 Use Case Contracts & Validation Logic**

```
package com.cleansaver.app.domain.usecase

import com.cleansaver.app.domain.model.*
import com.cleansaver.app.domain.repository.MediaRepository
import com.cleansaver.app.domain.repository.RemoteConfigRepository
import kotlinx.coroutines.flow.Flow
import java.util.regex.Pattern

class ValidateUrlUseCase {
    
    private val extractUrlPattern = Pattern.compile("https?://\\S+")
    private val canonicalPattern = Pattern.compile("^https?://(www\\.)?instagram\\.com/(reel|reels|p|tv)/([A-Za-z0-9_-]{5,32})/?.*$")
    private val storiesPattern = Pattern.compile("^https?://(www\\.)?instagram\\.com/stories/.*$")

    operator fun invoke(input: String): Result<ParsedUrl> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Input is empty"))

        // Extract first URL if pasted with caption text
        val matcher = extractUrlPattern.matcher(trimmed)
        val rawUrl = if (matcher.find()) matcher.group(0) else trimmed

        if (storiesPattern.matcher(rawUrl).matches()) {
            return Result.failure(CustomThrowable(AppError.StoriesNotSupported))
        }

        val canonicalMatcher = canonicalPattern.matcher(rawUrl)
        if (!canonicalMatcher.matches()) {
            return Result.failure(CustomThrowable(AppError.InvalidUrl))
        }

        val typeSegment = canonicalMatcher.group(2)
        val shortcode = canonicalMatcher.group(3) ?: return Result.failure(CustomThrowable(AppError.InvalidUrl))

        val kind = when (typeSegment) {
            "reel", "reels" -> UrlKind.REEL
            "p", "tv" -> UrlKind.POST
            else -> UrlKind.POST
        }

        val canonicalUrl = "https://www.instagram.com/$typeSegment/$shortcode/"
        return Result.success(ParsedUrl(rawUrl = rawUrl, canonicalUrl = canonicalUrl, shortcode = shortcode, kind = kind))
    }
}

class CustomThrowable(val appError: AppError) : Throwable(appError.toString())

class ExtractMediaUseCase(
    private val mediaRepository: MediaRepository
) {
    suspend operator fun invoke(parsedUrl: ParsedUrl): Result<List<MediaCandidate>> {
        return mediaRepository.extractMedia(parsedUrl)
    }
}

class DownloadMediaUseCase(
    private val mediaRepository: MediaRepository
) {
    operator fun invoke(candidates: List<MediaCandidate>): Flow<Map<Int, DownloadProgress>> {
        return mediaRepository.downloadCandidates(candidates)
    }
}

class RefreshConfigUseCase(
    private val remoteConfigRepository: RemoteConfigRepository
) {
    suspend operator fun invoke(force: Boolean): Result<Unit> {
        return remoteConfigRepository.refreshConfig(force)
    }
}
```

---

# **4\. Remote Config Schema & RemoteConfigRepository Implementation**

## **4.1 JSON Remote Config Schema (v1 Validation Reference)**

```
{
  "schema_version": 1,
  "min_app_version_code": 1,
  "maintenance_mode": false,
  "maintenance_message": "",
  "config_ttl_hours": 24,
  "user_agents": [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.3 Safari/605.1.15"
  ],
  "url_patterns": {
    "reel_regex": "^https?://(www\\.)?instagram\\.com/(reel|reels)/([A-Za-z0-9_-]{5,32})/?.*$",
    "post_regex": "^https?://(www\\.)?instagram\\.com/(p|tv)/([A-Za-z0-9_-]{5,32})/?.*$",
    "stories_regex": "^https?://(www\\.)?instagram\\.com/stories/.*$"
  },
  "strategies": [
    {
      "id": "embed_json_primary",
      "type": "HTTP_EMBED",
      "enabled": true,
      "endpoint_template": "https://www.instagram.com/p/{shortcode}/embed/captioned/",
      "video_regex": "\"video_url\":\"(https?:\\\\/\\\\/[^\"]+)\"",
      "image_regex": "\"display_url\":\"(https?:\\\\/\\\\/[^\"]+)\"",
      "json_script_selector": "script[type='text/javascript']",
      "timeout_ms": 8000
    },
    {
      "id": "webview_ephemeral_fallback",
      "type": "WEBVIEW_EPHEMERAL",
      "enabled": true,
      "endpoint_template": "https://www.instagram.com/p/{shortcode}/",
      "video_regex": "meta[property='og:video']",
      "image_regex": "meta[property='og:image']",
      "json_script_selector": "",
      "timeout_ms": 10000
    }
  ]
}
```

## **4.2 Kotlin Remote Config DTOs**

```
package com.cleansaver.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ParserConfigDto(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("min_app_version_code") val minAppVersionCode: Int,
    @SerialName("maintenance_mode") val maintenanceMode: Boolean,
    @SerialName("maintenance_message") val maintenanceMessage: String = "",
    @SerialName("config_ttl_hours") val configTtlHours: Int = 24,
    @SerialName("user_agents") val userAgents: List<String>,
    @SerialName("url_patterns") val urlPatterns: UrlPatternsDto,
    @SerialName("strategies") val strategies: List<StrategyDto>
)

@Serializable
data class UrlPatternsDto(
    @SerialName("reel_regex") val reelRegex: String,
    @SerialName("post_regex") val postRegex: String,
    @SerialName("stories_regex") val storiesRegex: String
)

@Serializable
data class StrategyDto(
    @SerialName("id") val id: String,
    @SerialName("type") val type: String,
    @SerialName("enabled") val enabled: Boolean,
    @SerialName("endpoint_template") val endpointTemplate: String,
    @SerialName("video_regex") val videoRegex: String? = null,
    @SerialName("image_regex") val imageRegex: String? = null,
    @SerialName("json_script_selector") val jsonScriptSelector: String? = null,
    @SerialName("timeout_ms") val timeoutMs: Long = 8000L
)
```

## **4.3 RemoteConfigRepository Implementation**

```
package com.cleansaver.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cleansaver.app.data.remote.dto.ParserConfigDto
import com.cleansaver.app.domain.repository.RemoteConfigRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class RemoteConfigRepositoryImpl(
    private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : RemoteConfigRepository {

    companion object {
        private const val CONFIG_URL = "https://cleansaver.github.io/config/config.json"
        private const val SUPPORTED_SCHEMA_VERSION = 1

        val KEY_CONFIG_JSON = stringPreferencesKey("config_json")
        val KEY_CONFIG_ETAG = stringPreferencesKey("config_etag")
        val KEY_CONFIG_LAST_SYNC = longPreferencesKey("config_last_sync_ms")
    }

    override suspend fun getActiveConfig(forceRefresh: Boolean): ParserConfigDto {
        val prefs = dataStore.data.first()
        val cachedJson = prefs[KEY_CONFIG_JSON]
        val cachedEtag = prefs[KEY_CONFIG_ETAG] ?: ""
        val lastSyncMs = prefs[KEY_CONFIG_LAST_SYNC] ?: 0L
        val now = System.currentTimeMillis()

        val cachedConfig = parseConfigOrNull(cachedJson)
        val ttlMs = (cachedConfig?.configTtlHours ?: 24) * 3600_000L

        if (!forceRefresh && cachedConfig != null && (now - lastSyncMs < ttlMs)) {
            return cachedConfig
        }

        return try {
            val requestBuilder = Request.Builder()
                .url(CONFIG_URL)
                .get()

            if (cachedEtag.isNotEmpty()) {
                requestBuilder.header("If-None-Match", cachedEtag)
            }

            val client = okHttpClient.newBuilder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.code == 304 && cachedConfig != null) {
                    dataStore.edit { it[KEY_CONFIG_LAST_SYNC] = now }
                    return cachedConfig
                }

                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: throw IllegalStateException("Empty body")
                    val newConfig = json.decodeFromString<ParserConfigDto>(responseBody)

                    if (newConfig.schemaVersion <= SUPPORTED_SCHEMA_VERSION) {
                        val newEtag = response.header("ETag") ?: ""
                        dataStore.edit { mutablePrefs ->
                            mutablePrefs[KEY_CONFIG_JSON] = responseBody
                            mutablePrefs[KEY_CONFIG_ETAG] = newEtag
                            mutablePrefs[KEY_CONFIG_LAST_SYNC] = now
                        }
                        return newConfig
                    }
                }
                cachedConfig ?: loadBundledDefaultConfig()
            }
        } catch (e: Exception) {
            cachedConfig ?: loadBundledDefaultConfig()
        }
    }

    override suspend fun refreshConfig(force: Boolean): Result<Unit> {
        return try {
            getActiveConfig(forceRefresh = force)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseConfigOrNull(rawJson: String?): ParserConfigDto? {
        if (rawJson.isNullOrBlank()) return null
        return try {
            json.decodeFromString<ParserConfigDto>(rawJson)
        } catch (e: Exception) {
            null
        }
    }

    private fun loadBundledDefaultConfig(): ParserConfigDto {
        return context.assets.open("default_config.json").use { stream ->
            InputStreamReader(stream).use { reader ->
                json.decodeFromString<ParserConfigDto>(reader.readText())
            }
        }
    }
}
```

---

# **5\. Pluggable MediaExtractor Engine Low-Level Spec**

## **5.1 Architecture & Strategy Interface**

```
package com.cleansaver.app.data.extractor

import com.cleansaver.app.data.remote.dto.StrategyDto
import com.cleansaver.app.domain.model.MediaCandidate
import com.cleansaver.app.domain.model.ParsedUrl

interface MediaExtractorStrategy {
    val id: String
    suspend fun extract(
        parsedUrl: ParsedUrl,
        strategyConfig: StrategyDto,
        userAgent: String
    ): Result<List<MediaCandidate>>
}
```

## **5.2 MediaExtractorChain Orchestrator**

```kotlin
package com.cleansaver.app.data.extractor

import com.cleansaver.app.domain.model.*
import com.cleansaver.app.domain.repository.RemoteConfigRepository
import com.cleansaver.app.domain.usecase.CustomThrowable

class MediaExtractorChain(
    private val remoteConfigRepository: RemoteConfigRepository,
    private val strategies: Map<String, MediaExtractorStrategy>
) {
    suspend fun extract(parsedUrl: ParsedUrl, isRetry: Boolean = false): Result<List<MediaCandidate>> {
        val config = remoteConfigRepository.getActiveConfig(forceRefresh = false)

        if (config.maintenanceMode) {
            return Result.failure(CustomThrowable(AppError.ParserOutdated))
        }

        val userAgent = config.userAgents.randomOrNull()
            ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"

        val enabledStrategies = config.strategies.filter { it.enabled }
        var lastError: AppError = AppError.ParserOutdated

        for (strategyConfig in enabledStrategies) {
            val strategyImpl = strategies[strategyConfig.type] ?: continue
            val result = strategyImpl.extract(parsedUrl, strategyConfig, userAgent)

            if (result.isSuccess) {
                val candidates = result.getOrNull()
                if (!candidates.isNullOrEmpty()) {
                    return Result.success(candidates)
                }
            } else {
                val error = (result.exceptionOrNull() as? CustomThrowable)?.appError
                    ?: AppError.Unknown("Extractor failed")

                // Terminal error short-circuit check
                if (error is AppError.PrivateAccountNotSupported ||
                    error is AppError.PostNotFound ||
                    error is AppError.NetworkRateLimited
                ) {
                    return Result.failure(CustomThrowable(error))
                }
                lastError = error
            }
        }

        // Automatic remote config refresh fallback on parse failure
        if (!isRetry && lastError is AppError.ParserOutdated) {
            remoteConfigRepository.getActiveConfig(forceRefresh = true)
            return extract(parsedUrl, isRetry = true)
        }

        return Result.failure(CustomThrowable(lastError))
    }
}
```

### 5.3 Concrete Strategy: EmbedHttpExtractor

```kotlin
package com.cleansaver.app.data.extractor

import com.cleansaver.app.data.remote.dto.StrategyDto
import com.cleansaver.app.domain.model.*
import com.cleansaver.app.domain.usecase.CustomThrowable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class EmbedHttpExtractor(
    private val baseOkHttpClient: OkHttpClient
) : MediaExtractorStrategy {

    override val id: String = "HTTP_EMBED"

    override suspend fun extract(
        parsedUrl: ParsedUrl,
        strategyConfig: StrategyDto,
        userAgent: String
    ): Result<List<MediaCandidate>> {
        return try {
            val targetUrl = strategyConfig.endpointTemplate.replace("{shortcode}", parsedUrl.shortcode)
            
            val client = baseOkHttpClient.newBuilder()
                .connectTimeout(strategyConfig.timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(strategyConfig.timeoutMs, TimeUnit.MILLISECONDS)
                .cookieJar(okhttp3.CookieJar.NO_COOKIES)
                .build()

            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", userAgent)
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 404) return Result.failure(CustomThrowable(AppError.PostNotFound))
                if (response.code == 429) return Result.failure(CustomThrowable(AppError.NetworkRateLimited))
                if (!response.isSuccessful) return Result.failure(CustomThrowable(AppError.ParserOutdated))

                val body = response.body?.string() ?: ""
                if (body.contains("This Account is Private")) {
                    return Result.failure(CustomThrowable(AppError.PrivateAccountNotSupported))
                }

                val candidates = mutableListOf<MediaCandidate>()
                
                strategyConfig.videoRegex?.let { regexStr ->
                    val matcher = Pattern.compile(regexStr).matcher(body)
                    var index = 0
                    while (matcher.find()) {
                        val rawUrl = matcher.group(1) ?: continue
                        val cleanUrl = unescapeUrl(rawUrl)
                        candidates.add(
                            MediaCandidate(
                                shortcode = parsedUrl.shortcode,
                                slideIndex = index++,
                                mediaType = MediaType.VIDEO,
                                downloadUrl = cleanUrl,
                                thumbnailUrl = null,
                                width = null,
                                height = null
                            )
                        )
                    }
                }

                if (candidates.isEmpty()) {
                    strategyConfig.imageRegex?.let { regexStr ->
                        val matcher = Pattern.compile(regexStr).matcher(body)
                        var index = 0
                        while (matcher.find()) {
                            val rawUrl = matcher.group(1) ?: continue
                            val cleanUrl = unescapeUrl(rawUrl)
                            candidates.add(
                                MediaCandidate(
                                    shortcode = parsedUrl.shortcode,
                                    slideIndex = index++,
                                    mediaType = MediaType.IMAGE,
                                    downloadUrl = cleanUrl,
                                    thumbnailUrl = null,
                                    width = null,
                                    height = null
                                )
                            )
                        }
                    }
                }

                if (candidates.isNotEmpty()) Result.success(candidates) else Result.failure(CustomThrowable(AppError.ParserOutdated))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun unescapeUrl(url: String): String {
        return url.replace("\\/", "/")
            .replace("&amp;", "&")
            .replace("\&", "&")
    }
}
```

## **5.4 Concrete Strategy: EphemeralWebViewExtractor**

```
package com.cleansaver.app.data.extractor

import android.content.Context
import android.webkit.*
import com.cleansaver.app.data.remote.dto.StrategyDto
import com.cleansaver.app.domain.model.*
import com.cleansaver.app.domain.usecase.CustomThrowable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.regex.Pattern
import kotlin.coroutines.resume

class EphemeralWebViewExtractor(
    private val context: Context
) : MediaExtractorStrategy {

    override val id: String = "WEBVIEW_EPHEMERAL"

    override suspend fun extract(
        parsedUrl: ParsedUrl,
        strategyConfig: StrategyDto,
        userAgent: String
    ): Result<List<MediaCandidate>> = withContext(Dispatchers.Main) {
        var webView: WebView? = null
        try {
            withTimeout(strategyConfig.timeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    webView = WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = userAgent
                        settings.saveFormData = false
                        
                        CookieManager.getInstance().setAcceptCookie(false)

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                val url = request?.url?.toString() ?: ""
                                if (url.contains("google-analytics.com") || url.contains("facebook.net") || url.endsWith(".ttf")) {
                                    return WebResourceResponse("text/plain", "UTF-8", null)
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                evaluateJavascript(
                                    "(function() { return document.querySelector('meta[property=\"og:video\"]')?.content || document.querySelector('meta[property=\"og:image\"]')?.content || ''; })();"
                                ) { result ->
                                    val extractedUrl = result?.trim('"')?.replace("\\/", "/") ?: ""
                                    if (extractedUrl.isNotBlank() && extractedUrl != "null") {
                                        val type = if (extractedUrl.contains(".mp4")) MediaType.VIDEO else MediaType.IMAGE
                                        val candidate = MediaCandidate(
                                            shortcode = parsedUrl.shortcode,
                                            slideIndex = 0,
                                            mediaType = type,
                                            downloadUrl = extractedUrl,
                                            thumbnailUrl = null,
                                            width = null,
                                            height = null
                                        )
                                        if (continuation.isActive) continuation.resume(Result.success(listOf(candidate)))
                                    } else {
                                        if (continuation.isActive) continuation.resume(Result.failure(CustomThrowable(AppError.ParserOutdated)))
                                    }
                                }
                            }
                        }
                        loadUrl(parsedUrl.canonicalUrl)
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            webView?.apply {
                stopLoading()
                clearCache(true)
                destroy()
            }
        }
    }
}
```

---

# **6\. Atomic MediaStore Download Pipeline & WorkManager Worker Spec**

## **6.1 MediaStoreWriter Implementation**

```
package com.cleansaver.app.data.local

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.cleansaver.app.domain.model.MediaCandidate
import com.cleansaver.app.domain.model.MediaType
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.io.OutputStream

data class SavedFileMeta(val mediaStoreUri: String, val fileName: String, val fileSize: Long)

class MediaStoreWriter(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    suspend fun downloadToGallery(
        candidate: MediaCandidate,
        onProgress: (bytesRead: Long, totalBytes: Long, percent: Int) -> Unit
    ): Result<SavedFileMeta> {
        val isVideo = candidate.mediaType == MediaType.VIDEO
        val collection: Uri = if (isVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val extension = if (isVideo) "mp4" else "jpg"
        val fileName = "CleanSaver_${candidate.shortcode}_${candidate.slideIndex}.$extension"
        val relativePath = if (isVideo) "Movies/CleanSaver" else "Pictures/CleanSaver"
        val mimeType = if (isVideo) "video/mp4" else "image/jpeg"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val contentResolver = context.contentResolver
        val pendingUri = contentResolver.insert(collection, contentValues)
            ?: return Result.failure(IllegalStateException("Failed to create MediaStore entry"))

        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null

        return try {
            val request = Request.Builder().url(candidate.downloadUrl).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP server returned error code: ${response.code}")
            }

            val body = response.body ?: throw IllegalStateException("Response body is null")
            val totalBytes = body.contentLength()
            inputStream = body.byteStream()
            outputStream = contentResolver.openOutputStream(pendingUri)
                ?: throw IllegalStateException("Failed to open output stream for URI: $pendingUri")

            val buffer = ByteArray(16384)
            var bytesReadTotal = 0L
            var read: Int
            var lastReportedPercent = -1

            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
                bytesReadTotal += read

                if (totalBytes > 0) {
                    val percent = ((bytesReadTotal * 100) / totalBytes).toInt()
                    if (percent != lastReportedPercent) {
                        lastReportedPercent = percent
                        onProgress(bytesReadTotal, totalBytes, percent)
                    }
                }
            }
            outputStream.flush()

            // Finalize entry creation: Toggle IS_PENDING to 0
            val publishValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            contentResolver.update(pendingUri, publishValues, null, null)

            Result.success(SavedFileMeta(pendingUri.toString(), fileName, bytesReadTotal))

        } catch (e: Throwable) {
            // Guarantee atomic cleanup: Purge partial file from MediaStore on failure
            contentResolver.delete(pendingUri, null, null)
            if (e is CancellationException) throw e
            Result.failure(e)
        } finally {
            inputStream?.close()
            outputStream?.close()
        }
    }
}
```

## **6.2 MediaDownloadWorker WorkManager Orchestration**

```
package com.cleansaver.app.data.worker

import android.content.Context
import androidx.work.*
import com.cleansaver.app.data.local.DownloadedMediaDao
import com.cleansaver.app.data.local.DownloadedMediaEntity
import com.cleansaver.app.data.local.MediaStoreWriter
import com.cleansaver.app.domain.model.MediaCandidate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json

class MediaDownloadWorker(
    context: Context,
    params: WorkerParameters,
    private val mediaStoreWriter: MediaStoreWriter,
    private val downloadedMediaDao: DownloadedMediaDao,
    private val json: Json
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_CANDIDATES_JSON = "key_candidates_json"
        const val KEY_SHORTCODE = "key_shortcode"
        const val KEY_SOURCE_URL = "key_source_url"
    }

    override suspend fun doWork(): Result = coroutineScope {
        val candidatesJson = inputData.getString(KEY_CANDIDATES_JSON) ?: return@coroutineScope Result.failure()
        val sourceUrl = inputData.getString(KEY_SOURCE_URL) ?: ""
        val candidates = json.decodeFromString<List<MediaCandidate>>(candidatesJson)

        val semaphore = Semaphore(permits = 2) // Enforce maximum 2 concurrent file downloads

        val tasks = candidates.map { candidate ->
            async {
                semaphore.withPermit {
                    // Check local DB deduplication state
                    val existing = downloadedMediaDao.findExisting(candidate.shortcode, candidate.slideIndex)
                    if (existing != null && verifyUriExists(existing.mediaStoreUri)) {
                        return@async true
                    }

                    val downloadResult = mediaStoreWriter.downloadToGallery(candidate) { _, _, _ -> }
                    if (downloadResult.isSuccess) {
                        val meta = downloadResult.getOrThrow()
                        downloadedMediaDao.upsert(
                            DownloadedMediaEntity(
                                shortcode = candidate.shortcode,
                                slideIndex = candidate.slideIndex,
                                mediaType = candidate.mediaType.name,
                                mediaStoreUri = meta.mediaStoreUri,
                                fileName = meta.fileName,
                                fileSizeBytes = meta.fileSize,
                                sourceUrl = sourceUrl,
                                createdAtMs = System.currentTimeMillis()
                            )
                        )
                        true
                    } else {
                        false
                    }
                }
            }
        }

        val results = tasks.awaitAll()
        if (results.all { it }) Result.success() else Result.retry()
    }

    private fun verifyUriExists(uriStr: String): Boolean {
        return try {
            applicationContext.contentResolver.openAssetFileDescriptor(android.net.Uri.parse(uriStr), "r")?.use { }
            true
        } catch (e: Exception) {
            false
        }
    }
}
```

---

# **7\. Local Persistence Schemas (Room SQLite & DataStore Preferences)**

## **7.1 Room Entity (`downloaded_media`) & DAO**

```kotlin
package com.cleansaver.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "downloaded_media",
    indices = [Index(value = ["shortcode", "slide_index"], unique = true)]
)
data class DownloadedMediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "shortcode") val shortcode: String,
    @ColumnInfo(name = "slide_index") val slideIndex: Int,
    @ColumnInfo(name = "media_type") val mediaType: String,
    @ColumnInfo(name = "mediastore_uri") val mediaStoreUri: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "file_size_bytes") val fileSizeBytes: Long,
    @ColumnInfo(name = "source_url") val sourceUrl: String,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long
)

@Dao
interface DownloadedMediaDao {
    @Query("SELECT * FROM downloaded_media ORDER BY created_at_ms DESC")
    fun observeAll(): Flow<List<DownloadedMediaEntity>>

    @Query("SELECT * FROM downloaded_media WHERE media_type = :type ORDER BY created_at_ms DESC")
    fun observeByType(type: String): Flow<List<DownloadedMediaEntity>>

    @Query("SELECT * FROM downloaded_media WHERE shortcode = :shortcode AND slide_index = :slideIndex LIMIT 1")
    suspend fun findExisting(shortcode: String, slideIndex: Int): DownloadedMediaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadedMediaEntity): Long

    @Query("DELETE FROM downloaded_media WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM downloaded_media")
    suspend fun clearAll()
}

@Database(entities = [DownloadedMediaEntity::class], version = 1, exportSchema = true)
abstract class CleanSaverDatabase : RoomDatabase() {
    abstract fun downloadedMediaDao(): DownloadedMediaDao
}
```

### 7.2 UserPrefsRepository (DataStore Keys & Defaults)

| Preference Key | Data Type | Default Value | Description / Purpose |
| :--- | :--- | :--- | :--- |
| `theme_mode` | String | `"SYSTEM"` | App visual theme preference (`"SYSTEM"`, `"LIGHT"`, `"DARK"`). |
| `clipboard_autodetect` | Boolean | `false` | When `true`, automatically reads clipboard on app foreground. |
| `config_json` | String | `""` | Cached Remote Config JSON payload string. |
| `config_etag` | String | `""` | HTTP ETag header value for Remote Config conditional caching. |
| `config_last_sync_ms` | Long | `0L` | Epoch timestamp of last successful remote config synchronization. |
| `fair_use_accepted` | Boolean | `false` | Tracks user acknowledgement of the Fair Use disclaimer modal. |

---

## 8. UI State Machines, AndroidManifest & Network Security XML

### 8.1 UI State Machine Specifications

```kotlin
package com.cleansaver.app.ui.state

import com.cleansaver.app.domain.model.AppError
import com.cleansaver.app.domain.model.DownloadProgress
import com.cleansaver.app.domain.model.MediaCandidate

data class HomeUiState(
    val inputUrl: String = "",
    val isValidating: Boolean = false,
    val isExtracting: Boolean = false,
    val extractedCandidates: List<MediaCandidate> = emptyList(),
    val downloadProgressMap: Map<Int, DownloadProgress> = emptyMap(),
    val error: AppError? = null
)

sealed interface ShareSheetUiState {
    data object Idle : ShareSheetUiState
    data object Loading : ShareSheetUiState
    data class PreviewReady(val candidates: List<MediaCandidate>) : ShareSheetUiState
    data class Downloading(val progressMap: Map<Int, DownloadProgress>) : ShareSheetUiState
    data class Completed(val savedCount: Int) : ShareSheetUiState
    data class Error(val appError: AppError) : ShareSheetUiState
}

data class LibraryUiState(
    val filterType: String = "ALL", // "ALL", "VIDEO", "IMAGE"
    val items: List<com.cleansaver.app.data.local.DownloadedMediaEntity> = emptyList(),
    val isLoading: Boolean = false
)

data class SettingsUiState(
    val themeMode: String = "SYSTEM",
    val clipboardAutoDetect: Boolean = false,
    val fairUseAccepted: Boolean = false,
    val appVersion: String = "1.0.0"
)
```

## **8.2 AndroidManifest.xml Specification**

```
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:name=".CleanSaverApplication"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:networkSecurityConfig="@xml/network_security_config"
        android:theme="@style/Theme.CleanSaver">

        <activity
            android:name=".ui.home.MainActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Transparent Interceptor Activity for Process Text & Intent Shares -->
        <activity
            android:name=".ui.share.ShareReceiverActivity"
            android:exported="true"
            android:excludeFromRecents="true"
            android:theme="@style/Theme.CleanSaver.Transparent">
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="text/plain" />
            </intent-filter>
        </activity>

        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false">
        </provider>

    </application>
</manifest>
```

## **8.3 Network Security Configuration (res/xml/network\_security\_config.xml)**

```
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

---

# **9\. Automated Unit & Instrumentation Test Matrix**

| Test Suite Class | Test Case Description | Test Input Data | Assertion / Target Result |
| :---- | :---- | :---- | :---- |
| `ValidateUrlUseCaseTest` | Standard Reel URL parsing | `"https://www.instagram.com/reel/C1234567890/"` | Returns `Result.success(ParsedUrl)` with `shortcode = "C1234567890"`, `kind = REEL`. |
| `ValidateUrlUseCaseTest` | Mobile Share URL with tracking parameters | `"Check out this Reel! https://instagram.com/reel/C1234567890/?igsh=NzBmMjdhZWRiYQ=="` | Strips caption text and query params; returns canonical URL `"https://www.instagram.com/reel/C1234567890/"`. |
| `ValidateUrlUseCaseTest` | Standard Post URL | `"https://www.instagram.com/p/B9876543210/"` | Returns `Result.success(ParsedUrl)` with `kind = POST`. |
| `ValidateUrlUseCaseTest` | Story URL explicit rejection | `"https://www.instagram.com/stories/user/1234567890/"` | Fails with `AppError.StoriesNotSupported`. |
| `ValidateUrlUseCaseTest` | Invalid / Non-Instagram URL parsing | `"https://example.com/video/12345"` | Fails with `AppError.InvalidUrl`. |
| `MediaExtractorChainTest` | Primary HTTP Embed extraction success | Valid `ParsedUrl` object | Returns `Result.success` containing `MediaCandidate` list; does not trigger fallback strategies. |
| `MediaExtractorChainTest` | Fallback trigger on HTTP strategy failure | Valid `ParsedUrl` with primary strategy failing (404) | System falls back to `WEBVIEW_EPHEMERAL` strategy and extracts media. |
| `MediaExtractorChainTest` | Private Account short-circuit check | Private post HTML snippet containing `"This Account is Private"` | Returns `Result.failure(AppError.PrivateAccountNotSupported)` instantly without executing secondary strategies. |
| `MediaExtractorChainTest` | Remote config auto-refresh on outdated parser | Response indicating parser failure | Calls `remoteConfigRepository.getActiveConfig(forceRefresh = true)` once and retries chain extraction. |
| `RemoteConfigRepositoryTest` | HTTP 304 Not Modified cache preservation | Mock server returning 304 with matching ETag | Keeps existing DataStore JSON cache and updates `config_last_sync_ms` timestamp. |
| `RemoteConfigRepositoryTest` | HTTP 200 OK configuration update | Valid Config JSON payload with new ETag | Persists new JSON and ETag to DataStore and returns updated model. |
| `RemoteConfigRepositoryTest` | Fallback to asset defaults on JSON error | Malformed JSON response body from network | Catches parsing exception and returns bundled `assets/default_config.json`. |
| `MediaStoreWriterTest` | Atomic media creation & cleanup on failure | Interrupted byte stream simulation | Creates file with `IS_PENDING = 1`, catches `IOException`, deletes partial file from `MediaStore`, and returns `Result.failure`. |

---

# **10\. Open Implementation Gaps**

Known spec gaps identified during the PRD/HLD/LLD reconciliation. These are *not* contradictions — they are missing specifications that must be resolved before or during implementation.

| # | Gap | Impact | Suggested Resolution |
| :---- | :---- | :---- | :---- |
| 1 | **`assets/default_config.json` content is never specified.** The bundled fallback must deserialize into `ParserConfigDto`, but no file content is given. | First launch / offline extraction fails with an asset-read or parse error. | Author the file using the schema in §4.1 (the same JSON as the PRD/HLD examples) and commit it under `app/src/main/assets/`. |
| 2 | **`gradle/libs.versions.toml` is missing** although `app/build.gradle.kts` references `alias(libs.plugins.*)`. | Build does not resolve. | Create the version catalog matching the dependency matrix in §2.1 (AGP, Kotlin 2.0, KSP, serialization plugin + all library versions). |
| 3 | **`proguard-rules.pro` content is unspecified.** Release build has `isMinifyEnabled = true`. | kotlinx-serialization and Room rules will be stripped → runtime crashes in release only. | Add serializer keep rules, Room keep rules, and OkHttp/okio defaults; verify with a minified release build. |
| 4 | **No custom `WorkerFactory` specified.** `MediaDownloadWorker` takes constructor-injected `MediaStoreWriter`, `DownloadedMediaDao`, and `Json`, but Hilt/Dagger are excluded from the stack. | WorkManager cannot instantiate the worker → `IllegalStateException` at enqueue. | Implement `Configuration.Provider` on `CleanSaverApplication` with a manual factory built from `AppContainer`. |
| 5 | **Foreground-service promise without declarations.** PRD Screen 2 states downloads run via a "foreground service notification"; the manifest declares neither `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC` permissions nor any service. On targetSdk 35 a long download without it is subject to execution limits. | Background downloads may be killed; PRD behavior not delivered as written. | Either add the foreground-service type + `foregroundServiceType="dataSync"` on a `DownloadService`, or amend the PRD to describe plain `WorkManager` notification progress. |
| 6 | **Media3/ExoPlayer missing from the dependency matrix.** PRD Screen 3 specifies full-screen video preview "via ExoPlayer/Media3", but §2.1 does not list `androidx.media3`. | In-app preview cannot be built as specified. | Add `androidx.media3:media3-exoplayer` + `media3-ui` to §2.1, or scope preview to a system gallery handoff. |
| 7 | **No UI-layer implementation spec.** The LLD defines models, repositories, workers, and UI *state* (§8.1) but no composables, navigation graph, or theme tokens for the four PRD screens. | Implementers must design the UI from the PRD prose alone. | Add a §11 covering screen composables, navigation (single-activity, bottom sheet for share), and Material 3 theme; or accept PRD §5 as the UI authority. |
| 8 | **`MediaStoreWriterTest` harness unspecified.** The test requires `ContentResolver.insert/update/delete` behavior; the test matrix does not say whether it runs under Robolectric or as an instrumentation test. | Test cannot be written unambiguously. | Declare it an instrumentation test (androidTest) using a `MediaStore` on an emulator, or a Robolectric test with a shadow `ContentResolver`. |
| 9 | **`Room DB` schema export & migration path undefined.** `exportSchema = true` but no `schemas/` directory, no migration strategy for future versions, and Room's `fallbackToDestructiveMigration` is not addressed. | Version bumps risk data loss or build failure (Room requires a schema location when exporting). | Configure `room.schemaLocation` in KSP args, commit `app/schemas/`, and state the migration policy for `version > 1`. |

