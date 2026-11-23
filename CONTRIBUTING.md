# Contributing to Stow

Thank you for your interest in contributing to **Stow**! Every bug report, UI refinement, extractor improvement, and documentation fix helps keep Stow fast, private, and ad-free.

## Core Principles

Before opening a PR, please keep Stow's non-negotiable rules in mind:
- **100% On-Device:** Zero backend servers, zero cloud relays, zero logins.
- **Zero Ads & Zero Telemetry:** No analytics SDKs, ad networks, or tracking identifiers.
- **Calm Neo-Swiss UI:** Clean hierarchy, breathable spacing, and zero visual clutter.

## Getting Started

1. Fork [omkarspace/stow](https://github.com/omkarspace/stow) and clone your fork:
   ```bash
   git clone https://github.com/YOUR_USERNAME/stow.git
   cd stow
   ```
2. Open the project in **Android Studio** (JDK 17, Android SDK 35).
3. Run unit tests before making changes:
   ```bash
   ./gradlew test
   ```
4. Build a debug APK:
   ```bash
   ./gradlew assembleDebug
   ```

## Submitting a Pull Request

1. Create a descriptive branch (`feat/playlist-queue`, `fix/audio-cover-art`).
2. Keep commits focused and clear.
3. Ensure `./gradlew test` and `./gradlew lint` pass with zero errors.
4. Open a Pull Request and fill out the PR template.
