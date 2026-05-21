# locationjoystick — Agent Reference

> Primary reference for AI coding agents. Read before touching any file.

---

## Project

Android-only mock GPS app. Background operation, minimal battery.

| Field | Value |
|---|---|
| Package | `com.locationjoystick.app` |
| Language | Kotlin |
| UI | Jetpack Compose |
| Min SDK | API 31 |
| Distribution | GitHub Releases APK + Play Store (AAB) |
| Storage | Room + DataStore |
| Backend | None |
| Open source | Yes |

Constraints:

- Offline-first
- No accounts
- All data on-device in Room + DataStore

---

## Constants

All app-wide constants live in a single source of truth: `:core:common/constants/AppConstants.kt`.
Nested objects: `LocationConstants`, `ProfileConstants`, `JitterConstants`, `RoamingConstants`, `OsrmConstants`, `MapConstants`, `NominatimConstants`, `ExportConstants`, `NotificationConstants`, `ServiceConstants`, `DataStoreConstants`, `JoystickConstants`, `WidgetConstants`, `RouteConstants`, `DatabaseConstants`, `AppInfo`.

Rules:
- Never define a new top-level or companion-object constant that belongs in `AppConstants`. Add it there instead.
- Modules that need constants must declare `implementation(project(":core:common"))` in their `build.gradle.kts`.
- Exception: `:core:model` is a pure JVM library and cannot depend on `core:common`. Constants used only within `core:model` stay in that module.
---

## Pre-Commit Validation Policy

Work is NOT complete until lint and test passes.

```bash
make format
make lint
make test
```

To verify the AAB builds locally (manual Play Store upload — no automated CI deployment):

```bash
make bundle
```

Rules:
- Fix every lint error before declaring done. Warnings acceptable; errors not.
- Run after every set of edits, not just end of session.
- If check fails, fix root cause. Don't suppress unless genuine false positive + inline comment explaining why.
- Never suppress `Errors` category rules. Never batch-suppress with `@file:Suppress`.

---

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


---

## Architecture

Multi-module, NowInAndroid-style. Each feature = two Gradle modules (`api` + `impl`). Shared code in `:core:*`.

MVVM + Repository pattern. ViewModel → Repository → DataSource (Room / DataStore / LocationManager). ViewModels expose `StateFlow`/`SharedFlow`. Compose UI collects via `collectAsStateWithLifecycle()`.

`LjApp` wraps `LjNavHost` in a `ModalNavigationDrawer`. `IdleScreen` serves as the main hub after onboarding, with cards navigating to Map, Routes, Favorites, and Settings. `LjNavHost` uses nested `navigation {}` graphs for back-isolation: `routes_graph` (Routes + RouteCreator + RouteDetail) and `favorites_graph` (Favorites + MapPicker). Drawer navigation uses `popUpTo(IDLE_ROUTE) { saveState = true }` + `launchSingleTop + restoreState`. `FavoritesViewModel` shared across favorites graph via `hiltViewModel(navController.getBackStackEntry("favorites_graph"))`.

DI: Hilt throughout. Every ViewModel `@HiltViewModel`. Every repository `@Singleton`.

Reactive streams: Kotlin Flow everywhere. No RxJava. No LiveData.

Coroutines: `viewModelScope` for UI-bound work. `ServiceScope` (tied to service lifecycle) for background. Never `GlobalScope`.

---

## Feature Specifications

### Mock Location Engine

Injects fake GPS into Android location system. All apps receive spoofed coords as real GPS.

Update rate: `AppConstants.LocationConstants.UPDATE_INTERVAL_MS`. Matches real GPS cadence.

Cleanup: on service stop call `locationManager.removeTestProvider`. Failure leaves ghost provider breaking real GPS until reboot.

Edge cases:
- Another app holds mock location slot → `addTestProvider` throws `IllegalArgumentException`. Catch, show clear error.
- `elapsedRealtimeNanos` must be monotonically increasing. Never set fixed value.
- `accuracy` below 1.0f can trigger anti-cheat. Keep within `AppConstants.JitterConstants.ACCURACY_MIN`–`AppConstants.JitterConstants.ACCURACY_MAX`.

