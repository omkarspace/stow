# **High-Level Design (HLD): CleanSaver — Zero-Cost, On-Device Android Media Saver**

# **Document Overview & Core Constraints**

| Attribute | Specification Details |
| :---- | :---- |
| **Product / Component** | CleanSaver Android Application (v1.0 MVP) |
| **Companion Spec** | [PRD — CleanSaver](https://docs.google.com/document/d/11YUxMT8xowaLOTbgOOzCz0vrOwdQ1ynONCfhKctrkjg/edit) |
| **Target Platform** | Android 10.0 to Android 15.0 (Min SDK 29, Target SDK 35\) |
| **Architectural Constraints** | 100% On-Device Execution · ₹0/month Infra Cost · Zero Auth / No Login · Zero Ads & Zero Telemetry SDKs · APK Size \<5 MB |
| **Document Date** | November 22, 2026 |

---

# **Architectural Principles & System Context**

CleanSaver is engineered as an autonomous, client-side Android application designed to extract and store public media without reliance on centralized proxy servers, paid extraction APIs, or cloud datastores.

## **Governing Architectural Principles**

* **Zero-Backend Client-Side Execution:** All link parsing, metadata extraction, and binary media streaming originate directly from the user's mobile device IP address to public Content Delivery Networks (CDNs) and local storage. No developer-owned application servers, backend microservices, or database instances exist.  
* **Remote-Configurable Parser Resilience:** Platform HTML layouts and metadata schemas evolve over time. CleanSaver decouples parsing rules (regular expressions, DOM selectors, JSON Path rules) from binary application releases. It fetches a versioned, static configuration file (`config.json`) hosted on free static platforms (GitHub Pages) using HTTP ETag conditional headers.  
* **Scoped Storage Native Design:** Adheres strictly to modern Android storage policies. On Android 10+ (API 29+), the app writes binary assets directly to public media collections via the Android `MediaStore` API without requesting broad legacy storage permissions (`READ_EXTERNAL_STORAGE` or `WRITE_EXTERNAL_STORAGE`).  
* **Zero-SDK Privacy Guarantee:** To ensure absolute privacy and optimize binary size, the codebase omits all third-party tracking, analytics, crash reporting (e.g., Firebase Crashlytics), or monetization SDKs (e.g., AdMob). The application contains no user identifiers, telemetric beacons, or persistent cookies.  
* **Clean MVVM Modularity:** Uses Model-View-ViewModel (MVVM) architecture with pluggable strategy patterns. The core extraction engine relies on isolated, testable extraction components, separating UI interactions from parsing logic and networking layers.

## **System Context**

The system boundary encompasses the local Android device and three external touchpoints:

```
+-----------------------------------------------------------------------------------+
|                              ANDROID DEVICE BOUNDARY                              |
|                                                                                   |
|  +----------------------+      +----------------------+     +------------------+  |
|  | Android OS Share     | ---> | CleanSaver Application| --> | Android MediaStore|  |
|  | Intent (ACTION_SEND) |      | (MVVM Clean Arch)    |     | (Movies/Pictures)|  |
|  +----------------------+      +----------------------+     +------------------+  |
|                                           |                                       |
+-------------------------------------------|---------------------------------------+
                                            |
                         Direct HTTPS       |       Direct HTTPS
                      (Client Device IP)    |    (Client Device IP)
                                            v                       v
               +----------------------------------+   +---------------------------+
               | Static Config Host               |   | Public Web / CDN          |
               | (GitHub Pages)                   |   | Endpoints                 |
               | Serves static config.json        |   | Direct MP4 / JPG Streams  |
               +----------------------------------+   +---------------------------+
```

1. **Android OS Intents & MediaStore:** Receives shared text content via `ACTION_SEND` and persists finalized media directly to shared public storage (`Movies/CleanSaver` or `Pictures/CleanSaver`).  
2. **Static Config Host:** GitHub Pages hosting a `<5 KB` `config.json` file. Requests leverage `If-None-Match` headers for `304 Not Modified` responses to minimize bandwidth.  
3. **Public Web / CDN Endpoints:** Media extraction and binary streaming connect directly from the device to origin CDNs. No developer-owned proxies exist in the data path.

---

# **Layered Android Component Architecture**

CleanSaver follows a 4-layer Clean Architecture model combined with unidirectional data flow (UDF).

| Layer | Primary Responsibilities | Key Kotlin Components & Package |
| :---- | :---- | :---- |
| **Presentation Layer** | Renders UI using Jetpack Compose & Material 3\. Handles Android system intents, state presentation, and user interactions. Exposes immutable `StateFlow<UiState>`. | `ui.home.HomeScreenui.share.ShareReceiverActivityui.library.LibraryScreenui.settings.SettingsScreenui.*.ViewModel` |
| **Domain Layer** | Pure Kotlin business logic. Independent of Android framework dependencies. Contains use cases, domain entities, and repository interfaces. | `domain.usecase.ValidateUrlUseCasedomain.usecase.ExtractMediaUseCasedomain.usecase.DownloadMediaUseCasedomain.usecase.RefreshConfigUseCasedomain.model.MediaItemdomain.model.AppError` |
| **Data Layer** | Implements data coordination, strategy execution, caching mechanisms, static configuration syncing, and Room/DataStore operations. | `data.extractor.MediaExtractorRepositoryImpldata.config.RemoteConfigRepositoryImpldata.download.DownloadRepositoryImpldata.local.LibraryRepositoryImpl` |
| **Platform / OS Layer** | Android system bindings: OS Share Sheet handlers, low-level HTTP networking, atomic MediaStore IO streams, background processing, and local storage engines. | `ui.share.ShareReceiverActivity` (`ACTION_SEND`) `OkHttpClient` (Isolated instance, zero cookies)`Android MediaStore API` (`IS_PENDING` atomic writes)`WorkManager` (`MediaDownloadWorker`)`Room SQLite DB` / `Jetpack DataStore` |

---

# **End-to-End Sequence Flows**

## **Flow A — Share Sheet to Saved Gallery**

```
[User / OS Share]    [ShareReceiverActivity]    [ValidateUrlUseCase]    [ExtractMediaUseCase]    [DownloadMediaUseCase]    [MediaStore API]
        |                       |                         |                       |                        |                      |
        |--- Share Reel/Post -->|                         |                       |                        |                      |
        |                       |--- Pass EXTRA_TEXT ---->|                       |                        |                      |
        |                       |                         |-- Canonicalize URL -->|                        |                      |
        |                       |                         |<-- Cleaned Shortcode -|                        |                      |
        |                       |                                                 |                        |                      |
        |                       |----------------------- Request Extraction ----->|                        |                      |
        |                       |                                                 |-- Fetch Parser Config ->|                      |
        |                       |                                                 |-- Execute Strategy 1/2 >|                      |
        |                       |<---------------- Return MediaCandidates ---------|                        |                      |
        |                       |                                                                          |                      |
        |<-- Display Preview ---|                                                                          |                      |
        |    (1-Tap Save)       |                                                                          |                      |
        |                       |                                                                          |                      |
        |--- Tap Download ----->|------------------------------------------------------------------------->|                      |
        |                       |                                                                          |-- Create IS_PENDING ->|
        |                       |                                                                          |-- Stream Bytes ----->|
        |                       |<----------------------- Live Progress Stream ----------------------------|-- Set IS_PENDING=0 ->|
        |                       |                                                                          |                      |
        |<-- Gallery Ready -----|                                                                          |                      |
```

1. **Share Initiation:** The user selects **Share** on a public post or reel in an external app and selects **CleanSaver**.  
2. **Intent Processing:** `ShareReceiverActivity` handles the incoming `ACTION_SEND` intent, extracts `EXTRA_TEXT`, and forwards the raw text string to `ValidateUrlUseCase`.  
3. **URL Validation & Canonicalization:** `ValidateUrlUseCase` strips tracking query parameters (e.g., `?igsh=...`, `?utm_source=...`), rejects invalid paths (such as `/stories/`), and extracts the canonical shortcode and media classification.  
4. **Config Fetching:** `ExtractMediaUseCase` retrieves the active `ParserConfig` from `RemoteConfigRepository` (using the local DataStore cache or bundled `assets/default_config.json`).  
5. **Extractor Strategy Execution:** `MediaExtractorChain` attempts Strategy 1 (lightweight HTTP GET to the public embed/page endpoint). If Strategy 1 fails or returns incomplete payload structure, it executes Strategy 2 (ephemeral off-screen WebView with isolated storage and a 10-second timeout).  
6. **Candidate Parsing:** The active strategy resolves the response payload into a list of `MediaCandidate` objects containing thumbnail URLs, direct MP4/JPG CDN links, slide indexes, and asset dimensions.  
7. **Preview Display:** `ShareReceiverActivity` opens a translucent Material 3 BottomSheet displaying media previews. Single items auto-present a 1-tap download trigger; carousels render a selectable grid with a "Save All" option.  
8. **Atomic Media Store Download:** `DownloadMediaUseCase` enqueues an asynchronous download job (managed via Coroutines or `WorkManager`). The service creates an entry in Android `MediaStore` with `IS_PENDING = 1` and streams bytes directly via OkHttp using 16 KB buffers while emitting progress state to the UI.  
9. **Finalization & Indexing:** Upon reaching EOF, the repository updates `IS_PENDING = 0`, instantly revealing the asset in the system gallery (`Movies/CleanSaver` or `Pictures/CleanSaver`). The transaction details are logged to the local Room database (`downloaded_media`).

## **Flow B — Zero-Cost Remote Parser Config Sync**

```
[App Launch / Manual Trigger] --> [RemoteConfigRepository] --> Send HTTP GET (If-None-Match: "etag_hash")
                                            |
                    +-----------------------+-----------------------+
                    |                                               |
            [HTTP 304 Not Modified]                         [HTTP 200 OK]
                    |                                               |
         Maintain Cached DataStore                  Validate Schema & Integrity
                    |                                               |
         Zero Bandwidth Payload                     Persist to DataStore + New ETag
                    |                                               |
                    +-----------------------+-----------------------+
                                            v
                                 Active Engine Rules Ready
```

1. **Sync Trigger:** On cold launch (throttled to once per 24-hour window) or when a user manually taps "Refresh Rules" upon encountering a `ParserOutdatedError`, `RemoteConfigRepository` initiates an asynchronous HTTP GET request to the static `config.json` URL. The request includes the `If-None-Match` header containing the stored ETag value.  
2. **304 Unmodified Handling:** If the static host returns `HTTP 304 Not Modified`, the client aborts data Transfer, consuming 0 bytes of payload, and maintains the current DataStore configuration.  
3. **200 OK Payload Updating:** If the host returns `HTTP 200 OK`, the app validates `schema_version` compatibility and JSON structure. Upon successful validation, it writes the payload and the new `ETag` string to Jetpack DataStore.  
4. **Fallback & Error Recovery:** If network connectivity is unavailable or the incoming JSON fails validation, the repository logs the error without interrupting execution and falls back to the existing DataStore cache or the bundled `assets/default_config.json`.

---

# **Proposed Kotlin Package Structure**

```
com.cleansaver.app/
│
├── CleanSaverApplication.kt           # Application context initialization & DI container setup
│
├── di/                                # Manual Dependency Injection / Modules
│   ├── AppContainer.kt
│   └── ServiceLocator.kt
│
├── ui/                                # Presentation Layer (Jetpack Compose UI & ViewModels)
│   ├── home/
│   │   ├── HomeScreen.kt
│   │   └── HomeViewModel.kt
│   ├── share/
│   │   ├── ShareReceiverActivity.kt
│   │   ├── ShareBottomSheet.kt
│   │   └── ShareViewModel.kt
│   ├── library/
│   │   ├── LibraryScreen.kt
│   │   └── LibraryViewModel.kt
│   ├── settings/
│   │   ├── SettingsScreen.kt
│   │   └── SettingsViewModel.kt
│   └── theme/
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
│
├── domain/                            # Domain Layer (Pure Kotlin Business Logic)
│   ├── model/
│   │   ├── MediaItem.kt
│   │   ├── MediaCandidate.kt
│   │   ├── ParserConfig.kt
│   │   └── AppError.kt
│   ├── usecase/
│   │   ├── ValidateUrlUseCase.kt
│   │   ├── ExtractMediaUseCase.kt
│   │   ├── DownloadMediaUseCase.kt
│   │   └── RefreshConfigUseCase.kt
│   └── repository/
│       ├── ExtractorRepository.kt
│       ├── RemoteConfigRepository.kt
│       ├── DownloadRepository.kt
│       └── LibraryRepository.kt
│
├── data/                              # Data Layer (Implementations & Data Sources)
│   ├── extractor/
│   │   ├── MediaExtractorRepositoryImpl.kt
│   │   ├── StrategyChain.kt
│   │   ├── MediaExtractorStrategy.kt
│   │   ├── EmbedHttpExtractor.kt
│   │   └── EphemeralWebViewExtractor.kt
│   ├── remote/
│   │   └── dto/
│   │       ├── ParserConfigDto.kt
│   │       ├── UrlPatternsDto.kt
│   │       └── StrategyDto.kt
│   ├── config/
│   │   ├── RemoteConfigRepositoryImpl.kt
│   │   └── ConfigParser.kt
│   ├── download/
│   │   └── DownloadRepositoryImpl.kt
│   ├── worker/
│   │   └── MediaDownloadWorker.kt
│   └── local/
│       ├── db/
│       │   ├── CleanSaverDatabase.kt
│       │   ├── DownloadedMediaDao.kt
│       │   └── DownloadedMediaEntity.kt
│       ├── MediaStoreWriter.kt
│       └── prefs/
│           └── UserPreferencesDataStore.kt
│
└── util/                              # Utilities & Constants
    ├── StorageUtils.kt
    └── UrlUtils.kt
```

---

# **Pluggable Media Extraction Engine & Remote Config Schema**

## **Extractor Strategy Pattern Design**

The extraction architecture uses the Strategy Pattern executed via a sequential chain of responsibility.

```
interface MediaExtractorStrategy {
    val strategyId: String
    suspend fun canHandle(url: String, config: ParserConfig): Boolean
    suspend fun extract(shortcode: String, config: ParserConfig): Result<List<MediaCandidate>>
}
```

```
                        [Incoming Link Request]
                                   |
                   +---------------+---------------+
                   | Primary Embed Strategy        |
                   | (HTTP GET / Fast execution)   |
                   +---------------+---------------+
                                   |
                         Success? / \ Failure
                         /             \
                        v               v
            [Return Candidates]   +-------------------------------+
                                  | Ephemeral WebView Strategy    |
                                  | (Headless DOM Render / 10s)   |
                                  +---------------+---------------+
                                                  |
                                        Success? / \ Failure
                                        /             \
                                       v               v
                           [Return Candidates]   [Throw ParserOutdatedError]
                                                       |
                                               Trigger Config Refresh
```

* **Primary Strategy (`EmbedHttpExtractor`):** Performs a lightweight HTTP GET request using OkHttp with configured mobile `User-Agent` strings. It evaluates public OpenGraph metadata tags, embedded JSON-LD scripts, or secondary API responses. Processing takes \~300ms to 800ms.  
* **Fallback Strategy (`EphemeralWebViewExtractor`):** Triggered if the primary strategy fails or returns incomplete payloads. Instantiates an isolated off-screen `WebView` instance with strict resource boundaries (cookies disabled, DOM storage disabled, hardware acceleration toggled). It renders the public document, executes configured JavaScript extraction snippets, extracts target CDN URLs, and self-destructs within a 10-second timeout window.

## **Remote `config.json` Schema Design (v1)**

Authoritative definition: `ParserConfigDto` in the LLD (`data/remote/dto/`). The PRD, HLD, and LLD all use this schema.

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

## **Domain Error Mapping Architecture**

| HTTP Status / Trigger Signal | Internal Domain Exception | Resolved UI User Action |
| :---- | :---- | :---- |
| `HTTP 404 Not Found` | `AppError.PostNotFound` | Render error dialog: "Content missing or removed by author." |
| `HTTP 429 Too Many Requests` | `AppError.NetworkRateLimited` | Prompt user: "Rate limit reached. Please wait a moment before trying again." |
| Login Redirect / Private Marker detected | `AppError.PrivateAccountNotSupported` | Render informative state: "Private posts cannot be accessed without authentication." |
| Link matching `/stories/` pattern | `AppError.StoriesNotSupported` | Display message: "Stories are temporary and currently unsupported." |
| Extraction empty on `HTTP 200 OK` | `AppError.ParserOutdatedError` | Auto-trigger background `config.json` refresh. If unchanged, notify user: "Parser update required." |
| No active internet connection | `AppError.OfflineNetworkError` | Show offline indicator: "Network unavailable. Check your connection." |

---

# **Download Pipeline & Android Scoped Storage Design**

CleanSaver targets Android 10+ (API 29 to API 35\) and uses Scoped Storage conventions to write binary streams to public media storage without demanding broad permissions.

```
                              [Start Download Request]
                                         |
                                         v
                         Check Room DB & MediaStore URI
                                         |
                       Exists? /                   \ New Media
                              v                     v
                  [Prompt Re-Download]     Insert ContentValues
                                           (IS_PENDING = 1)
                                                    |
                                                    v
                                         Open MediaStore OutputStream
                                                    |
                                                    v
                                         Stream OkHttp Response Bytes
                                            (16 KB Buffer Loop)
                                                    |
                                 Success? /                   \ Network Drop / Cancel
                                         v                     v
                             Update ContentValues       Delete Pending MediaStore Row
                               (IS_PENDING = 0)         (Zero 0-byte orphan files)
                                         |
                                         v
                             Record Metadata in Room DB
```

## **Atomic MediaStore Writing Flow**

To prevent incomplete or corrupted zero-byte media files from cluttering the user's gallery during network drops or early cancellations, all writes follow an atomic transaction sequence using `ContentResolver` and the `IS_PENDING` flag:

```
val contentValues = ContentValues().apply {
    put(MediaStore.MediaColumns.DISPLAY_NAME, "CleanSaver_${shortcode}_${slideIndex}")
    put(MediaStore.MediaColumns.MIME_TYPE, if (isVideo) "video/mp4" else "image/jpeg")
    put(MediaStore.MediaColumns.RELATIVE_PATH, if (isVideo) "Movies/CleanSaver" else "Pictures/CleanSaver")
    put(MediaStore.MediaColumns.IS_PENDING, 1)
}

val uri = contentResolver.insert(targetCollectionUri, contentValues)

try {
    contentResolver.openOutputStream(uri!!).use { outputStream ->
        response.body!!.byteStream().use { inputStream ->
            val buffer = ByteArray(16 * 1024) // 16 KB buffer
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                // Emit current download progress percentages to UI state
            }
            outputStream.flush()
        }
    }
    contentValues.clear()
    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
    contentResolver.update(uri, contentValues, null, null)
} catch (e: Exception) {
    // Delete incomplete file entry to maintain gallery integrity
    contentResolver.delete(uri!!, null, null)
    throw e
}
```

## **Concurrency Controls & Background Operations**

* **Multi-Slide Carousel Concurrency:** Multi-item downloads process using a Kotlin `Semaphore(permits = 2)`. This limits simultaneous downloads to 2 active network connections, preventing mobile radio saturation and OS socket starvation.  
* **Background Operations:** Long-running downloads are managed by a Foreground `WorkManager` `CoroutineWorker`. This task displays a standard system status notification showing progress percentages, allowing execution to finish even if the user exits the app or returns to Instagram.

## **File Deduplication Engine**

Before executing a network fetch, CleanSaver runs a two-step validation:

1. Queries the local Room database for matching `(shortcode, slide_index)` records.  
2. If a database record exists, checks `ContentResolver` to confirm the target file URI remains accessible on external storage.  
3. If verified, the user is presented with options: *"Media already saved to gallery — Open File or Download Again"*.

---

# **Local Data Model & Persistence Schema**

## **Room Database Schema (`downloaded_media`)**

Local file history is stored in an embedded SQLite database managed via Room. Column names match `DownloadedMediaEntity` in the LLD.

```
CREATE TABLE IF NOT EXISTS `downloaded_media` (
    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    `shortcode` TEXT NOT NULL,
    `slide_index` INTEGER NOT NULL DEFAULT 0,
    `media_type` TEXT NOT NULL,
    `mediastore_uri` TEXT NOT NULL,
    `file_name` TEXT NOT NULL,
    `file_size_bytes` INTEGER NOT NULL,
    `source_url` TEXT NOT NULL,
    `created_at_ms` INTEGER NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS `index_downloaded_media_shortcode_slide_index` 
ON `downloaded_media` (`shortcode`, `slide_index`);
```

## **Room Data Access Object (DAO) Interface**

```
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
```

## **Jetpack DataStore Preferences Key Store**

Application settings and cached values persist within Jetpack DataStore Preferences using the following layout:

| DataStore Key | Primitive Type | Default Value | Functional Purpose |
| :---- | :---- | :---- | :---- |
| `theme_mode` | `String` | `"SYSTEM"` | App color palette setting (`"SYSTEM"`, `"LIGHT"`, or `"DARK"`). |
| `clipboard_autodetect` | `Boolean` | `false` | Automatically inspects clipboard contents for media links upon app launch. |
| `config_json` | `String` | Bundled asset JSON | Full raw payload string of active operational extraction rules. |
| `config_etag` | `String` | `""` | HTTP ETag string used to validate static `config.json` updates. |
| `config_last_sync_ms` | `Long` | `0L` | Epoch millisecond timestamp tracking the most recent Remote Config sync. |
| `fair_use_accepted` | `Boolean` | `false` | Records legal/fair-use disclaimer acceptance state. |

---

# **Security, Privacy & APK Footprint Design**

## **Android Manifest & Permission Declarations**

CleanSaver maintains a minimal manifest surface area to preserve privacy and reduce security vectors.

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
        android:networkSecurityConfig="@xml/network_security_config"
        android:supportsRtl="true"
        android:theme="@style/Theme.CleanSaver">

        <activity
            android:name=".ui.home.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <activity
            android:name=".ui.share.ShareReceiverActivity"
            android:excludeFromRecents="true"
            android:exported="true"
            android:theme="@style/Theme.CleanSaver.Translucent">
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

## **Network Security Configuration (`res/xml/network_security_config.xml`)**

Encrypted transport is enforced globally across all network operations. Unencrypted HTTP cleartext traffic is explicitly disallowed.

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

## **Ephemeral Storage Isolation**

When executing fallback HTML parsing via `EphemeralWebViewExtractor`, strict sandbox isolation is applied:

```
webView.settings.apply {
    javaScriptEnabled = true
    domStorageEnabled = false
    databaseEnabled = false
    allowFileAccess = false
}
CookieManager.getInstance().setAcceptCookie(false)
CookieManager.getInstance().removeAllCookies(null)
```

Upon completing an extraction operation, the web container executes `.destroy()` to purge intermediate DOM memory, transient buffers, and script artifacts.

## **Application Packaging & Binary Optimization**

To achieve a target distribution size under **5 MB**, build configurations employ aggressive code shrinking, resource stripping, and dependency optimization:

```
android {
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
}
```

Third-party dependencies are restricted to core Android Jetpack libraries, OkHttp, and Kotlin Coroutines. Heavy reflection frameworks, specialized analytics libraries, and large image-rendering runtimes are excluded.

---

# **Non-Functional Requirements & Performance Targets**

| Metric / NFR Category | SLA Target Parameter | Verification Methodology |
| :---- | :---- | :---- |
| **Cold Start Time** | `<600 ms` on mid-range hardware (Snapdragon 6xx/7xx series) | Measured via Macrobenchmark testing suite. |
| **Link Extraction Latency** | Median `<2.5 seconds` on stable 4G network | Measured from Intent receipt to initial preview render. |
| **Release Package Size** | `<5 MB` download size (APK / AAB) | Verified via Android Studio APK Analyzer build step. |
| **Operational Infra Cost** | `₹0 / month` total hosting expenditure | Rely on free-tier GitHub Pages hosting for static JSON config updates. As an optional alternative, [Firebase Remote Config](https://firebase.google.com/docs/remote-config/pricing) offers up to 100K daily fetches for free, though static JSON avoids extra SDK dependencies. |
| **Crash-Free Session Rate** | `>99.5%` crash-free usage sessions | Monitored via local unit test suites and opt-in system telemetry. |
| **Offline Functionality** | `100%` functionality for saved media library | Complete offline operation for local browsing and media playback. |

---

# **Testing, CI/CD & Operational Maintenance Workflow**

## **Automated Continuous Integration Pipeline (GitHub Actions)**

Every pull request targeted to the `main` branch triggers an automated build verification workflow:

```
[Pull Request Created] 
          |
          v
[1. Static Analysis] ---------> Detekt & Ktlint Style Enforcement
          |
          v
[2. Unit Test Suite] ---------> Regex Validation & Canonicalization Tests
          |                     Offline Fixture Parsing Tests
          |
          v
[3. Instrumentation Tests] ---> Room Database Migration Verification
          |                     Scoped Storage File I/O Tests
          |
          v
[4. APK Build Analysis] ------> Assert Release Size <5 MB
```

1. **Static Code Analysis:** Executes `Detekt` and `Ktlint` to enforce architectural rules, code quality standards, and formatting guidelines.  
2. **Unit Testing Suite:** Executes unit tests validating URL parser logic, canonical shortcode generation, and extractor parsing against stored static HTML/JSON fixtures.  
3. **Instrumentation Testing:** Runs Android instrumentation tests on managed emulators to verify Room database operations and Scoped Storage file writing.  
4. **Size Assertions:** Compiles the production release build and verifies the final binary size remains below the 5 MB threshold.

## **Parser Fix Workflow**

When external platforms update their web markup structures, extraction rules can be updated without rebuilding or redistributing the binary application.

```
[Platform HTML Layout Updated]
              |
              v
[Synthetic Monitor / User Error Notification]
              |
              v
[Developer Updates Local config.json]
              |
              v
[Execute Integration Tests Against Live Markup]
              |
              v
[Merge PR to Configuration Repository]
              |
              v
[GitHub Pages Automated Deployment (<60s)]
              |
              v
[Clients Fetch Updated Rules via ETag Sync (Zero App Update Required)]
```

1. **Issue Identification:** Platform markup shifts are detected via automated synthetic integration tests or elevated client-side `ParserOutdatedError` rates.  
2. **Rule Correction:** The developer updates the extraction patterns (regular expressions, DOM selectors, or JSON paths) within the repository's `config.json` file.  
3. **Validation:** Local test suites evaluate updated extraction patterns against live web fixtures to confirm resolution.  
4. **Deployment:** The PR is merged to the main branch. GitHub Pages deploys the updated `config.json` file to the CDN edge in under 60 seconds.  
5. **Client Propagation:** Installed applications automatically pick up the new configuration file during their next scheduled sync or manual rule refresh, restoring functionality without requiring an application update via Play Store.

## **Distribution & Operational Overhead**

While establishing a Google Play Developer Account involves a [\$25 one-time fee (\~₹2,100) and mandatory 12-tester closed testing verification](https://www.iconikai.com/blog/google-play-developer-account-fee-2026), CleanSaver can be distributed directly as signed APK releases through GitHub Releases and F-Droid repositories. This approach maintains a ₹0 ongoing operational cost model and provides an alternative distribution path independent of centralized app marketplaces.