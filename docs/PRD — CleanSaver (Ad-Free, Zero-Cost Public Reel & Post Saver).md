# **Product Requirements Document: CleanSaver**

# **Ad-Free, Zero-Cost Public Reel & Post Saver for Android**

# **1\. Document Metadata**

| Parameter | Value |
| :---- | :---- |
| Working Product Name | CleanSaver |
| Target Platform | Android (Min SDK 29 / Android 10+, Target SDK 35\) |
| Version / Status | v1.0 MVP Specification — Ready for Engineering |
| Document Date | November 22, 2026 |
| Cost & Monetization Policy | 100% Free Forever · Zero Ads · Zero Subscriptions · Zero Data Collection · ₹0/month Running Cost |

# **2\. Executive Summary & Core Product Principles**

CleanSaver is an open-source, utility-first Android application designed to save public Instagram Reels, photos, and carousels directly to the local phone gallery with zero friction. It is built to resolve key user frustrations caused by current market alternatives: aggressive full-screen ads, mandatory user logins, heavy SDK bloat, and battery-draining background trackers.

# **Core Product Goals**

* **Maximum User Trust:** Zero ads, zero paywalls, zero account logins, and zero data analytics SDKs.  
* **Lightweight Footprint:** Native Kotlin \+ Jetpack Compose execution maintaining an installed APK size of under 5 MB.  
* **Privacy-First Architecture:** 100% on-device parsing and downloading. All network requests run directly from the user's phone IP to public endpoints.  
* **₹0 Developer Running Cost:** Zero backend server dependencies. The application operates strictly peer-to-edge using public web endpoints and free static hosting for parser rules.

# **Market Context & User Demand**

While Instagram supports native reel saving in select regions, native saves impose significant limitations:

* They stamp a prominent creator username/logo watermark over the video content.  
* Downloads can be disabled by creators on a per-post basis ([Cashify](https://www.cashify.in/news/download-instagram-reels-directly-with-this-new-feature), [Postiz](https://postiz.com/blog/how-to-save-instagram-reels)).

Existing third-party downloaders on the Google Play Store degrade user experience through interstitial pop-up ads, deceptive subscription prompts, and risky inline login requirements. CleanSaver provides a clean, local-only alternative that respects user privacy and device storage.

# **3\. Scope Boundaries**

# **In Scope (v1.0 MVP)**

* **Public Media Ingestion:** Public Instagram Reels (MP4 format).  
* **Public Post Ingestion:** Single-image posts (JPG format) and single-video posts (MP4 format).  
* **Carousel Support:** Multi-slide carousels with individual item selection or batch download options.  
* **Android OS Integration:** System Share Sheet receiver (`ACTION_SEND`) and manual URL paste interface.  
* **Local Media Storage:** Direct media pipeline into system `Movies/CleanSaver/` and `Pictures/CleanSaver/` directories via Android MediaStore.  
* **Local Storage Library:** On-device download history powered by SQLite/Room.  
* **Dynamic Resilience:** Lightweight static remote config parser fetched over HTTPS.

# **Strictly Out of Scope (Non-Goals)**

* **No Authentication:** No Instagram account login, session cookie storage, or OAuth flows.  
* **No Ephemeral/Private Media:** No Stories, Highlights, or Private Account posts (saving these requires logged-in session cookies and risks triggering user account verification checkpoints or security freezes — [ContentStudio](https://contentstudio.io/blog/can-people-see-who-viewed-their-instagram-profile)).  
* **No Multi-Platform Expansion:** No YouTube, TikTok, or X/Twitter scrapers in v1.0.  
* **No Tracking or Monetization:** No AdMob, Meta Audience Network, Firebase Analytics, Mixpanel, or third-party telemetry.  
* **No Proxy Infrastructure:** No developer-hosted proxy servers, backend API gateways, or remote databases.

# **4\. Functional Requirements**

## **FR-1: Link Ingestion & Validation**

* The application shall register an intent filter for `android.intent.action.SEND` with `text/plain` MIME type to accept links directly from the Instagram Share Sheet.  
* The main screen shall provide an explicit text input field for manual URL pasting with an interactive "Paste" button.  
* The application shall offer an optional "Clipboard Auto-Detect" setting, which must remain **OFF by default** to preserve system clipboard privacy.  
* The URL ingestion engine shall strictly validate input strings against known public URL patterns:  
  * `https://www.instagram.com/reel/{SHORTCODE}/`  
  * `https://www.instagram.com/p/{SHORTCODE}/`  
  * `https://www.instagram.com/reels/{SHORTCODE}/`  
* Non-matching or malformed URLs must be rejected immediately at the UI layer without initiating network requests.

## **FR-2: On-Device Public Media Extraction**

* The parsing engine shall execute entirely on-device via a modular `MediaExtractor` interface.  
* Extractor module shall request public embedding and open-graph web data from public endpoints using standard HTTP GET calls with generic web user-agent strings.  
* If static HTTP parsing fails due to client-side DOM rendering changes, the engine shall fall back to an ephemeral background Android `WebView` that renders the public DOM in isolation, extracts target source tags (`<meta property="og:video">`, `<meta property="og:image">`), and terminates without persisting cookies or site data.  
* Extracted outputs must be typed into: `SingleVideo`, `SinglePhoto`, or `Carousel([MediaItem])`.

## **FR-3: Zero-Cost Remote Parser Config**

* To insulate the application against client-side breakdown caused by Instagram frontend updates, the app shall fetch a lightweight JSON configuration (`config.json`, size \< 5 KB) hosted on GitHub Pages.  
* **Caching Policy:** The app shall store the JSON locally using HTTP ETag validation and enforce a mandatory 24-hour local cache window to minimize network requests.  
* **Bundled Fallback:** The compiled APK must include a static `default_config.json` inside assets to ensure full offline/first-launch functionality without an initial remote fetch.  
* **Schema Content:** The configuration file must declare a `schema_version` compatibility field, URL extraction regex patterns (`url_patterns`), an ordered list of extraction strategies (`strategies`), rotating user-agent strings, a config TTL, and an emergency operational flag (`maintenance_mode`: `boolean`, `maintenance_message`: `string`). The authoritative schema is defined by `ParserConfigDto` in the LLD.

```
{
  "schema_version": 1,
  "min_app_version_code": 1,
  "maintenance_mode": false,
  "maintenance_message": "",
  "config_ttl_hours": 24,
  "user_agents": [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
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

## **FR-4: Native Local Download & Gallery Saving**

* Media streams shall be written directly to system media directories (`Environment.DIRECTORY_MOVIES/CleanSaver` and `Environment.DIRECTORY_PICTURES/CleanSaver`) using the Android `MediaStore` API.  
* The application must comply fully with Scoped Storage guidelines, requiring **zero broad storage permissions** (`READ_EXTERNAL_STORAGE` or `WRITE_EXTERNAL_STORAGE`) on Android 10+ (API 29+).  
* The download interface shall display real-time progress indicators: percentage complete, bytes downloaded, total file size, and explicit Cancel/Retry controls.  
* The system shall execute shortcode deduplication against the local database prior to downloading; if the file exists, prompt the user: *"Item already saved. Download again?"*

## **FR-5: Local-Only Library & History**

* Download metadata (shortcode, media type, local MediaStore URI, file size, thumbnail cache path, timestamp) must be stored in an on-device SQLite database via Room.  
* The Library UI shall offer toggleable Filter Chips (`All`, `Reels`, `Photos`).  
* Each library card must support:  
  * **Open:** Launch file in the system default video/photo viewer.  
  * **Share:** Send file via native Android Share Sheet (`ACTION_SEND`).  
  * **Delete:** Remove entry from Room DB and delete underlying media file via MediaStore.  
* The app shall include a global "Clear All History" utility under Settings.

## **FR-6: Trust, Transparency & Fair-Use Safeguards**

* On first app launch, a non-intrusive bottom sheet dialog must present the core operational boundaries.  
* A permanent "Trust & Fair Use" block within Settings must detail:  
  1. CleanSaver is an independent utility unaffiliated with Meta Platforms or Instagram.  
  2. Users bear sole responsibility for ensuring saved media complies with original creator rights and personal fair-use standards.  
  3. CleanSaver collects, stores, and transmits zero user identifiers, analytical metrics, or network metadata.

# **5\. User Experience & Screen-by-Screen Flow**

```
[ Instagram App ] ──(Share Intent)──► [ Screen 2: Instant Bottom Sheet ] ──► [ Direct Save to Gallery ]
                                                │
[ Manual URL Copy ] ──► [ Screen 1: Home ] ─────┴─► [ Processing / Preview ] ──► [ Direct Save to Gallery ]
                             │
                             ├────────────────────► [ Screen 3: Saved Library ]
                             │
                             └────────────────────► [ Screen 4: Settings & About ]
```

# **Screen 1 — Home & Paste Screen**

* **Top Bar:** Clean title ("CleanSaver") with subtle icon buttons for Library and Settings.  
* **Trust Banner:** Highlighted top card displaying: `100% On-Device · Zero Ads · No Account Required`.  
* **Input Section:** Large text field with dynamic hint text (`Paste public Instagram link...`), explicit "Paste" button, and a primary "Fetch Media" Action Button.  
* **Recent Activity Carousel:** Horizontal list displaying thumbnails of the 3 most recently downloaded items for immediate access.

# **Screen 2 — Share-Sheet Instant Bottom Sheet**

* **Trigger:** Launched modally when receiving a link via Android system `ACTION_SEND`.  
* **Visual States:**  
  * *State A (Parsing):* Indeterminate progress spinner with message "Extracting public media...".  
  * *State B (Single Item Preview):* High-resolution thumbnail preview, media format badge (MP4/JPG), file size calculation, and a primary "Save to Gallery" button.  
  * *State C (Carousel Selection):* Horizontal scrolling grid of media items with checkboxes enabling selective or batch download ("Save Selected (3)").  
* **Background Execution:** Downloads process via foreground service notification, allowing the user to remain within the Instagram app without interruption.

# **Screen 3 — Saved Library**

* **Layout:** Two-column responsive grid view displaying locally cached thumbnails.  
* **Filtering Controls:** Top sticky chip row: `All Items`, `Reels`, `Photos`.  
* **Media Card:** Displays thumbnail, play icon overlay (for videos), file size badge, download timestamp, and an overflow menu (`Share`, `Re-save`, `Delete`).  
* **Built-in Preview:** Tapping a card launches a lightweight full-screen modal featuring video playback (via ExoPlayer/Media3) or photo zooming, with a system gallery handoff option.

# **Screen 4 — Settings & About**

* **Preferences Group:**  
  * App Theme Selector (`System Default`, `Light`, `Dark`).  
  * Clipboard Auto-Detect Switch (Default: `OFF`, with privacy warning callout).  
* **Parser Engine Group:**  
  * Parser Rules Status: `Parser rules: Up to date`.  
  * Action Button: `Check for Rule Updates` (triggers manual sync of remote `config.json`).  
* **Data Control Group:**  
  * Action Button: `Clear Download History` (with confirmation dialog).  
* **Legal & Disclaimer Group:**  
  * Privacy Commitment (Explicit "No Data Collected" statement).  
  * Fair-Use & Copyright Notice.  
  * Source Code Link (GitHub repository redirect).

# **6\. Technical Architecture & Recommended Stack**

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Jetpack Compose UI Layer                        │
│    [Home Screen]    [Share BottomSheet]    [Library]    [Settings]     │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ (Observes UI State)
┌───────────────────────────────────▼────────────────────────────────────┐
│                              ViewModel Layer                           │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ (Executes Use Cases)
┌───────────────────────────────────▼────────────────────────────────────┐
│                           Repository Layer                             │
│   ┌───────────────────────────────┴──────────────────────────────┐     │
│   │                                                              │     │
│ ┌─▼────────────────────────────┐               ┌─────────────────▼───┐ │
│ │ Local Data Source            │               │ Remote Config Engine│ │
│ │  - Room DB (History metadata)│               │  - OkHttp Client    │ │
│ │  - DataStore (User settings) │               │  - Cache / ETag     │ │
│ └──────────────────────────────┘               └─────────────────────┘ │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│                     On-Device Extraction Layer                         │
│ ┌──────────────────────────────┐     ┌───────────────────────────────┐ │
│ │ PrimaryParser (OkHttp + HTML)│ ──► │ FallbackParser (WebView DOM)  │ │
│ └──────────────────────────────┘     └───────────────────────────────┘ │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ (Writes Extracted Streams)
┌───────────────────────────────────▼────────────────────────────────────┐
│                   Android Storage Framework (API 29+)                  │
│  - MediaStore API (Movies/CleanSaver & Pictures/CleanSaver)            │
└────────────────────────────────────────────────────────────────────────┘
```

# **Recommended Technology Stack**

| Component | Technology | Rationale |
| :---- | :---- | :---- |
| **Language** | Kotlin 2.0+ | Modern memory management, coroutine support, and type safety. |
| **UI Framework** | Jetpack Compose \+ Material 3 | Declarative UI reducing boilerplate and optimizing layout performance. |
| **Architecture** | Clean Architecture \+ MVVM | Strict separation of concerns enabling modular parser updates. |
| **Networking** | OkHttp 4.x \+ Coroutines | Lightweight HTTP client; avoids heavy SDK inclusions; handles redirects. |
| **Local DB** | Room Database (SQLite) | Standardized, robust local object mapping for library history management. |
| **Key-Value Store** | Jetpack DataStore | Modern replacement for SharedPreferences; async preference reads. |
| **Media Playback** | Media3 ExoPlayer | Minimal composition for local video playback verification. |
| **Storage API** | Native Android `MediaStore` | Scoped Storage compliance; requires zero system permission prompts. |
| **Config Host** | GitHub Pages | Free static hosting delivering JSON rules; ₹0 operational overhead ([Firebase Remote Config](https://firebase.google.com/docs/remote-config/pricing) offers 100k daily fetches free, but static hosting eliminates proprietary SDK requirements). |

# **7\. Error Handling & Edge Cases Matrix**

| Scenario | Trigger Condition | System Behavior | User-Facing Message |
| :---- | :---- | :---- | :---- |
| **Private Account Post** | Input URL belongs to a private profile. | Extractor detects HTTP redirect to login page or missing Open Graph tags. Aborts extraction immediately; no login prompt shown. | *"This account is private. CleanSaver only supports public posts and Reels."* |
| **Story / Highlight Link** | Input URL contains `/stories/` or `/highlights/`. | URL validator flags non-supported endpoint before network execution. | *"Stories are not supported to keep your account safe without logins."* |
| **Deleted / Invalid Link** | Post removed or shortcode mis-typed. | HTTP endpoint returns status 404 or empty response body. | *"We couldn't find a public post at this link. Please check the URL."* |
| **Instagram Web Layout Change** | Meta alters public DOM structure/selectors. | PrimaryParser fails extraction. System attempts FallbackParser. If both fail, flags parser update requirement. | *"Parser update needed. Tap Refresh Rules or check back shortly."* |
| **Network Rate Limit** | Too many extraction requests from target IP. | Endpoint returns HTTP 429 status. System halts retries with exponential backoff. | *"Instagram temporarily limited requests from your network. Try again in a few minutes or switch Wi-Fi/mobile data."* |
| **Insufficient Local Storage** | Storage write fails due to `IOException`. | Disk check flags inadequate space before initiating file stream down. | *"Not enough storage space available on your device to save this file."* |
| **No Internet Connection** | Device offline during extraction step. | Network state listener catches offline status before request. | *"No internet connection. Please check your network and try again."* |

# **8\. Cost Breakdown & Sustainability**

# **Monthly Running Cost Model**

| Resource Layer | Provider / Architecture | Cost (INR) |
| :---- | :---- | :---- |
| Media Bandwidth | Direct client-to-edge network execution (User Data/Wi-Fi) | ₹0 / month |
| Proxy / Scraper Servers | None (100% On-Device Parsing) | ₹0 / month |
| User Data Database | Local SQLite/Room DB on user's device | ₹0 / month |
| Config File Hosting | GitHub Pages static CDN | ₹0 / month |
| **Total Running Overhead** | **Independent Execution Model** | **₹0 / month** |

# **Distribution Channel Comparison**

| Distribution Track | Initial Setup Fees | Operational Requirements / Risks | Developer Suitability |
| :---- | :---- | :---- | :---- |
| **Option A: GitHub Releases \+ F-Droid** *(Recommended)* | **₹0** (100% Free) | Zero Play Store policy risk; strong open-source developer trust; simple manual/APK updates. | Best choice for complete independence and zero financial cost. |
| **Option B: Google Play Console** | **\$25 USD** (\~₹2,100 INR) one-time registration fee | Requires 12-tester, 14-day closed testing track for new personal developer accounts ([IconikAI](https://www.iconikai.com/blog/google-play-developer-account-fee-2026), [Colorleaves](https://colorleaves.in/blog/google-play-console-developer-account-setup-india/)). Ongoing risk of Meta trademark/copyright enforcement actions. | Best for max reach, but carries account risk. |

# **9\. Compliance, Policy & Risk Mitigation**

# **Play Store & Branding Hygiene**

To minimize policy enforcement actions on commercial app distribution platforms:

* **Naming Policy:** The app name must remain strictly "CleanSaver". Terms such as "Instagram", "Insta", "IG", or "Reel Downloader" must not appear in the application title, package name (`com.cleansaver.app`), or app icon.  
* **Visual Identity:** Iconography must utilize neutral, modern vector graphics. Usage of Meta’s trademark camera logo, color gradients (pink/purple/yellow), or proprietary iconography is strictly prohibited.  
* **Listing Language:** App description text must explicitly state: *"CleanSaver is an independent utility application for saving personal media and is not affiliated with, endorsed by, or associated with Meta or Instagram."*

# **Data Safety Declarations**

Because CleanSaver integrates zero telemetry, analytics, or advertising SDKs:

* **Data Collection:** Declared as **"No Data Collected"** in standard application declarations.  
* **Data Sharing:** Declared as **"No Data Shared with Third Parties"**.  
* **Permissions:** Zero high-risk permissions requested (`Camera`, `Contacts`, `Location`, `Read SMS`, or broad `MANAGE_EXTERNAL_STORAGE` are omitted).

# **Platform Takedown Risk Disclosure**

Third-party utilities operating within social media ecosystems face inherent operational risks regardless of non-profit intent:

* Public media parsing logic is vulnerable to silent upstream structural changes by platform operators.  
* Applications published on centralized app stores remain susceptible to automated trademark or copyright complaints.  
* **Mitigation Plan:** Maintain the core codebase as open-source on GitHub. Treat GitHub Releases and F-Droid as primary deployment channels, ensuring user continuity if store listings are restricted.

# **10\. 2-Week Solo MVP Development Plan & QA Checklist**

# **Sprint Schedule**

```
Week 1: Core Engine & Storage
├── Day 1–2: Setup Android Project (Kotlin, Jetpack Compose, Material 3, Room, DataStore)
├── Day 3–4: Build URL Validator, Share-Sheet Intent Receiver & MediaExtractor Core
└── Day 5–7: Implement MediaStore Download Pipeline (Reels, Photos, Carousels) + Error Handling

Week 2: UI Polish, Privacy & Release
├── Day 8–9: Develop Saved Library Screen, Settings & Privacy/Fair-Use Notices
├── Day 10–11: Multi-Device QA (Android 10 to 15 testing across various screen sizes)
└── Day 12–14: Sign Production APK/AAB, Publish GitHub Privacy Policy & Release v1.0
```

## **Week 1: Core Architecture & Downloading Engine**

* **Days 1–2: Infrastructure & Foundation**  
  * Initialize repository with Kotlin, Jetpack Compose, and Material 3 design tokens.  
  * Configure room database for download history and DataStore for user preferences.  
  * Establish baseline application architecture (Clean MVVM).  
* **Days 3–4: Link Ingestion & Media Parsing Module**  
  * Implement URL parsing engine validating public shortcodes.  
  * Build Android `ACTION_SEND` Share-Sheet receiver activity.  
  * Implement `MediaExtractor` interface supporting `PrimaryParser` (HTML meta tag extractor) and dynamic Remote Config JSON reader.  
* **Days 5–7: Download Pipeline & Scoped Storage Integration**  
  * Build background download task framework utilizing OkHttp streams.  
  * Wire output directly to `MediaStore` API for `Movies/CleanSaver/` and `Pictures/CleanSaver/`.  
  * Write unit tests covering shortcode extraction, invalid link handling, and file deduplication logic.

## **Week 2: Interface, Testing & Deployment**

* **Days 8–9: User Interface & Privacy Integration**  
  * Finalize Home Screen, Share-Sheet Bottom Sheet, Library Grid, and Settings view.  
  * Implement dynamic Light/Dark theme switching using Material 3 palettes.  
  * Embed copyright disclaimers, first-launch disclosures, and parser rule refresh controls.  
* **Days 10–11: Quality Assurance & Edge-Case Validation**  
  * Execute test matrices across physical devices spanning Android 10 (API 29\) through Android 15 (API 35).  
  * Validate failure handling for private posts, stories, rate limits, and network dropouts.  
* **Days 12–14: Packaging & Release Strategy**  
  * Generate production-signed release APKs and Android App Bundles (AAB).  
  * Host static `privacy.html` and default `config.json` on GitHub Pages.  
  * Tag release `v1.0.0` on GitHub Releases and submit manifest to F-Droid (or launch Google Play Console closed testing track).

---

# **Pre-Release QA Verification Checklist**

- [ ] **Share Intent Receiver:** Launching CleanSaver via Instagram Share Sheet opens the direct Bottom Sheet instantly without visual lag or app crashes.  
- [ ] **Media Extraction Precision:**  
  - [ ] Public Reel URL extracts correct MP4 video stream without audio loss.  
  - [ ] Single photo post extracts full-resolution JPG image.  
  - [ ] Carousel post extracts all individual slides and presents multi-selection UI correctly.  
- [ ] **Boundary Rejections:**  
  - [ ] Private account link cleanly rejected with informative user message.  
  - [ ] Story / Highlight link rejected with explanatory non-support message.  
  - [ ] Invalid/garbage text string rejected at entry validation step.  
- [ ] **Storage Compliance:** Media saves correctly to system gallery without requesting legacy storage permissions on Android 10, 11, 12, 13, 14, and 15\.  
- [ ] **Offline & Fault Tolerance:** App handles airplane mode gracefully without crashing, displaying clear actionable retry alerts.  
- [ ] **Footprint & Performance:** Installed application size remains under 5.0 MB; zero memory leaks detected during carousel processing.