Key files: `:core:location/MockLocationService.kt`, `:core:data/LocationRepository.kt`

#### GPS Realism

Five independent toggles stored in `AppSettings` (persisted via DataStore). Each defaults to off except `bearingHoldOnIdle` and `satelliteExtrasEnabled` which default to `true`.

| Setting | `AppSettings` field | Behaviour |
|---|---|---|
| Bearing hold | `bearingHoldOnIdle` | When `speedMs == 0`, reports `lastNonZeroBearing` instead of 0° (avoids resetting compass to north). |
| Altitude drift | `altitudeEnabled` | Gaussian random walk (σ = `RealismConstants.ALTITUDE_SIGMA_METERS`, drift = `ALTITUDE_DRIFT_PER_SECOND_METERS`) clamped within `±ALTITUDE_CLAMP_RADIUS_METERS` of `DEFAULT_ALTITUDE_METERS`. |
| Warm-up envelope | `warmupEnabled` | Accuracy degrades at session start and converges to nominal over `RealismConstants.WARMUP_DURATION_MS` (≈ 30 s). `warmupStartMs` is set once in `startSpoofing` and never reset on pause/resume. |
| Satellite extras | `satelliteExtrasEnabled` | Attaches `Bundle` extras with slow-churning total + in-fix satellite counts. Counts are refreshed every `RealismConstants.SATELLITE_UPDATE_INTERVAL_MS`. |
| Suspended mocking | `suspendedMockingEnabled` | Push/pause cycle: pushes updates for `RealismConstants.SUSPENDED_PUSH_DURATION_MS`, then skips ticks for `RealismConstants.SUSPENDED_PAUSE_DURATION_MS`. Automatically disabled during `ROUTE_REPLAY` mode. |

**Internal architecture:**

Each tick, `captureSnapshot()` reads all `@Volatile` service fields into an immutable `LocationSnapshot` — eliminating TOCTOU races between fields read in `buildLocation`. The pure function `buildLocation(state, nowMs, random)` takes that snapshot and returns a `LocationFix` (or `null` during suspended-phase). No Android imports in `buildLocation`; `Random` is injected for deterministic unit testing. `applyToProvider()` translates `LocationFix` → `android.location.Location` and pushes it to `LocationManager`.

Execution order inside `buildLocation`: suspended-phase check → altitude walk → bearing hold → position jitter → warm-up accuracy envelope → accuracy perturbation → satellite extras.

All realism tuning values live in `AppConstants.RealismConstants`.

---

### Foreground Service

Persistent notification while spoofing active. App keeps running when minimized or screen off.

Declared with `foregroundServiceType="location"`. Started via `ServiceCompat.startForeground` with `FOREGROUND_SERVICE_TYPE_LOCATION` (required API 34+). Restart behavior: `START_STICKY`. Notification channel: `IMPORTANCE_LOW`, channel ID `AppConstants.NotificationConstants.CHANNEL_ID_ACTIVE`. Update loop runs as coroutine with `SupervisorJob()` scope. Cleanup: cancel scope + remove test provider in `onDestroy`.

Key files: `:core:location/MockLocationService.kt`

---

### Floating Joystick

Circular overlay on all apps. Drag → moves fake location. Release → stops. Draggable anywhere on screen.

Requires `SYSTEM_ALERT_WINDOW`. Uses `TYPE_APPLICATION_OVERLAY` with `FLAG_NOT_FOCUSABLE` (mandatory — prevents stealing keyboard focus from game) and `FLAG_NOT_TOUCH_MODAL`. Drag-to-reposition via `View.OnTouchListener` updating `WindowManager.LayoutParams`. Input normalized to direction vector, multiplied by speed (m/s), new lat/lon via Haversine. Pushed to `MockLocationService`. Overlay utilities shared via `:core:overlay`.

Cleanup critical: must call `windowManager.removeView` in `onDestroy` with null/attached check.

Edge cases:
- Revoking `SYSTEM_ALERT_WINDOW` while overlay showing → `removeView` throws. Wrap in try/catch.
- MIUI/ColorOS: overlay permissions reset on reboot. Show reminder on startup.

