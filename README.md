# locationjoystick

![Build](https://img.shields.io/github/actions/workflow/status/locationjoystick/locationjoystick/release.yml?label=Build&style=flat-square)
![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)
![minSdk](https://img.shields.io/badge/minSdk-31%20(Android%2012)-green?style=flat-square)
![Kotlin](https://img.shields.io/badge/Kotlin-2.x-purple?style=flat-square)

No-root mock location app for Android. Spoof GPS anywhere using floating joystick, saved routes, and OSM-powered roaming — without touching system partition.

---

## Features

| Feature | Description |
|---------|-------------|
| **Map** | OpenStreetMap via MapLibre (GPU-accelerated, offline-capable). Tap to walk or teleport. Spoofed position shown as live marker. |
| **Last Position** | Restores last spoofed location on app restart. No manual re-entry needed. |
| **Joystick** | Floating overlay stays on top of any app. Drag to move in any direction at chosen speed. Draggable anywhere on screen. |
| **Speed Profiles** | Walk / Run / Bike presets, all user-editable. Anti-cheat warning when speed exceeds threshold. Accessible from floating widget. |
| **Routes** | Create waypoints on map → polyline. Two types: **straight** (direct segments) and **guided** (OSRM road-following). Save, edit, replay, loop, or record in real time. |
| **Roaming** | Set center, radius, duration. Auto-walks randomly within radius. Optional road-following via OSRM. Configured via bottom sheet on Map screen. |
| **Favorites** | Save named map positions. Instantly teleport or walk to any. Add via inline dialog or MapPicker with Nominatim search. |
| **Floating Widget** | Configurable quick-access panel floats over other apps. Collapsible FAB → expanded panel with user-selected controls. |
| **Click-to-Move** | Long-press map → "Walk here" or "Teleport here". Walk advances at current speed; teleport jumps instantly. |
| **QR Transfer** | Share or import config between devices via QR codes. Export splits into scannable chunks; import scans and reassembles. |
| **GPS Realism** | Makes spoofed GPS indistinguishable from a real chip. Toggle per-feature: bearing hold when stationary, realistic altitude drift, warm-up accuracy envelope (converges over 30 s), satellite count in fix (7–14), and natural signal dropouts. All off by default; enable selectively in Settings. |
| **Import/Export** | All data to/from JSON (routes, favorites, speed profiles, widget config, roaming defaults, jitter settings). |
| **Background Service** | Spoofs while minimized or screen off via foreground service. Low-priority notification. |
| **Onboarding** | Multi-step first-run flow: location permission, overlay permission, mock location enablement. |
| **About** | App version, GitHub, privacy policy, and open-source credits. Accessible from home screen and navigation drawer. |

---

## What is locationjoystick?

GPS spoofing app built on Android's official mock location API. No root, no Xposed, no system mods. Enable Developer Options, pick locationjoystick as mock location provider → device believes it's wherever you say.

Primary use case: location-based games. Walk saved routes, roam a neighborhood automatically, or nudge position with floating joystick while a game runs in foreground.

Also useful for: privacy (mask real location), QA testing (simulate movement at desk), development (test geofences, location triggers, map features).

---

## Download

Pre-built APKs on [Releases page](https://github.com/locationjoystick/locationjoystick/releases).

Sideload:

```bash
adb install locationjoystick-vX.X.X.apk
```

Or transfer APK to device and open with file manager (allow installs from unknown sources).

---

## Setup Guide

### Step 1 — Enable Developer Options

Settings → About phone → tap **Build number** seven times → Developer Options unlocked.

### Step 2 — Select Mock Location App

Settings → System → Developer Options → **Select mock location app** → choose **locationjoystick**.

### Step 3 — Grant Overlay Permission

Open locationjoystick → tap **Grant Permission** → find locationjoystick in list → enable toggle → return to app.

### Step 4 — Start Spoofing

Open locationjoystick → tap map to teleport or use joystick → open target app → locationjoystick keeps running in background.

> **Note:** Some apps detect mock locations. Check the app's community for current workarounds.

---

## Test Coverage

Coverage tracked via [kotlinx-kover](https://github.com/Kotlin/kotlinx-kover). Aggregates all modules into a single report.

```bash
./gradlew koverHtmlReport   # HTML report → build/reports/kover/html/index.html
./gradlew koverXmlReport    # XML report for CI → build/reports/kover/report.xml
```

Or via Make:

```bash
make coverage        # generate both reports
make coverage-open   # open HTML in browser
```

---

## Building

### Prerequisites

- Android Studio Hedgehog or newer (or JDK + Android SDK command-line tools)
- Java 17
- Android SDK with API 31+

### Clone and build

```bash
git clone https://github.com/locationjoystick/locationjoystick.git
cd locationjoystick
./gradlew assembleDebug
```

Debug APK at `app/build/outputs/apk/debug/app-debug.apk`.

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Release build

Set the following environment variables before building (or in CI secrets). The build falls back to Google's test-account IDs if unset, which is fine for debug but will not serve real ads.

| Variable | Description |
|---|---|
| `ADMOB_APP_ID` | Your AdMob application ID (e.g. `ca-app-pub-XXXXXXXXXXXXXXXX~NNNNNNNNNN`) |
| `ADMOB_BANNER_ID` | Your AdMob banner ad unit ID (e.g. `ca-app-pub-XXXXXXXXXXXXXXXX/NNNNNNNNNN`) |

```bash
export ADMOB_APP_ID="ca-app-pub-..."
export ADMOB_BANNER_ID="ca-app-pub-.../..."
./gradlew assembleRelease
```

To build a release AAB for manual Play Store upload:

```bash
make bundle
```

AAB at `app/build/outputs/bundle/release/app-release.aab`.

```bash
git tag v1.0.0
git push origin v1.0.0
```

GitHub Actions builds, signs, uploads APK to GitHub Releases on tag push.

---

## Architecture

Multi-module NowInAndroid-style. Each feature = Gradle module. Shared code in `:core:*`.

```
feature/*        — UI + ViewModels (Compose screens, no business logic)
  ↓ depends on
core/data        — Repositories (single source of truth)
  ↓
core/database    — Room DB
core/datastore   — DataStore Prefs

core/location    — Mock GPS engine (ForegroundService), independent of UI
core/model       — Pure Kotlin data classes, no Android deps
```

MVVM + Repository. ViewModels expose `StateFlow`/`SharedFlow`. Compose collects via `collectAsStateWithLifecycle()`. Hilt DI throughout. `LjApp` wraps `LjNavHost` in a `ModalNavigationDrawer`. `IdleScreen` serves as the main hub after onboarding, with cards navigating to Map, Routes, Favorites, and Settings.

### Modules

Each feature split into `:api` (public contract) + `:impl` (implementation).

| Module | Purpose |
|--------|---------|
| `:app` | Entry point, Hilt setup, `LjApp` composable, `LjNavHost`, drawer |
| `:core:common` | Utilities, extensions, constants (`AppConstants`) |
| `:core:data` | Repositories, DataStore preferences |
| `:core:database` | Room DB (v2), DAOs, entities |
| `:core:datastore` | DataStore preferences source |
| `:core:designsystem` | Design tokens, theme, typography, shared components |
| `:core:location` | Mock GPS foreground service + movement engine |
| `:core:model` | Pure Kotlin domain data classes |
| `:core:overlay` | Shared WindowManager overlay utilities |
| `:core:routing` | OSRM client, route interpolation, roaming engine, replay engine |
| `:core:testing` | Shared test utilities, fakes |
| `:feature:favorites:api` / `:impl` | Favorites list, MapPicker, teleport |
| `:feature:joystick:api` / `:impl` | Floating joystick overlay service |
| `:feature:map:api` / `:impl` | MapLibre screen, map interactions, roaming bottom sheet |
| `:feature:onboarding:api` / `:impl` | Multi-step onboarding flow |
| `:feature:routes:api` / `:impl` | Route list, creator, detail, replay |
| `:feature:settings:api` / `:impl` | Speed profiles, widget config, export/import, QR transfer |
| `:feature:widget:api` / `:impl` | Floating widget overlay service, map floating view |

---

## Tech Stack

| Component | Library |
|-----------|---------|
| Language | Kotlin 2.x |
| UI | Jetpack Compose + Material3 |
| Map | MapLibre Android SDK 12.x |
| DI | Hilt (Dagger) |
| Database | Room |
| Preferences | DataStore (Preferences) |
| Routing | OSRM (router.project-osrm.org) |
| Serialization | kotlinx-serialization (JSON) |
| Async | Kotlin Coroutines + Flow |
| Build | Gradle + Version Catalog (`libs.versions.toml`) |
| CI | GitHub Actions |
| Min SDK | API 31 (Android 12) |

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for PR rules, required checks, and reference docs.

---

## License

MIT License. See [LICENSE](LICENSE) for full text.
