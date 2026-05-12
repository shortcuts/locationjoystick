# locationjoystick — Agent Reference

> Primary reference for all AI coding agents. Read before touching any file.

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Architecture](#architecture)
3. [Module Map](#module-map)
4. [Feature Specifications](#feature-specifications)
5. [Domain Models Reference](#domain-models-reference)
6. [Key Services](#key-services)
7. [Permissions Matrix](#permissions-matrix)
8. [Technical Constraints](#technical-constraints)
9. [Code Style Rules](#code-style-rules)
10. [Testing Strategy](#testing-strategy)
11. [Common Patterns](#common-patterns)
12. [Pre-Commit Validation Policy](#pre-commit-validation-policy)

---

## Project Overview

**locationjoystick** — Android-only fake GPS / mock location spoofer. Spoofs location in games like Pokémon GO while phone in pocket. Runs in background, minimal battery, produces updates games accept as real GPS.

| Property | Value |
|---|---|
| Platform | Android only |
| Min SDK | API 31 (Android 12) |
| Package | `com.locationjoystick.app` |
| Language | Kotlin |
| UI | Jetpack Compose |
| Distribution | APK via GitHub Releases (not Play Store) |
| Open source | Yes |

Offline-first. No backend. No accounts. All data on-device in Room + DataStore.

---

## Architecture

Multi-module, NowInAndroid-style. Each feature = Gradle module. Shared code in `:core:*`.

MVVM + Repository pattern. ViewModel → Repository → DataSource (Room / DataStore / LocationManager). ViewModels expose `StateFlow`/`SharedFlow`. Compose UI collects via `collectAsStateWithLifecycle()`.

`LjNavHost` uses nested `navigation {}` graphs for back-isolation: `routes_graph` (Routes + RouteCreator + RouteDetail) and `favorites_graph` (Favorites + MapPicker). Drawer navigation uses `popUpTo(MAP_ROUTE) { saveState = true }` + `launchSingleTop + restoreState`. `FavoritesViewModel` is shared across the favorites graph via `hiltViewModel(navController.getBackStackEntry("favorites_graph"))`.

DI: Hilt throughout. Every ViewModel `@HiltViewModel`. Every repository `@Singleton`.

Reactive streams: Kotlin Flow everywhere. No RxJava. No LiveData.

Coroutines: `viewModelScope` for UI-bound work. `ServiceScope` (tied to service lifecycle) for background. Never `GlobalScope`.

---

## Module Map

```
:app                        — Application entry, Hilt setup, NavGraph
:core:data                  — Repositories, DataStore preferences
:core:database              — Room database, DAOs, entities
:core:model                 — Domain models (pure Kotlin, no Android deps)
:core:network               — OSRM client (Retrofit/Ktor), offline fallback
:core:ui                    — Shared Compose components, theme, typography
:feature:map                — MapLibre screen, map interactions
:feature:routes             — Route list, route editor, route replay
:feature:favorites          — Favorites list, teleport action
:feature:settings           — Speed profiles, widget config, export/import
:feature:joystick           — Floating joystick overlay service
:feature:widget             — Floating widget overlay service
:service:location           — MockLocationService (ForegroundService)
:service:roaming            — RoamingEngine, OSRM path resolver
```

---

## Feature Specifications

### Mock Location Engine

Injects fake GPS into Android location system. All apps (incl. Pokémon GO) receive spoofed coords as real GPS.

Update rate: 1 Hz (every 1000 ms). Matches real GPS cadence. Pokémon GO accepts it.

User must enable "Mock location app" in Developer Options and select locationjoystick. Check at runtime via `AppOpsManager.OPSTR_MOCK_LOCATION`.

Cleanup: on service stop call `locationManager.removeTestProvider`. Failure leaves ghost provider breaking real GPS until reboot.

Edge cases:
- Another app holds mock location slot → `addTestProvider` throws `IllegalArgumentException`. Catch, show clear error.
- `elapsedRealtimeNanos` must be monotonically increasing. Never set fixed value.
- `accuracy` below 1.0f can trigger anti-cheat. Keep 2.0–5.0f.

Key files: `:service:location/MockLocationService.kt`, `:core:data/LocationRepository.kt`

---

### Foreground Service

Persistent notification while spoofing active. App keeps running when minimized or screen off.

Declared with `foregroundServiceType="location"`. Started via `ServiceCompat.startForeground` with `FOREGROUND_SERVICE_TYPE_LOCATION` (required API 34+). Restart behavior: `START_STICKY`. Notification channel: `IMPORTANCE_LOW`, channel ID `"location_spoof_channel"`. Update loop runs as coroutine with `SupervisorJob()` scope. Cleanup: cancel scope + remove test provider in `onDestroy`.

Key files: `:service:location/MockLocationService.kt`

---

### Floating Joystick

Circular overlay on all apps. Drag → moves fake location. Release → stops. Draggable anywhere on screen.

Requires `SYSTEM_ALERT_WINDOW`. Uses `TYPE_APPLICATION_OVERLAY` with `FLAG_NOT_FOCUSABLE` (mandatory — prevents stealing keyboard focus from game) and `FLAG_NOT_TOUCH_MODAL`. Drag-to-reposition via `View.OnTouchListener` updating `WindowManager.LayoutParams`. Input normalized to direction vector, multiplied by speed (m/s), new lat/lon via Haversine. Pushed to `MockLocationService`.

Cleanup critical: must call `windowManager.removeView` in `onDestroy` with null/attached check.

Edge cases:
- Revoking `SYSTEM_ALERT_WINDOW` while overlay showing → `removeView` throws. Wrap in try/catch.
- MIUI/ColorOS: overlay permissions reset on reboot. Show reminder on startup.

Key files: `:feature:joystick/JoystickOverlayService.kt`, `:feature:joystick/JoystickView.kt`

---

### Map (MapLibre)

Main screen shows OpenStreetMap centered on Paris (48.8566, 2.3522) at zoom 12 on first load. Scroll gestures enabled by default. TopAppBar with hamburger icon opens the nav drawer via `onOpenDrawer: () -> Unit` lambda (drawer owned by `LjApp`, not `LjNavHost`). Spoofed location shown as marker. Map follows marker during spoofing.

Library: MapLibre Android SDK 12.x (not osmdroid, not Google Maps). OSM tile source via `RasterSource`. Location marker: `SymbolLayer` backed by GeoJSON — update coords, don't remove/re-add layer. Route polylines: `LineLayer` backed by GeoJSON `FeatureCollection`. Offline tiles via `OfflineManager.downloadRegion()`.

Interactions: long-press → bottom sheet "Walk here / Teleport here". Tap route point → select. Tap empty map in edit mode → add waypoint. Camera follow: disable on `REASON_API_GESTURE`, re-enable via re-center FAB.

Edge cases: forward all lifecycle events to `MapView`. Never call MapLibre APIs before `onMapReady`.

Key files: `:feature:map/MapScreen.kt`, `:feature:map/MapViewModel.kt`

---

### Route System

Create waypoints on map → polyline in order. Two route types:
- **STRAIGHT** (`RouteType.STRAIGHT`): straight-line segments between waypoints, no network required.
- **GUIDED** (`RouteType.GUIDED`): OSRM road-following segments. On OSRM failure, sets `osrmError = true` in `CreatorState` — no silent fallback to straight line.

Save named route to Room. Edit (drag/add/delete waypoints). Delete via swipe or detail screen. Replay at current speed with progress indicator. Optional looping.

Storage: `RouteEntity` + `WaypointEntity` one-to-many. Waypoints store `routeId`, `lat`, `lon`, `orderIndex`. DB version: **2** (migration 1→2 adds `routeType TEXT NOT NULL DEFAULT 'STRAIGHT'`).

Replay: interpolate between waypoints at speed (m/s), compute bearing via `atan2`, advance by `speed * deltaTime`, snap within 1 m of waypoint. Loops: interpolate smoothly from last to first waypoint.

Recording: collect real location at 1 Hz, simplify via Ramer-Douglas-Peucker (epsilon = 5 m), save on stop.

Edge cases: <2 waypoints → disable replay. Resume replay after service restart (persist waypoint index in DataStore).

Key files: `:feature:routes/RouteListScreen.kt`, `:feature:routes/RouteEditorScreen.kt`, `:feature:routes/RouteViewModel.kt`, `:core:database/RouteDao.kt`, `:service:location/RouteReplayEngine.kt`

---

### Favorite Locations

Save named locations. Tap from list → instantly teleport spoofed position. Rename and delete supported.

Two add flows: **+ coordinates** (inline dialog with name/lat/lon fields) and **+ map** (navigates to `MapPickerScreen` where user taps map or uses Nominatim search, enters a name, then confirms). MapPickerScreen calls back with `(name, lat, lon)`.

Storage: `FavoriteEntity` flat table (no relations). Teleport: set position directly, push one update, camera jumps. Sort by `createdAt` desc default; optional alpha sort.

Key files: `:feature:favorites/FavoritesScreen.kt`, `:feature:favorites/FavoritesViewModel.kt`, `:core:database/FavoriteDao.kt`

---

### Speed Profiles

Three presets: Walk (0.556 m/s / 2 km/h), Run (2.778 m/s / 10 km/h), Bike (5.556 m/s / 20 km/h). All user-editable. Applies to joystick, route replay, roaming.

Stored in DataStore. UI: three chips or segmented button in widget + Settings. Change takes effect immediately on next tick.

Edge cases: clamp 0.1–15.0 m/s. Warn inline below speed input when >8 m/s (anti-cheat risk). Warning uses `MaterialTheme.colorScheme.error`, generic language — no game names.

Key files: `:feature:settings/SpeedSettingsScreen.kt`, `:core:data/SpeedProfileRepository.kt`

---

### Floating Widget

Small floating button overlay. Tap → expand panel with configured quick-access controls. Items configured in Settings.

Same overlay mechanism as joystick. Separate service, toggled independently. State: collapsed (FAB) / expanded (panel) via `ValueAnimator`. Items stored in DataStore as `stringSetPreferencesKey`. Binds to `MockLocationService` in `onStartCommand`, unbinds in `onDestroy`.

Edge cases: no items configured → show placeholder. Clamp panel to screen bounds. Re-clamp on `onConfigurationChanged`.

Key files: `:feature:widget/FloatingWidgetService.kt`, `:feature:widget/WidgetPanel.kt`, `:feature:settings/WidgetConfigScreen.kt`

---

### Click-to-Move / Teleport

Tap map → bottom sheet with "Walk here" or "Teleport here".

Walk here: bearing computed from current to target, advance at `currentSpeed` m/s per tick, snap within 1 m. Teleport: set position directly, one update push.

Edge cases: new walk-here cancels previous. Walk-here while route replaying → ask confirmation to stop replay.

Key files: `:feature:map/MapViewModel.kt`

---

### Roaming Mode

Set center, radius, duration. Walks randomly within radius. Two modes: simple (straight-line, no network) and road-following (OSRM routes, opt-in). OSRM failure → fall back to straight-line automatically.

Algorithm (road-following): pick random destination in radius (uniform disk distribution) → fetch OSRM route → walk route → repeat until duration elapsed.

OSRM endpoint: `https://router.project-osrm.org/route/v1/{profile}/{lon1},{lat1};{lon2},{lat2}?overview=full&geometries=geojson`. Profiles: `foot`, `cycling`.

Edge cases: cache last OSRM route (don't re-fetch while walking it). Radius/duration changes apply on next waypoint pick. Persist start time in DataStore (survives restarts).

Key files: `:service:roaming/RoamingEngine.kt`, `:core:network/OsrmClient.kt`, `:feature:settings/RoamingConfigScreen.kt`

---

### Export / Import

Settings → export all data to JSON, import from previous export. Schema version: **1**.

Covers: routes, favorites, speed profiles, widget config, roaming defaults.

Export: serialize via `kotlinx.serialization` → write to `getExternalFilesDir(null)` → share via `FileProvider + Intent.ACTION_SEND`.

Import: file picker (`OpenDocument`, MIME `application/json`) → parse + validate `schemaVersion == 1` → confirm "replace all data?" → clear Room + DataStore → insert. All I/O on `Dispatchers.IO`.

Edge cases: malformed JSON → show "Invalid file". Missing fields → use `@SerialName` defaults. Skip confirmation on fresh install (empty DB).

Key files: `:feature:settings/ExportImportScreen.kt`, `:core:data/ExportRepository.kt`

---

### Setup / Onboarding

First launch → multi-step onboarding. Track completion via `ONBOARDING_COMPLETE` DataStore key.

Steps:
1. Welcome
2. Grant `ACCESS_FINE_LOCATION`
3. Grant `SYSTEM_ALERT_WINDOW`
4. Enable mock location (deep link to Developer Options, "Check again" button re-checks `AppOpsManager`)
5. Done → MapScreen

Permission checks: `ContextCompat.checkSelfPermission`, `Settings.canDrawOverlays(context)`, `AppOpsManager.checkOpNoThrow(OPSTR_MOCK_LOCATION)`.

Edge cases: allow skipping each permission (show banner for missing). Detect revoked permissions on `onResume`.

Key files: `:feature:onboarding/OnboardingScreen.kt`, `:feature:onboarding/OnboardingViewModel.kt`

---

## Domain Models Reference

All in `:core:model`. Pure Kotlin — no Android imports, no Room annotations. Room entities in `:core:database` mirror these and map via extension functions.

| Model | Fields |
|-------|--------|
| `LatLon` | `lat: Double`, `lon: Double` |
| `Route` | `id`, `name`, `waypoints: List<LatLon>`, `isLooping: Boolean`, `routeType: RouteType`, `createdAt: Long`, `updatedAt: Long` |
| `FavoriteLocation` | `id`, `name`, `lat`, `lon`, `createdAt: Instant` |
| `RouteType` | enum: `STRAIGHT`, `GUIDED` |
| `SpeedProfileType` | enum: `WALK`, `RUN`, `BIKE` |
| `SpeedProfile` | `type: SpeedProfileType`, `speedMs: Double` |
| `RoamingConfig` | `centerLat`, `centerLon`, `radiusMeters`, `durationMinutes`, `roadFollowing` |
| `ExportBundle` | `schemaVersion`, `exportedAt`, `speedProfiles`, `activeProfile`, `routes`, `favorites`, `widgetItems`, `roamingDefaults` |

---

## Key Services

| Service | Module | Type | Purpose |
|---------|--------|------|---------|
| `MockLocationService` | `:service:location` | ForegroundService | Owns `LocationManager` test provider. Exposes `StateFlow<SpoofState>`. Commands: `startSpoofing`, `updatePosition`, `stopSpoofing`. |
| `JoystickOverlayService` | `:feature:joystick` | Service | Manages `WindowManager` overlay. Reads joystick input → `LocationRepository.updatePosition()`. |
| `FloatingWidgetService` | `:feature:widget` | Service | Manages widget overlay. Binds to `MockLocationService`. |
| `RoamingEngine` | `:service:roaming` | Class (not service) | Instantiated by `MockLocationService`. Owns OSRM client + random waypoint picker. Runs on service scope. |

---

## Permissions Matrix

| Permission | Type | When Required | Manifest |
|---|---|---|---|
| `ACCESS_FINE_LOCATION` | Dangerous | Map centering, route recording | Yes |
| `ACCESS_COARSE_LOCATION` | Dangerous | Fallback if fine denied | Yes |
| `SYSTEM_ALERT_WINDOW` | Special (AppOps) | Joystick + widget overlays | Yes |
| `FOREGROUND_SERVICE` | Normal | Running `MockLocationService` | Yes |
| `FOREGROUND_SERVICE_LOCATION` | Normal (API 34+) | `foregroundServiceType="location"` | Yes (API 34+) |
| `ACCESS_MOCK_LOCATION` | Dev Options only | Registering as mock location provider | Yes (with `tools:ignore="MockLocation"` — this IS a mock location app, not a test-only use) |

Notes:
- `SYSTEM_ALERT_WINDOW` not granted via `requestPermissions` — requires `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`.
- API 34+: `FOREGROUND_SERVICE_LOCATION` must be in manifest or `startForeground` crashes.

---

## Technical Constraints

- Min SDK API 31. Use `ProviderProperties.Builder` (API 31+). Don't use deprecated raw-int overload.
- No Play Services: MapLibre not Google Maps. No Firebase.
- Offline-first: core features work without internet. OSRM opt-in, degrades gracefully.
- No `Thread.sleep()` — use `delay()` in coroutines.
- No empty catch blocks — every `catch` must log or handle.
- No `GlobalScope` — use `viewModelScope`, `lifecycleScope`, or scoped `CoroutineScope`.
- No memory leaks: every `WindowManager.addView` → matching `removeView` in `onDestroy`. Every scope cancelled in `onDestroy`/`onCleared`.
- 1 Hz location updates exactly.
- Battery: `IMPORTANCE_LOW` channel. No wake locks unless absolutely necessary.

---

## Code Style Rules

### General
- Kotlin only. No Java.
- Package declaration in every file matching module structure.
- No wildcard imports.
- Max line length: 120 chars.

### Compose
- State hoisting: ViewModels hold state, Composables receive as params.
- `collectAsStateWithLifecycle()` (not `collectAsState()`).
- No business logic in Composables.
- `@Preview` for every non-trivial Composable.
- `remember { }` for expensive computations, `rememberSaveable { }` for state surviving process death.

### Coroutines
- `viewModelScope.launch { }` for ViewModel coroutines.
- `Dispatchers.IO` for DB and network.
- `Dispatchers.Default` for CPU-intensive work (path interpolation, RDP simplification).
- `Dispatchers.Main` only for UI updates when not already on main.
- Never `runBlocking` on main thread.
- Always `SupervisorJob()` in service scopes.

### Repository Pattern
- Repositories = single source of truth. ViewModels never touch DAOs or DataStore directly.
- `Flow<T>` for observable data, `Result<T>` for one-shot operations.
- Map Room entities → domain models inside repository, not ViewModel.

### Error Handling
- `Result<T>` for operations that can fail.
- Catch at repository boundary. Domain models and ViewModels stay exception-free.
- `Log.e(TAG, "message", e)` on every caught exception. Never swallow silently.

### Naming
- ViewModels: `FeatureViewModel`
- Screens: `FeatureScreen`
- DAOs: `EntityDao`
- Repositories: `FeatureRepository`
- Services: descriptive + `Service` suffix

---

## Testing Strategy

### Unit Tests (`:core:*`)
- Repository logic with fake DAO (in-memory Room)
- Route replay interpolation: waypoints A+B → assert position after N ticks
- RDP simplification: known path → assert simplified output
- Bearing calculation: known lat/lon pairs → expected bearing
- `randomPointInRadius`: output always within specified radius
- Export/import serialization: round-trip full `ExportBundle` through JSON

### Integration Tests (`:feature:*`)
- Hilt testing with `@HiltAndroidTest`
- Full route save → list → replay with in-memory Room
- Favorites: add → list → teleport → delete

### UI Tests (Compose)
- `ComposeTestRule` for screen-level tests
- Onboarding flow: mock permission states, assert correct screen transitions
- Route editor: add waypoints, assert polyline updates

### What NOT to test
- MapLibre rendering (GPU, not unit-testable)
- `WindowManager` overlay behavior (requires real device)
- `LocationManager.addTestProvider` (requires real device with Developer Options)

---

## Common Patterns

- **Bound service**: `LocalBinder` inner class in service, `ServiceConnection` in client. Unbind in `onDestroy`/`onCleared`.
- **DataStore read**: `dataStore.data.map { prefs -> prefs[KEY] ?: default }`
- **DataStore write**: `dataStore.edit { prefs -> prefs[KEY] = value }` in suspend fun
- **Room one-to-many**: `@Transaction @Query` returning `Flow<EntityWithChildren>`
- **Haversine**: use for distance between two `LatLon` points (R = 6371000.0 m)
- **Bearing**: `atan2(sin(dLon)*cos(lat2), cos(lat1)*sin(lat2) - sin(lat1)*cos(lat2)*cos(dLon))`
- **Advance position**: spherical law of cosines from `LatLon` + bearing + distance

---

## Pre-Commit Validation Policy

Work is NOT complete until lint passes.

```bash
make format
make lint
```

Rules:
- Fix every lint error before declaring done. Warnings acceptable; errors not.
- Run both after every set of edits, not just end of session.
- If check fails, fix root cause. Don't suppress unless genuine false positive + inline comment explaining why.
- Never suppress `Errors` category rules. Never batch-suppress with `@file:Suppress`.

---

*Last updated: May 2026. Export/import schemaVersion: 1.*

## Code Exploration Policy

Always use jCodemunch-MCP tools for code navigation. Never fall back to Read, Grep, Glob, or Bash for code exploration.
**Exception:** Use `Read` when editing — harness requires `Read` before `Edit`/`Write` succeeds. Use jCodemunch tools to find/understand code, then `Read` only file you're about to modify.

**Start any session:**
1. `resolve_repo { "path": "." }` — confirm project indexed. If not: `index_folder { "path": "." }`
2. `suggest_queries` — when repo unfamiliar

**Finding code:**
- symbol by name → `search_symbols` (add `kind=`, `language=`, `file_pattern=`, `decorator=` to narrow)
- decorator-aware queries → `search_symbols(decorator="X")` to find symbols with specific decorator (e.g. `@property`, `@route`); combine with set-difference to find symbols *lacking* decorator
- string, comment, config value → `search_text` (supports regex, `context_lines`)
- database columns (dbt/SQLMesh) → `search_columns`

**Reading code:**
- before opening any file → `get_file_outline` first
- one or more symbols → `get_symbol_source` (single ID → flat object; array → batch)
- symbol + imports → `get_context_bundle`
- specific line range only → `get_file_content` (last resort)

**Repo structure:**
- `get_repo_outline` → dirs, languages, symbol counts
- `get_file_tree` → file layout, filter with `path_prefix`

**Relationships & impact:**
- what imports this file → `find_importers`
- where name used → `find_references`
- identifier used anywhere → `check_references`
- file dependency graph → `get_dependency_graph`
- what breaks if change X → `get_blast_radius`
- symbols changed since last commit → `get_changed_symbols`
- dead code → `find_dead_code`
- class hierarchy → `get_class_hierarchy`

## Session-Aware Routing

**Opening move:**
1. `plan_turn { "repo": "...", "query": "your task description", "model": "<your-model-id>" }` — get confidence + recommended files; `model` narrows exposed tool list at zero extra requests.
2. Obey confidence level:
   - `high` → go directly to recommended symbols, max 2 supplementary reads
   - `medium` → explore recommended files, max 5 supplementary reads
   - `low` → feature likely doesn't exist. Report gap. Do NOT search further.

**Interpreting search results:**
- `search_symbols` returns `negative_evidence` with `verdict: "no_implementation_found"`:
  - Do NOT re-search with different terms
  - Do NOT assume related file implements missing feature
  - DO report: "No existing implementation found for X. This would need to be created."
  - DO check `related_existing` files
- `verdict: "low_confidence_matches"`: examine matches critically before assuming they implement feature

**After editing files:**
- PostToolUse hooks installed (Claude Code only): edited files auto-reindexed
- Otherwise: call `register_edit` with edited file paths to invalidate caches
- Bulk edits (5+ files): always use `register_edit` with all paths

**Token efficiency:**
- `_meta` contains `budget_warning`: stop exploring, work with what you have
- `auto_compacted: true`: results auto-compressed due to turn budget
- Use `get_session_context` to check what you've already read — avoid re-reading

## Model-Driven Tool Tiering

jcodemunch-mcp narrows exposed tool list based on model. Always include `model="<your-model-id>"` in opening `plan_turn`.

Replace `<your-model-id>` with active model:
- Claude Opus variants → `claude-opus-4-7` (or any `claude-opus-*`)
- Claude Sonnet variants → `claude-sonnet-4-6`
- Claude Haiku variants → `claude-haiku-4-5`
- GPT-4o / GPT-5 / o1 / Llama → use model id as printed by runner

`model=` rides on existing `plan_turn` call — does **not** add separate invocation. If `plan_turn` not appropriate for non-code task, call `announce_model(model="...")` once instead.


<!-- code-review-graph MCP tools -->
## MCP Tools: code-review-graph

**IMPORTANT: Project has knowledge graph. ALWAYS use code-review-graph MCP tools BEFORE Grep/Glob/Read.** Graph faster, cheaper (fewer tokens), gives structural context (callers, dependents, test coverage) file scanning can't.

### When to use graph tools FIRST

- **Exploring code**: `semantic_search_nodes` or `query_graph` instead of Grep
- **Understanding impact**: `get_impact_radius` instead of manually tracing imports
- **Code review**: `detect_changes` + `get_review_context` instead of reading entire files
- **Finding relationships**: `query_graph` with callers_of/callees_of/imports_of/tests_for
- **Architecture questions**: `get_architecture_overview` + `list_communities`

Fall back to Grep/Glob/Read **only** when graph doesn't cover what you need.

### Key Tools

| Tool | Use when |
|------|----------|
| `detect_changes` | Reviewing code changes — gives risk-scored analysis |
| `get_review_context` | Need source snippets for review — token-efficient |
| `get_impact_radius` | Understanding blast radius of a change |
| `get_affected_flows` | Finding which execution paths are impacted |
| `query_graph` | Tracing callers, callees, imports, tests, dependencies |
| `semantic_search_nodes` | Finding functions/classes by name or keyword |
| `get_architecture_overview` | Understanding high-level codebase structure |
| `refactor_tool` | Planning renames, finding dead code |

### Workflow

1. Graph auto-updates on file changes (via hooks).
2. Use `detect_changes` for code review.
3. Use `get_affected_flows` to understand impact.
4. Use `query_graph` pattern="tests_for" to check coverage.