Key files: `:feature:joystick:impl/JoystickOverlayService.kt`, `:feature:joystick:impl/JoystickView.kt`

---

### Map (MapLibre)

Main screen shows OpenStreetMap centered on `AppConstants.MapConstants.DEFAULT_LAT`, `AppConstants.MapConstants.DEFAULT_LON` on first load. Scroll gestures enabled by default. TopAppBar with hamburger icon opens nav drawer via `onOpenDrawer: () -> Unit` lambda (drawer owned by `LjApp`, not `LjNavHost`). Spoofed location shown as marker. Map follows marker during spoofing.

Library: MapLibre Android SDK 12.x (not osmdroid, not Google Maps). OSM tile source via `RasterSource`. Location marker: `SymbolLayer` backed by GeoJSON — update coords, don't remove/re-add layer. Route polylines: `LineLayer` backed by GeoJSON `FeatureCollection`. Offline tiles via `OfflineManager.downloadRegion()`.

Interactions: long-press → bottom sheet "Walk here / Teleport here". Tap route point → select. Tap empty map in edit mode → add waypoint. Camera follow: disable on `REASON_API_GESTURE`, re-enable via re-center FAB.

Edge cases: forward all lifecycle events to `MapView`. Never call MapLibre APIs before `onMapReady`.

Key files: `:feature:map:impl/MapScreen.kt`, `:feature:map:impl/MapViewModel.kt`

---

### Route System

Create waypoints on map → polyline in order. Two route types:
- **STRAIGHT** (`RouteType.STRAIGHT`): straight-line segments between waypoints, no network required.
- **GUIDED** (`RouteType.GUIDED`): OSRM road-following segments. On OSRM failure, sets `osrmError = true` in `CreatorState` — no silent fallback to straight line.

Save named route to Room. Edit (drag/add/delete waypoints). Delete via swipe or detail screen. Replay at current speed with progress indicator. Optional looping.

Storage: `RouteEntity` + `WaypointEntity` one-to-many. Waypoints store `routeId`, `lat`, `lon`, `orderIndex`.

Replay: interpolate between waypoints at speed (m/s), compute bearing via `atan2`, advance by `speed * deltaTime`, snap within `AppConstants.LocationConstants.WALK_ARRIVAL_THRESHOLD_METERS` m of waypoint. Loops: interpolate smoothly from last to first waypoint.

Recording: collect real location at `AppConstants.LocationConstants.UPDATE_INTERVAL_MS` ms, simplify via Ramer-Douglas-Peucker (epsilon = `AppConstants.LocationConstants.RDP_SIMPLIFICATION_EPSILON_METERS` m), save on stop.

Edge cases: <2 waypoints → disable replay. Resume replay after service restart (persist waypoint index in DataStore).

Key files: `:feature:routes:impl/RoutesScreen.kt`, `:feature:routes:impl/RouteCreatorScreen.kt`, `:feature:routes:impl/RoutesViewModel.kt`, `:core:database/RouteDao.kt`, `:core:routing/RouteReplayEngine.kt`

---

### Favorite Locations

Save named locations. Tap from list → instantly teleport spoofed position. Rename and delete supported.

Two add flows: from coordinates (inline dialog with name/lat/lon fields) and from map (navigates to `MapPickerScreen` where user taps map or uses Nominatim search, enters name, then confirms). MapPickerScreen calls back with `(name, lat, lon)`.

Storage: `FavoriteEntity` flat table (no relations). Teleport: set position directly, push one update, camera jumps. Sort by `createdAt` desc default; optional alpha sort.

Key files: `:feature:favorites:impl/FavoritesScreen.kt`, `:feature:favorites:impl/FavoritesViewModel.kt`, `:core:database/FavoriteDao.kt`

---

### Speed Profiles

Three presets: Walk (`AppConstants.ProfileConstants.WALK_SPEED_MPS` m/s), Run (`AppConstants.ProfileConstants.RUN_SPEED_MPS` m/s), Bike (`AppConstants.ProfileConstants.BIKE_SPEED_MPS` m/s). All user-editable. Applies to joystick, route replay, roaming.

