# locationjoystick

![Build](https://img.shields.io/github/actions/workflow/status/locationjoystick/locationjoystick/release.yml?label=Build&style=flat-square)
![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)
![minSdk](https://img.shields.io/badge/minSdk-31%20(Android%2012)-green?style=flat-square)
![Kotlin](https://img.shields.io/badge/Kotlin-2.x-purple?style=flat-square)

No-root mock location app for Android. Spoof GPS anywhere using floating joystick, saved routes, and OSM-powered roaming — without touching system partition.

---

## What is locationjoystick?

GPS spoofing app built on Android's official mock location API. No root, no Xposed, no system mods. Enable Developer Options, pick locationjoystick as mock location provider → device believes it's wherever you say.

Primary use case: location-based games like Pokémon GO. Walk saved routes, roam a neighborhood automatically, or nudge position with floating joystick while game runs in foreground. App keeps spoofing in background.

Also useful for: privacy (mask real location), QA testing (simulate movement at desk), development (test geofences, location triggers, map features).

---

## Features

- **Map**: OpenStreetMap via MapLibre (GPU-accelerated, offline-capable). Tap to walk or teleport. Spoofed position as live marker.
- **Joystick**: Floating overlay, stays on top of any app. Move any direction at chosen speed. Draggable. Persists while minimized.
- **Speeds**: Walk / Run / Bike presets. Each user-editable (m/s). Accessible from floating widget.
- **Routes**: Create waypoints on map. Save/rename/edit/delete. Replay, loop, or record in real time.
- **Roaming**: Set radius + duration. Auto-walks randomly. Optional road-following via OSRM.
- **Favorites**: Save named map positions. Instantly teleport to any.
- **Floating Widget**: Configurable quick-access panel floats over other apps. Collapsible.
- **Background**: Spoofs while minimized or screen off via foreground service. Low-priority notification.
- **Import/Export**: All settings to/from JSON (routes, favorites, speed presets, widget config).

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

MVVM + Repository. ViewModels expose `StateFlow`/`SharedFlow`. Compose collects via `collectAsStateWithLifecycle()`. Hilt DI throughout. Single `NavHost` in `MainActivity`.

### Modules

| Module | Purpose |
|--------|---------|
| `:app` | Entry point, Hilt setup, NavGraph |
| `:core:model` | Domain data classes |
| `:core:data` | Repositories, DataStore preferences |
| `:core:database` | Room DB, DAOs, entities |
| `:core:datastore` | DataStore preferences source |
| `:core:location` | Mock GPS foreground service + movement engine |
| `:core:routing` | OSRM client + route interpolation |
| `:core:ui` | Shared Compose components + theme |
| `:core:common` | Utilities, extensions, constants |
| `:feature:map` | Main map screen |
| `:feature:joystick` | Floating joystick overlay |
| `:feature:routes` | Route list, editor, recorder |
| `:feature:favorites` | Favorites list + teleport |
| `:feature:roaming` | Roaming config + control |
| `:feature:widget` | Floating widget overlay |
| `:feature:settings` | Speed presets, import/export, permissions |

---

## Tech Stack

| Component | Library |
|-----------|---------|
| Language | Kotlin 2.x |
| UI | Jetpack Compose + Material3 |
| Map | MapLibre Android SDK 12.x |
| DI | Hilt (Dagger) |
| Database | Room |
| Preferences | DataStore (Proto) |
| Routing | OSRM (router.project-osrm.org) |
| Serialization | kotlinx-serialization (JSON) |
| Async | Kotlin Coroutines + Flow |
| Build | Gradle + Version Catalog (`libs.versions.toml`) |
| CI | GitHub Actions |
| Min SDK | API 31 (Android 12) |

---

## Contributing

PRs welcome. Before opening one:

1. Read [AGENTS.md](AGENTS.md) — code standards, module conventions, architecture rules
2. One feature or fix per PR
3. `./gradlew lint && ./gradlew assembleDebug` must pass before submitting

Adding new feature → open issue first.

---

## License

MIT License. See [LICENSE](LICENSE) for full text.
