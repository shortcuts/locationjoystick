# locationjoystick

![Build](https://img.shields.io/github/actions/workflow/status/shortcuts/locationjoystick/release.yml?label=Build&style=flat-square)
![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)
![minSdk](https://img.shields.io/badge/minSdk-31%20(Android%2012)-green?style=flat-square)
![Kotlin](https://img.shields.io/badge/Kotlin-2.x-purple?style=flat-square)

Spoof your GPS location on Android. Point your phone anywhere on the map using a floating joystick, saved routes, or automatic roaming while your other apps keep running normally.

## Why locationjoystick?

- **Free and open-source** — no subscriptions, no premium tiers, no paywalled features, no ads
- **No root required** — works out of the box via Android's built-in Developer Options mock location setting
- **Runs in the background** — joystick, widget, and routes all stay active while other apps run in the foreground
- **Import in seconds** — bring your saved routes from GPS Joystick or YAMLA without starting from scratch

## What can it do?

Here's everything included:

| Feature | Description |
|---------|-------------|
| **Map** | OpenStreetMap via MapLibre (GPU-accelerated, offline-capable). Tap to walk or teleport. Spoofed position shown as live marker. |
| **Last Position** | Restores last spoofed location on app restart. No manual re-entry needed. |
| **Joystick** | Floating overlay stays on top of any app. Drag to move in any direction at chosen speed. Draggable anywhere on screen. |
| **Speed Profiles** | Walk / Run / Bike presets, all user-editable. Anti-cheat warning when speed exceeds threshold. Accessible from floating widget. |
| **Routes** | Create waypoints on map → polyline. Two types: **straight** (direct segments) and **guided** (OSRM road-following). Save, edit, replay, loop, or record in real time. Import from GPX files. |
| **Roaming** | Set center, radius, duration. Auto-walks randomly within radius. Optional road-following via OSRM. Optional return-to-start after loop completes. Configured via bottom sheet on Map screen. |
| **Favorites** | Save named map positions. Instantly teleport or walk to any. Add via inline dialog or MapPicker with Nominatim search. Optional curated list of 26 popular locations (Settings → Favorites → Show hot locations). |
| **Floating Widget** | Configurable quick-access panel floats over other apps. Collapsible FAB → expanded panel with user-selected controls. |
| **Click-to-Move** | Long-press map → "Walk here" or "Teleport here". Walk advances at current speed; teleport jumps instantly. |
| **QR Transfer** | Share or import config between devices on the same Wi-Fi network by scanning a single QR code. |
| **GPS Realism** | Makes spoofed GPS indistinguishable from a real chip. Toggle per-feature: bearing hold when stationary, realistic altitude drift, warm-up accuracy envelope (converges over 30 s), satellite count in fix (7–14), and natural signal dropouts (auto-paused during route replay and walk-to). All off by default; enable selectively in Settings. |
| **Import/Export** | All data to/from JSON (routes, favorites, speed profiles, widget config, roaming defaults, jitter settings). Route import also supports GPX, GPS Joystick, and YAMLA formats. |
| **Background Service** | Spoofs while minimized or screen off via foreground service. Low-priority notification. |
| **Onboarding** | Multi-step first-run flow: location permission, overlay permission, mock location enablement. |
| **Group Sync** | Sync spoofed location across multiple devices on the same Wi-Fi network. No account needed. One device is the leader (shares position via QR-joined session); others are followers (mirror leader's location). |
| **Tap to Walk** | Two quick-walk shortcuts. Floating map quick-walk: tap the floating map to walk there without a confirmation sheet. Screen overlay: a crosshair button in the widget panel activates a transparent full-screen overlay — tap any point in a game or map app to walk there. Configurable meters-per-pixel scale. |

---

## Download

Pre-built APKs on [Releases page](https://github.com/shortcuts/locationjoystick/releases).

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

> **Note:** Some apps detect mock locations. Check the app's community for current workarounds. Root is not required for any core feature — the Elevation Controls widget (experimental) is the only feature that optionally uses root, and it can be ignored entirely.

---

## Test Coverage

Coverage tracked via [kotlinx-kover](https://github.com/Kotlin/kotlinx-kover). Aggregates all modules into a single report.

```bash
./gradlew koverHtmlReport   # HTML report → build/reports/kover/html/index.html
./gradlew koverXmlReport    # XML report for CI → build/reports/kover/report.xml
```

Or via Make:

```bash
make test            # unit tests (JVM)
make smoke-test      # end-to-end navigation suite (requires connected device/emulator)
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
git clone https://github.com/shortcuts/locationjoystick.git
cd locationjoystick
./gradlew assembleDebug
```

Debug APK at `app/build/outputs/apk/debug/app-debug.apk`.

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Release build

```bash
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
| `:core:map` | GeoJSON utils, MapLibre lifecycle bridge, style extensions |
| `:core:overlay` | Shared WindowManager overlay utilities |
| `:core:routing` | OSRM client, route interpolation, roaming engine, replay engine |
| `:core:testing` | Shared test utilities, fakes |
| `:feature:favorites:api` / `:impl` | Favorites list, MapPicker, teleport |
| `:feature:group:api` / `:impl` | Group Sync screen — leader/follower Wi-Fi location sync |
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