Stored in DataStore. UI: three chips or segmented button in widget + Settings. Change takes effect immediately on next tick.

Edge cases: clamp `AppConstants.ProfileConstants.MIN_SPEED_MS`–`AppConstants.ProfileConstants.MAX_SPEED_MS` m/s. Warn inline below speed input when >`AppConstants.ProfileConstants.ANTI_CHEAT_WARNING_THRESHOLD_MS` m/s (anti-cheat risk). Warns in generic language — no game names.

Key files: `:feature:settings:impl/SettingsScreen.kt`, `:core:data/SettingsRepository.kt`

---

### Floating Widget

Small floating button overlay. Tap → expand panel with configured quick-access controls. Items configured in Settings.

Same overlay mechanism as joystick (via `:core:overlay`). Separate service, toggled independently. State: collapsed (FAB) / expanded (panel) via `ValueAnimator`. Items stored in DataStore as `stringSetPreferencesKey`. Binds to `MockLocationService` in `onStartCommand`, unbinds in `onDestroy`.

Edge cases: no items configured → show placeholder. Clamp panel to screen bounds. Re-clamp on `onConfigurationChanged`.

Key files: `:feature:widget:impl/FloatingWidgetService.kt`, `:feature:settings:impl/SettingsScreen.kt`

---

### Click-to-Move / Teleport

Tap map → bottom sheet with "Walk here" or "Teleport here".

Walk here: bearing computed from current to target, advance at `currentSpeed` m/s per tick, snap within `AppConstants.LocationConstants.WALK_ARRIVAL_THRESHOLD_METERS` m. Teleport: set position directly, one update push.

Edge cases: new walk-here cancels previous. Walk-here while route replaying → ask confirmation to stop replay.

Key files: `:feature:map:impl/MapViewModel.kt`

---

### Roaming Mode

Set center, radius, duration. Walks randomly within radius. Two modes: simple (straight-line, no network) and road-following (OSRM routes, opt-in). OSRM failure → fall back to straight-line automatically.

Algorithm (road-following): pick random destination in radius (uniform disk distribution) → fetch OSRM route → walk route → repeat until duration elapsed.

OSRM endpoint: base URL, overview, geometries, and profile constants in `AppConstants.OsrmConstants` and `AppConstants.RoamingConstants`.

