# Stow

**Minimal, ad-free, 100% on-device media saver for Android.**

Stow saves public videos, Reels, photos, carousels, and `.m4a` audio directly to your Android gallery — with zero ads, zero logins, zero backend servers, and zero analytics.

## Features
- **Multi-Platform:** Instagram, Threads, X (Twitter), Reddit (with native DASH audio muxing), Pinterest, TikTok, and YouTube Shorts.
- **Native Audio Extraction:** Extract standalone `.m4a` audio from videos using Android's `MediaExtractor` + `MediaMuxer` (0 MB FFmpeg overhead).
- **Caption & Creator Metadata:** Copy captions/hashtags in 1 tap and search your Library by caption, `@creator`, or `#tag`.
- **Zero-Friction Workflows:** 1-tap Instant Share Save, multi-line Batch Queue, Quick Settings Tile, and Home Screen Widget.
- **Smart Library & Vault:** Visual 2-column grid with video thumbnails, Favorites, custom collection tags, sorting, storage breakdown, bulk share/delete, and optional Biometric Vault lock.
- **Power-User Controls:** Custom file naming (`Stow_code` / `Creator_Date`), custom Gallery subfolder, Best vs Data Saver quality preference, Wi-Fi-only guard, extraction diagnostics log, and GitHub Releases update checker.

## Architecture & Docs
- **Tech Stack:** Kotlin 2.0, Jetpack Compose (Material 3), OkHttp 4, Kotlinx Serialization, Room, DataStore, Coil + `coil-video`, WorkManager, AndroidX Biometric.
- **Target:** Android 10–15 (API 29–35).
- See [`TODO.md`](TODO.md) and [`docs/`](docs/) for the full design specs and checklist.

## Disclaimer
Stow is an independent open-source utility unaffiliated with Meta, X, Reddit, Pinterest, TikTok, or YouTube. Users are responsible for respecting creator rights and fair-use guidelines.
