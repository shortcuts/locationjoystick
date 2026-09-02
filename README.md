# locationjoystick

![Build](https://img.shields.io/github/actions/workflow/status/shortcuts/locationjoystick/main.yml?label=Build&style=flat-square)
![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)
![minSdk](https://img.shields.io/badge/minSdk-28%20(Android%209)-green?style=flat-square)
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
| **Map** | OpenStreetMap via MapLibre (GPU-accelerated, offline-capable). Tap to walk or teleport. Spoofed position shown as live marker. Optional translucent circle shows the current position-jitter radius when Debug stats is enabled. |
| **Last Position** | Restores last spoofed location on app restart. No manual re-entry needed. |
| **Joystick** | Floating overlay stays on top of any app. Drag to move in any direction at chosen speed. Draggable anywhere on screen. |
| **Speed Profiles** | Slow Walk / Walk / Run / Bike / Drive presets, all user-editable. Anti-cheat warning when speed exceeds threshold. Accessible from floating widget. |
| **Routes** | Create waypoints on map → polyline. Two types: **straight** (direct segments) and **guided** (OSRM road-following). Save, edit, replay, loop, or record in real time. Import from GPX files. |
| **Roaming** | Set center, radius, duration. Auto-walks randomly within radius. Optional road-following via OSRM. Optional return-to-start after loop completes. Configured via bottom sheet on Map screen. |
| **Favorites** | Save named map positions. Instantly teleport or walk to any. Add via inline dialog or MapPicker with Nominatim search. Optional curated list of 48 popular locations (Settings → Favorites → Show hot locations). |
| **Floating Widget** | Configurable quick-access panel floats over other apps. Collapsible FAB → expanded panel with user-selected controls. |
| **Click-to-Move** | Long-press map → "Walk here" or "Teleport here". Walk advances at current speed; teleport jumps instantly. |
| **QR Transfer** | Share or import config between devices on the same Wi-Fi network by scanning a single QR code. |
| **GPS Realism** | Makes spoofed GPS indistinguishable from a real chip. Toggle per-feature: bearing hold when stationary, realistic altitude drift (user-configurable magnitude), warm-up accuracy envelope (converges over 30 s), satellite count in fix (7–14), and natural signal dropouts (auto-paused during route replay and walk-to). Bearing hold, altitude drift, and satellite count are on by default; warm-up envelope and signal dropouts are opt-in. Altitude anchors to the real ground elevation at the spoofed position (looked up automatically, on by default) unless manually overridden from the floating widget (off by default). |
| **Import/Export** | All data to/from JSON (routes, favorites, speed profiles, widget config, roaming defaults, jitter settings). Route import also supports GPX, GPS Joystick, and YAMLA formats. A "Reset all data" button clears favorites, routes, and settings in one tap, without an OS-level app data clear or re-onboarding. |
| **Background Service** | Spoofs while minimized or screen off via foreground service. Low-priority notification, with an optional setting to hide its status bar icon (Android still requires the notification to exist). |
| **Onboarding** | Multi-step first-run flow: location permission, overlay permission, mock location enablement. |
| **Group Sync** | Sync spoofed location across multiple devices on the same Wi-Fi network. No account needed. One device is the leader (shares position via QR-joined session); others are followers (mirror leader's location). |
| **Tap to Walk** | Two quick-walk shortcuts. Floating map quick-walk: tap the floating map to walk there without a confirmation sheet. Screen overlay: a crosshair button in the widget panel activates a transparent full-screen overlay — tap any point in a game or map app to walk there. Configurable meters-per-pixel scale. |
| **Deep Links & Location Sharing** | Share any coordinate or saved favorite as a link. Anyone who taps it on Android with the app installed lands directly on that spot with a confirm sheet (teleport / walk / walk via roads). Also registers as a handler for Google Maps and `geo:` links from other apps. |
| **Theme** | Light and dark color themes for readability across lighting conditions. Toggle in Settings → Appearance. |
| **Hide Teleport Features** | Optional toggle (off by default) that hides every teleport button/checkbox app-wide — map, favorites, routes, group sync, and widget — leaving only walking and route replay. |

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

Enable Developer Options, pick locationjoystick as your mock location app, grant the overlay permission, then start spoofing. Full walkthrough with screenshots: **[Getting Started](https://shortcuts.github.io/locationjoystick/)**.

> **Note:** Some apps detect mock locations. Check the app's community for current workarounds. Root is not required for any feature.

---

## Test Coverage

```bash
make test            # unit tests (JVM)
make smoke-test      # end-to-end navigation suite (requires connected device/emulator)
make coverage        # generate HTML + XML reports (kotlinx-kover)
```

See [docs/testing.md](docs/testing.md) for the full testing strategy and report locations.

---

## Building

### Prerequisites

- Android Studio Hedgehog or newer (or JDK + Android SDK command-line tools)
- Java 17
- Android SDK with API 28+

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

Releases are automated via [release-please](https://github.com/googleapis/release-please): merging to `main` opens/updates a release PR from Conventional Commits; merging that PR tags the version and triggers CI to build, sign, and upload the APK to GitHub Releases. No manual tagging needed.

---

## Architecture

Multi-module NowInAndroid-style: MVVM + Repository, each feature its own Gradle module (`:api` + `:impl`), shared code in `:core:*`, Hilt DI throughout.

Full module table and dependency flow: [docs/architecture.md](docs/architecture.md). Agent-facing reference (feature specs, domain models, constants, services): [AGENTS.md](AGENTS.md).

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
| Routing | OSRM (FOSSGIS primary, project-osrm.org demo server as fallback) |
| Serialization | kotlinx-serialization (JSON) |
| Async | Kotlin Coroutines + Flow |
| Build | Gradle + Version Catalog (`libs.versions.toml`) |
| CI | GitHub Actions |
| Min SDK | API 28 (Android 9) |

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for PR rules, required checks, and reference docs.

---

## License

MIT License. See [LICENSE](LICENSE) for full text.
