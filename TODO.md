# CleanSaver Development Checklist & TODO

This document tracks the end-to-end engineering implementation of **CleanSaver** (v1.0 MVP), following the specifications in:
- [`docs/PRD — CleanSaver (Ad-Free, Zero-Cost Public Reel & Post Saver).md`](docs/PRD%20—%20CleanSaver%20(Ad-Free,%20Zero-Cost%20Public%20Reel%20%26%20Post%20Saver).md)
- [`docs/HLD — CleanSaver (System & Android High-Level Design).md`](docs/HLD%20—%20CleanSaver%20(System%20%26%20Android%20High-Level%20Design).md)
- [`docs/LLD — CleanSaver (Kotlin Interfaces, Schemas & Implementation Spec).md`](docs/LLD%20—%20CleanSaver%20(Kotlin%20Interfaces,%20Schemas%20%26%20Implementation%20Spec).md)

---

## Progress Overview
- **Status:** Complete v1.0 MVP Architecture & Implementation ✅
- **Target Platform:** Android 10+ (API 29–35)
- **Tech Stack:** Kotlin 2.0, Jetpack Compose (Material 3), OkHttp 4, Kotlinx Serialization, Room (KSP), DataStore Preferences, WorkManager.

---

## Phase 1: Project Scaffolding & Build System
- [x] Root Gradle build configuration: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`
- [x] Version catalog: `gradle/libs.versions.toml` (addresses Gap 2)
- [x] Gradle wrapper configuration (`gradle/wrapper/gradle-wrapper.properties`, `gradlew`, `gradlew.bat`)
- [x] Git configuration: `.gitignore`
- [x] App module configuration: `app/build.gradle.kts`
- [x] Proguard rules: `app/proguard-rules.pro` with serialization, Room, and OkHttp rules (addresses Gap 3)
- [x] Bundled default config asset: `app/src/main/assets/default_config.json` (addresses Gap 1)
- [x] Android Manifest: `app/src/main/AndroidManifest.xml` (permissions, MainActivity, ShareReceiverActivity, WorkManager init, ForegroundService declaration)
- [x] Network Security Config: `app/src/main/res/xml/network_security_config.xml`
- [x] App strings, colors, and base XML resources: `app/src/main/res/values/`

---

## Phase 2: Domain Layer
- [x] Domain models (`com.cleansaver.app.domain.model`):
  - [x] `MediaType` (VIDEO, IMAGE)
  - [x] `UrlKind` (REEL, POST)
  - [x] `ParsedUrl`
  - [x] `MediaCandidate`
  - [x] `AppError` sealed hierarchy
  - [x] `DownloadProgress` sealed hierarchy
- [x] Repository interfaces (`com.cleansaver.app.domain.repository`):
  - [x] `MediaRepository` (includes deduplication check `isMediaAlreadyDownloaded`)
  - [x] `RemoteConfigRepository`
  - [x] `LibraryRepository`
  - [x] `UserPrefsRepository`
- [x] Domain use cases (`com.cleansaver.app.domain.usecase`):
  - [x] `ValidateUrlUseCase` (canonicalization, query parameter stripping, `/stories/` rejection)
  - [x] `ExtractMediaUseCase`
  - [x] `DownloadMediaUseCase`
  - [x] `RefreshConfigUseCase`

---

## Phase 3: Data Layer — Remote Config & Extractor Engine
- [x] Remote config DTOs (`com.cleansaver.app.data.remote.dto`):
  - [x] `ParserConfigDto`, `UrlPatternsDto`, `StrategyDto`
- [x] Remote config repository (`com.cleansaver.app.data.repository`):
  - [x] `RemoteConfigRepositoryImpl` (ETag caching, 304 handling, asset fallback)
- [x] Extractor engine (`com.cleansaver.app.data.extractor`):
  - [x] `MediaExtractorStrategy` interface
  - [x] `EmbedHttpExtractor` (HTTP_EMBED strategy with regex matching & private account check)
  - [x] `EphemeralWebViewExtractor` (WEBVIEW_EPHEMERAL fallback strategy with tracker blocking)
  - [x] `MediaExtractorChain` (strategy orchestrator, terminal error short-circuit, retry on outdated config)

---

## Phase 4: Data Layer — Persistence, MediaStore & WorkManager
- [x] Room SQLite persistence (`com.cleansaver.app.data.local`):
  - [x] `DownloadedMediaEntity` (indexed by shortcode + slide_index)
  - [x] `DownloadedMediaDao` (reactive Flow queries, upsert, delete)
  - [x] `CleanSaverDatabase` abstract Room database
- [x] DataStore preferences (`com.cleansaver.app.data.repository`):
  - [x] `UserPrefsRepositoryImpl` (theme, clipboard autodetect, fair use flag)
- [x] Gallery saving pipeline (`com.cleansaver.app.data.local`):
  - [x] `MediaStoreWriter` (scoped storage, atomic write via `IS_PENDING`, 16 KB buffer streaming)
- [x] Background workers (`com.cleansaver.app.data.worker`):
  - [x] `MediaDownloadWorker` (WorkManager task with deduplication and foreground notification)
  - [x] `CleanSaverWorkerFactory` (manual injection factory without Hilt, addresses Gap 4)
  - [x] `NotificationHelper` (handles download notification channel and ForegroundInfo)
- [x] Repository implementations:
  - [x] `MediaRepositoryImpl`
  - [x] `LibraryRepositoryImpl`

---

## Phase 5: Dependency Injection & Application Lifecycle
- [x] Service locator / DI container: `com.cleansaver.app.di.AppContainer`
- [x] Application class: `com.cleansaver.app.CleanSaverApplication` (initializes AppContainer & custom WorkerFactory)

---

## Phase 6: Presentation Layer (Jetpack Compose & ViewModels)
- [x] UI Theme & Design System (`com.cleansaver.app.ui.theme`):
  - [x] `Color.kt`, `Type.kt`, `Theme.kt` (dynamic theme support: System, Light, Dark)
- [x] State representations (`com.cleansaver.app.ui.state`):
  - [x] `HomeUiState` (with carousel multi-selection & duplicate prompt state), `ShareSheetUiState`, `LibraryUiState`, `SettingsUiState`
- [x] ViewModels (`com.cleansaver.app.ui.*`):
  - [x] `HomeViewModel` (handles URL validation, carousel slide toggles, and FR-4 duplicate prompting)
  - [x] `ShareReceiverViewModel`
  - [x] `LibraryViewModel`
  - [x] `SettingsViewModel` (theme switcher, clipboard auto-detect, remote sync)
- [x] Screen composables & navigation:
  - [x] `MainActivity` & `HomeScreen` (URL input, paste button, carousel slide checkboxes, download progress bar, duplicate alert dialog)
  - [x] `ShareReceiverActivity` & Share Bottom Sheet (1-tap save on system share)
  - [x] `LibraryScreen` (filter chips, media list, in-app preview, system share, delete)
  - [x] `SettingsScreen` (theme selector chips, clipboard auto-detect, trust & fair use block, clear history)
  - [x] `DisclaimerBottomSheet` (first-run disclosure dialog)
  - [x] `MediaPreviewDialog` (in-app full-screen looping media player & photo viewer)

---

## Phase 7: Unit Testing & Verification
- [x] `ValidateUrlUseCaseTest` (unit tests for URL extraction, canonicalization, rejection)
- [x] `ParserConfigDtoTest` (schema verification and deserialization)
- [x] `MediaExtractorChainTest` (strategy execution order, private account failure, maintenance mode)
- [x] `MediaStoreWriterNamingTest` (validates file naming, mime types, and target directory mappings)
- [x] `RemoteConfigNetworkTest` (validates HTTP 200 parsing and HTTP 304 ETag caching)

---

## Phase 8: v2.0 Power Features & Minimalist Polish
- [x] Native Audio-Only Extraction (`.m4a` via Android `MediaExtractor` + `MediaMuxer`, 0 MB FFmpeg bloat)
- [x] Caption, Hashtags & Creator Metadata Extraction + 1-tap Copy Caption / Copy Hashtags
- [x] 1-Tap Instant Auto-Save on Share Sheet toggle
- [x] Batch Queue (multi-line URL paste with polite rate-limit pacing)
- [x] Cover / Thumbnail Save option on Videos
- [x] Multi-Platform Modular Extractors (Instagram, Threads, X/Twitter, Reddit, Pinterest)
- [x] Smart Library: Favorites, Custom Collection Tags, Multi-Select Bulk Share & Bulk Delete, Sort (Newest/Oldest/Largest/Creator), Storage Breakdown
- [x] Custom File Naming (`CleanSaver_code` vs `Creator_Date`) & Custom Gallery Subfolder
- [x] Biometric Vault Lock (`BiometricPrompt` gating Library tab)
- [x] Stream Quality Preference (`Best` / `Data Saver`) & Wi-Fi Only Downloads Guard
- [x] Built-in Extraction Diagnostics Log viewer in Settings
- [x] In-App GitHub Releases Update Checker
- [x] Android Quick Settings Tile (`QuickSaveTileService`) for 1-tap Paste & Save
- [x] Unit tests for multi-platform URLs, batch parsing, hashtags, semver update check, and custom file naming