Edge cases: cache last OSRM route (don't re-fetch while walking it). Radius/duration changes apply on next waypoint pick. Persist start time in DataStore (survives restarts).

Key files: `:core:routing/RoamingEngine.kt`, `:core:routing/OsrmClient.kt`, `:feature:settings:impl/SettingsScreen.kt`

---

### Export / Import

Settings → export all data to JSON, import from previous export. Schema version: `AppConstants.ExportConstants.SCHEMA_VERSION`.

Covers: routes, favorites, speed profiles, widget config, roaming defaults.

Export: serialize via `kotlinx.serialization` → write to `getExternalFilesDir(null)` → share via `FileProvider + Intent.ACTION_SEND`.

Import: file picker (`OpenDocument`, MIME `AppConstants.ExportConstants.MIME_TYPE`) → parse + validate `schemaVersion == AppConstants.ExportConstants.SCHEMA_VERSION` → confirm "replace all data?" → clear Room + DataStore → insert. All I/O on `Dispatchers.IO`.

Edge cases: malformed JSON → show "Invalid file". Missing fields → use `@SerialName` defaults. Skip confirmation on fresh install (empty DB).

Key files: `:feature:settings:impl/SettingsScreen.kt`, `:core:data/SettingsRepository.kt`

---

### QR Share / Transfer

Settings → share or import config between devices via QR codes. Export splits `ExportData` into scannable JSON chunks encoded as QR codes. Import scans chunks sequentially, reassembles, validates, and imports.

Chunking: `QrChunker` splits serialized JSON into chunks sized for QR capacity (alphanumeric mode, ~4296 chars max per QR). Each chunk prefixed with envelope (`ChunkEnvelope`) containing chunk index, total count, and checksum.

Scanner: `QrScannerScreen` uses ZXing (`CameraX` + `ImageAnalysis`) to scan QR codes. `ZxingImageAnalyzer` decodes frames, `ChunkReassembler` collects chunks in order, validates checksum, reassembles JSON.

Share dialog: `QrShareDialog` displays chunks as QR images via `QrEncoder` (ZXing `MultiFormatWriter`). User swipes through chunks; each displayed as Compose `Image` from generated `Bitmap`.

Edge cases: chunk loss during scan → reassembler detects missing index, shows progress. Corrupted chunk → checksum mismatch, retry. Large exports → many chunks, show progress indicator.

Key files: `:feature:settings:impl/QrScannerScreen.kt`, `:feature:settings:impl/QrShareDialog.kt`, `:feature:settings:impl/QrEncoder.kt`, `:feature:settings:impl/QrChunker.kt`, `:feature:settings:impl/ChunkReassemblerTest.kt`

---

### Last Remembered Location

On app restart, restores last spoofed position. No manual re-entry needed.

DataStore keys (in `:core:datastore`):
- `REMEMBER_LAST_LOCATION` (`Boolean`) — feature toggle
- `LAST_LATITUDE` (`Double`) — last spoofed latitude
- `LAST_LONGITUDE` (`Double`) — last spoofed longitude

On service start: if `REMEMBER_LAST_LOCATION` is true and valid coordinates exist, seed initial position from `LAST_LATITUDE`/`LAST_LONGITUDE`. On each position update: persist coordinates to DataStore.

---

### Setup / Onboarding

First launch → multi-step onboarding. Track completion via `ONBOARDING_COMPLETE` DataStore key. Module: `:feature:onboarding`.

Steps:
1. Welcome
2. Grant `ACCESS_FINE_LOCATION`
3. Grant `SYSTEM_ALERT_WINDOW`
4. Enable mock location (deep link to Developer Options, "Check again" button re-checks `AppOpsManager`)
5. Done → MapScreen

Permission checks: `ContextCompat.checkSelfPermission`, `Settings.canDrawOverlays(context)`, `AppOpsManager.checkOpNoThrow(OPSTR_MOCK_LOCATION)`.

Edge cases: allow skipping each permission (show banner for missing). Detect revoked permissions on `onResume`.

Key files: `:feature:onboarding:impl/OnboardingScreen.kt`, `:feature:onboarding:impl/OnboardingViewModel.kt`

---

### Info / About Page

Module: `:app` (no feature submodules). Composable: `InfoScreen.kt` (no ViewModel, static content).

Shows app version, GitHub link, issue tracker, privacy note, credits (MapLibre, OSM, OSRM, ZXing), MIT license. Accessible from: `IdleScreen` card ("About") and drawer bottom item. Route: `info` in `LjNavHost`.

Key files: `app/src/main/kotlin/com/locationjoystick/app/InfoScreen.kt`

---

## Domain Models Reference

All in `:core:model`. Pure Kotlin — no Android imports, no Room annotations. Room entities in `:core:database` mirror these and map via extension functions.

| Model | Fields |
|-------|--------|
| `LatLng` | `latitude: Double`, `longitude: Double` |
| `Waypoint` | `id: String`, `position: LatLng`, `orderIndex: Int` |
| `Route` | `id: String`, `name: String`, `waypoints: List<Waypoint>`, `isLooping: Boolean`, `routeType: RouteType`, `createdAt: Long`, `updatedAt: Long` |
| `FavoriteLocation` | `id: String`, `name: String`, `position: LatLng`, `createdAt: Long` |
| `RouteType` | enum: `STRAIGHT`, `GUIDED` |
| `SpeedProfile` | `id: String`, `name: String`, `speedMetersPerSecond: Double` |
| `RoamingConfig` | `centerPosition: LatLng`, `radiusMeters: Double`, `durationSeconds: Long`, `useRoadSnapping: Boolean` |
| `RoamingDefaults` | `radiusMeters: Double`, `distanceMeters: Double`, `speedProfileId: String`, `followRoads: Boolean`, `returnToInitialLocation: Boolean` |
| `AppSettings` | `activeSpeedProfileId: String`, `joystickStyle: JoystickStyle`, `enabledWidgetFeatures: List<WidgetFeature>`, `mapFollowsLocation: Boolean`, `useRoadSnappingByDefault: Boolean`, `speedUnit: SpeedUnit`, `roamingDefaults: RoamingDefaults`, `bearingHoldOnIdle: Boolean`, `altitudeEnabled: Boolean`, `warmupEnabled: Boolean`, `satelliteExtrasEnabled: Boolean`, `suspendedMockingEnabled: Boolean` |
| `ExportData` | `schemaVersion: Int`, `exportedAt: Long`, `settings: AppSettings`, `speedProfiles: List<SpeedProfile>`, `routes: List<Route>`, `favoriteLocations: List<FavoriteLocation>`, `jitterIdleRadius: Double`, `jitterMovingRadius: Double`, `jitterIntervalSeconds: Int` |
| `MockMode` | enum: `JOYSTICK`, `ROUTE_REPLAY`, `ROAMING`, `TELEPORT` |
| `MockLocationState` | enum: `IDLE`, `RUNNING`, `PAUSED`, `ERROR` |
| `WidgetFeature` | enum: `JOYSTICK_TOGGLE`, `JOYSTICK_LOCK`, `ROUTES_FLOATING`, `FAVORITES_FLOATING`, `SPEED_CYCLE`, `MAP_FLOATING` |
| `JoystickStyle` | enum: `FLOATING`, `FIXED` |
| `SpeedUnit` | enum: `KMH`, `MPH` |

---

## Key Services

| Service | Module | Type | Purpose |
|---------|--------|------|---------|
| `MockLocationService` | `:core:location` | ForegroundService | Owns `LocationManager` test provider. Exposes `StateFlow<SpoofState>`. Commands: `startSpoofing`, `updatePosition`, `stopSpoofing`. |
| `JoystickOverlayService` | `:feature:joystick:impl` | Service | Extends `OverlayService`. Manages `WindowManager` overlay. Reads joystick input → `LocationRepository.updatePosition()`. |
| `FloatingWidgetService` | `:feature:widget:impl` | Service | Manages widget overlay. Binds to `MockLocationService`. |
| `RoamingEngine` | `:core:routing` | Class (not service) | Instantiated by `MockLocationService`. Owns OSRM client + random waypoint picker. Runs on service scope. |

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

### Coverage

Coverage tracked via [kotlinx-kover](https://github.com/Kotlin/kotlinx-kover) (v0.8.3). Applied in every module via convention plugins. Root project aggregates all modules into single merged report.

```bash
make coverage        # generate HTML + XML reports
make coverage-open   # open HTML report in browser
```

HTML report: `build/reports/kover/html/index.html`
XML report (CI): `build/reports/kover/report.xml`

Per-module report: `./gradlew :<module>:koverHtmlReport`

### Unit Tests (`:core:*`)
- Repository logic with fake DAO (in-memory Room)
- Route replay interpolation: waypoints A+B → assert position after N ticks
- RDP simplification: known path → assert simplified output
- Bearing calculation: known lat/lon pairs → expected bearing
- `randomPointInRadius`: output always within specified radius
- Export/import serialization: round-trip full `ExportData` through JSON

Shared test utilities in `:core:testing`.

### Integration Tests (`:feature:*`)
- Hilt testing with `@HiltAndroidTest`
- Full route save → list → replay with in-memory Room
- Favorites: add → list → teleport → delete

### UI Tests (Compose)
- `ComposeTestRule` for screen-level tests
- Setup flow: mock permission states, assert correct screen transitions
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
- **Haversine**: use for distance between two `LatLon` points (R = `AppConstants.LocationConstants.EARTH_RADIUS_METERS`)
- **Bearing**: `atan2(sin(dLon)*cos(lat2), cos(lat1)*sin(lat2) - sin(lat1)*cos(lat2)*cos(dLon))`
- **Advance position**: spherical law of cosines from `LatLon` + bearing + distance

---

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
