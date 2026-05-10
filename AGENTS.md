# locationjoystick — Agent Reference

> **Primary reference for all AI coding agents.** Read before touching any file.
> Every section implementation-aware. Vague answers wrong answers.

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Architecture](#architecture)
3. [Module Map](#module-map)
4. [Feature Specifications](#feature-specifications)
   - [Mock Location Engine](#mock-location-engine)
   - [Foreground Service](#foreground-service)
   - [Floating Joystick](#floating-joystick)
   - [Map (MapLibre)](#map-maplibre)
   - [Route System](#route-system)
   - [Favorite Locations](#favorite-locations)
   - [Speed Profiles](#speed-profiles)
   - [Floating Widget](#floating-widget)
   - [Click-to-Move / Teleport](#click-to-move--teleport)
   - [Roaming Mode](#roaming-mode)
   - [Export / Import](#export--import)
   - [Setup / Onboarding](#setup--onboarding)
5. [Domain Models Reference](#domain-models-reference)
6. [Key Services](#key-services)
7. [Permissions Matrix](#permissions-matrix)
8. [Technical Constraints](#technical-constraints)
9. [Code Style Rules](#code-style-rules)
10. [Testing Strategy](#testing-strategy)
11. [Common Patterns](#common-patterns)

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

**Offline-first.** No backend. No accounts. All data on-device in Room + DataStore.

---

## Architecture

Multi-module, NowInAndroid-style. Each feature = Gradle module. Shared code in `:core:*`.

```
MVVM + Repository pattern
  ViewModel → Repository → DataSource (Room / DataStore / LocationManager)
  ViewModel exposes StateFlow / SharedFlow
  Compose UI collects flows via collectAsStateWithLifecycle()
```

**DI**: Hilt throughout. Every ViewModel `@HiltViewModel`. Every repository `@Singleton`.

**Reactive streams**: Kotlin Flow everywhere. No RxJava. No LiveData.

**Coroutines**: `viewModelScope` for UI-bound work. `ServiceScope` (tied to service lifecycle) for background. Never `GlobalScope`.

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

**Behavior**: Injects fake GPS into Android location system. All apps (incl. Pokémon GO) receive spoofed coords as real GPS.

**Technical implementation**:

```kotlin
// Registering the test provider (API 31+ style)
locationManager.addTestProvider(
    LocationManager.GPS_PROVIDER,
    false, false, false, false, true, true, true,
    ProviderProperties.POWER_USAGE_HIGH,
    ProviderProperties.ACCURACY_FINE
)
locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)

// Pushing a location update
val location = Location(LocationManager.GPS_PROVIDER).apply {
    latitude = lat
    longitude = lon
    altitude = 0.0
    accuracy = 3.0f          // tight accuracy — games trust it more
    speed = speedMs          // meters/second
    bearing = bearing        // degrees, 0–360
    time = System.currentTimeMillis()
    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
}
locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, location)
```

**Update rate**: 1 Hz (every 1000 ms). Matches real GPS cadence. Pokémon GO accepts it.

**Prerequisite**: User must enable "Mock location app" in Developer Options and select locationjoystick. App cannot bypass. Check at runtime:

```kotlin
val appOps = getSystemService(AppOpsManager::class.java)
val mode = appOps.checkOpNoThrow(
    AppOpsManager.OPSTR_MOCK_LOCATION,
    Process.myUid(),
    packageName
)
val isMockEnabled = mode == AppOpsManager.MODE_ALLOWED
```

**Cleanup**: On service stop, call `locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)`. Failure leaves ghost provider breaking real GPS until reboot.

**Edge cases**:
- Another app holds mock location slot → `addTestProvider` throws `IllegalArgumentException`. Catch, show clear error.
- `elapsedRealtimeNanos` must be monotonically increasing. Never set fixed value.
- `accuracy` below 1.0f can trigger anti-cheat. Keep 2.0–5.0f.

**Key files**: `:service:location/MockLocationService.kt`, `:core:data/LocationRepository.kt`

---

### Foreground Service

**Behavior**: Persistent notification while spoofing active. App keeps running when minimized or screen off.

Manifest declaration:
```xml
<service
    android:name=".MockLocationService"
    android:foregroundServiceType="location"
    android:exported="false" />
```

Starting (API 34+ requires `FOREGROUND_SERVICE_TYPE_LOCATION`):
```kotlin
ServiceCompat.startForeground(
    this,
    NOTIFICATION_ID,
    buildNotification(),
    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
)
```

Restart behavior: `START_STICKY`. OS kills → restarts automatically.

Notification channel: `IMPORTANCE_LOW` — no sound, no vibration, minimal battery. Channel ID: `"location_spoof_channel"`.

Update loop as coroutine:
```kotlin
private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

private fun startUpdateLoop() {
    serviceScope.launch {
        while (isActive) {
            pushLocationUpdate()
            delay(1000L)   // 1 Hz — never Thread.sleep()
        }
    }
}

override fun onDestroy() {
    serviceScope.cancel()
    locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
    super.onDestroy()
}
```

**Edge cases**:
- API 34+, missing `FOREGROUND_SERVICE_LOCATION` permission → crashes at `startForeground`. Declare in manifest.
- Notification needs tap action (open app) + stop action (stop spoofing).
- `SupervisorJob()` → one failed child coroutine doesn't cancel whole scope.

**Key files**: `:service:location/MockLocationService.kt`

---

### Floating Joystick

**Behavior**: Circular joystick overlay on all apps. Drag → moves fake location. Release → stops. Draggable to any screen position.

Permission required: `SYSTEM_ALERT_WINDOW`. Grant via:
```kotlin
startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
    Uri.parse("package:$packageName")))
```
Check: `Settings.canDrawOverlays(context)`.

Adding overlay view:
```kotlin
val params = WindowManager.LayoutParams(
    JOYSTICK_SIZE_PX,
    JOYSTICK_SIZE_PX,
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
    PixelFormat.TRANSLUCENT
)
params.gravity = Gravity.BOTTOM or Gravity.START
params.x = 32
params.y = 32
windowManager.addView(joystickView, params)
```

`FLAG_NOT_FOCUSABLE` mandatory — without it overlay steals keyboard focus from game.

Drag-to-reposition: `View.OnTouchListener` updates `params.x`/`params.y`, calls `windowManager.updateViewLayout(joystickView, params)` on `ACTION_MOVE`.

Input → direction vector: normalize thumb offset to unit vector, multiply by speed (m/s), compute new lat/lon via Haversine, push to `MockLocationService` via bound service or `SharedFlow`.

**Cleanup** (critical — GPS JoyStick's #1 bug):
```kotlin
override fun onDestroy() {
    if (::joystickView.isInitialized && joystickView.isAttachedToWindow) {
        windowManager.removeView(joystickView)
    }
    super.onDestroy()
}
```

**Edge cases**:
- User revokes `SYSTEM_ALERT_WINDOW` while overlay showing → `removeView` throws. Wrap in try/catch.
- MIUI/ColorOS: overlay permissions reset on reboot. Show reminder on startup.
- Must not intercept touches outside bounds. Use `FLAG_NOT_TOUCH_MODAL` alongside `FLAG_NOT_FOCUSABLE`.

**Key files**: `:feature:joystick/JoystickOverlayService.kt`, `:feature:joystick/JoystickView.kt`

---

### Map (MapLibre)

**Behavior**: Main screen shows OpenStreetMap. Spoofed location shown as marker. Map follows marker during active spoofing. Pan/zoom freely.

Library: **MapLibre Android SDK 12.x**. Not osmdroid (archived 2024). Not Google Maps (requires Play Services).

OSM tile source:
```kotlin
val osmSource = RasterSource(
    "osm-source",
    TileSet("tileset", "https://tile.openstreetmap.org/{z}/{x}/{y}.png").apply {
        maxZoom = 19f
    },
    256
)
mapboxMap.style?.addSource(osmSource)
mapboxMap.style?.addLayer(RasterLayer("osm-layer", "osm-source"))
```

Location marker: `SymbolLayer` backed by GeoJSON `Feature`. Update coords on each push — don't remove/re-add layer.

Route polylines: `LineLayer` backed by GeoJSON `FeatureCollection`. Each segment = `LineString`. Color/width in paint properties.

Route point markers: `SymbolLayer` with custom icon bitmap for waypoints.

Offline tiles: `OfflineManager.downloadRegion()` for current viewport.

**Map interactions**:
- Long-press → bottom sheet: "Walk here" / "Teleport here".
- Tap existing route point → select (show delete/move handles).
- Tap empty map in route-edit mode → add waypoint.

**Camera follow**: Spoofing active + no manual pan → keep camera centered. Detect pan via `OnCameraMoveStartedListener` (`REASON_API_GESTURE` → set "user panning" flag). Reset on "re-center" FAB.

**Edge cases**:
- OSM tile servers have usage policy. For production, use self-hosted or CDN.
- `MapView` must forward all lifecycle events: `onStart`, `onResume`, `onPause`, `onStop`, `onSaveInstanceState`, `onLowMemory`, `onDestroy`.
- Never call MapLibre APIs before `onMapReady` fires.

**Key files**: `:feature:map/MapScreen.kt`, `:feature:map/MapViewModel.kt`

---

### Route System

**Behavior**:
- **Create**: Tap waypoints on map → polyline connects in order.
- **Save**: Name the route → stored in Room.
- **Edit**: Tap route → edit mode. Drag waypoints, add, delete individual points.
- **Delete**: Swipe-to-delete in list, or delete from detail screen.
- **Replay**: Spoofed location walks along route at selected speed. Progress shown on map.
- **Loop**: Reach last waypoint → restart from first. Toggle per-route.

Domain model:
```kotlin
data class Route(
    val id: Long = 0,
    val name: String,
    val waypoints: List<LatLon>,   // ordered list
    val createdAt: Instant,
    val isLoop: Boolean = false
)

data class LatLon(val lat: Double, val lon: Double)
```

Room entity: `RouteEntity` + `WaypointEntity` (one-to-many). Waypoints store `routeId`, `lat`, `lon`, `orderIndex`.

Route replay engine:
1. Interpolate between consecutive waypoints at current speed (m/s).
2. Compute bearing to next waypoint via `atan2`.
3. Advance position by `speed * deltaTime` meters along bearing.
4. Within 1 meter of waypoint → snap, advance to next.
5. Push each position to `MockLocationService`.

Route recording:
1. Tap "Record" → service collects real location at 1 Hz.
2. Simplify path via Ramer-Douglas-Peucker (epsilon = 5 m).
3. On "Stop" → dialog: "Save route?" name pre-filled as "Route YYYY-MM-DD HH:mm".

**Edge cases**:
- <2 waypoints → can't replay. Disable button, show tooltip.
- Service killed/restarted during replay → resume from last waypoint index (persist in DataStore).
- Loop routes: last waypoint → interpolate smoothly back to first (no teleport).

**Key files**: `:feature:routes/RouteListScreen.kt`, `:feature:routes/RouteEditorScreen.kt`, `:feature:routes/RouteViewModel.kt`, `:core:database/RouteDao.kt`, `:service:location/RouteReplayEngine.kt`

---

### Favorite Locations

**Behavior**: Save named locations. Tap from list → instantly teleports spoofed position. Can rename and delete.

Domain model:
```kotlin
data class FavoriteLocation(
    val id: Long = 0,
    val name: String,
    val lat: Double,
    val lon: Double,
    val createdAt: Instant
)
```

Room entity: `FavoriteEntity`. Flat table, no relations.

Add: long-press on map → "Save as favorite" → name dialog → insert Room.

Teleport: set spoofed position to `(lat, lon)` without interpolation. Push one update. Camera jumps.

**Edge cases**:
- Duplicate names allowed. No uniqueness enforced.
- Deleting active teleport destination has no effect — position already set.
- Sort by `createdAt` desc by default; option to sort alphabetically.

**Key files**: `:feature:favorites/FavoritesScreen.kt`, `:feature:favorites/FavoritesViewModel.kt`, `:core:database/FavoriteDao.kt`

---

### Speed Profiles

**Behavior**: Three presets — Walk, Run, Bike. Customizable m/s per preset. Applies to joystick, route replay, roaming.

| Profile | Default speed |
|---|---|
| Walk | 1.4 m/s (~5 km/h) |
| Run | 3.0 m/s (~11 km/h) |
| Bike | 5.5 m/s (~20 km/h) |

DataStore keys:
```kotlin
val WALK_SPEED = doublePreferencesKey("walk_speed")
val RUN_SPEED = doublePreferencesKey("run_speed")
val BIKE_SPEED = doublePreferencesKey("bike_speed")
val ACTIVE_PROFILE = stringPreferencesKey("active_profile") // "walk" | "run" | "bike"
```

UI: three chips or segmented button in widget and Settings. Changing profile takes effect immediately.

**Edge cases**:
- Clamp: min 0.1 m/s, max 15.0 m/s.
- Speed >~8 m/s → warn "High speeds may trigger anti-cheat detection in some games."
- Applied per-tick. Mid-replay change takes effect on next tick.

**Key files**: `:feature:settings/SpeedSettingsScreen.kt`, `:core:data/SpeedProfileRepository.kt`

---

### Floating Widget

**Behavior**: Small floating button overlay. Tap → expand panel with configured quick-access controls. Items configured in Settings.

Same overlay mechanism as joystick: `TYPE_APPLICATION_OVERLAY`, `SYSTEM_ALERT_WINDOW`. Separate services — each toggled independently.

Widget state: collapsed (FAB) / expanded (panel). Animate via `ValueAnimator` on panel height.

Configurable items in DataStore:
```kotlin
val WIDGET_ITEMS = stringSetPreferencesKey("widget_items")
// Possible values: "speed_selector", "start_stop", "active_route", "favorites_shortcut", "roaming_toggle"
```

Communicates with `MockLocationService` via bound service. Bind in `onStartCommand`, unbind in `onDestroy`.

**Edge cases**:
- All items removed → show placeholder: "Add items in Settings → Widget."
- Expanded panel must not exceed screen bounds. Clamp after expansion.
- On rotation, overlay may go off-screen. Re-clamp in `onConfigurationChanged`.

**Key files**: `:feature:widget/FloatingWidgetService.kt`, `:feature:widget/WidgetPanel.kt`, `:feature:settings/WidgetConfigScreen.kt`

---

### Click-to-Move / Teleport

**Behavior**: Tap map → bottom sheet with two options:
- **Walk here**: spoofed location moves toward tapped point at current speed, straight line.
- **Teleport here**: jumps instantly to tapped point.

Walk-here: compute bearing from current to target:
```kotlin
fun bearing(from: LatLon, to: LatLon): Double {
    val dLon = Math.toRadians(to.lon - from.lon)
    val lat1 = Math.toRadians(from.lat)
    val lat2 = Math.toRadians(to.lat)
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    return (Math.toDegrees(atan2(y, x)) + 360) % 360
}
```
Advance at `currentSpeed` m/s per tick until within 1 meter of target, then snap.

Teleport: set position directly, push one update.

**Edge cases**:
- "Walk here" while already walking to previous target → cancel previous, start new.
- "Walk here" while route replaying → stop replay first (ask confirmation).
- No teleport distance limit.

**Key files**: `:feature:map/MapViewModel.kt` (handles tap events, dispatches to `LocationRepository`)

---

### Roaming Mode

**Behavior**: Set center point, radius (e.g. 2 km), duration (e.g. 30 min). Walks randomly within radius for duration, following real roads when possible.

Two modes:
1. **Simple (default)**: straight-line random walk within radius. No network.
2. **Road-following (opt-in)**: OSRM fetches walking/cycling route between random waypoints.

OSRM endpoint:
```
https://router.project-osrm.org/route/v1/{profile}/{lon1},{lat1};{lon2},{lat2}?overview=full&geometries=geojson
```
Profiles: `foot` (walk/run), `cycling` (bike).

Roaming algorithm (road-following):
1. Pick random destination within radius:
   ```kotlin
   fun randomPointInRadius(center: LatLon, radiusMeters: Double): LatLon {
       val r = radiusMeters * sqrt(Random.nextDouble())
       val theta = Random.nextDouble() * 2 * PI
       val dLat = r * cos(theta) / 111320.0
       val dLon = r * sin(theta) / (111320.0 * cos(Math.toRadians(center.lat)))
       return LatLon(center.lat + dLat, center.lon + dLon)
   }
   ```
2. Fetch OSRM route to destination.
3. Walk route. Destination reached → pick new point, repeat.
4. Stop when elapsed time reaches configured duration.

OSRM failure → fall back to straight-line. Never block roaming on network.

**Edge cases**:
- OSRM rate limits → cache last route. Don't re-fetch if still walking it.
- Radius/duration changed mid-roam → apply on next waypoint pick.
- Persist start time in DataStore — survives service restarts.
- Speed profile change mid-roam takes effect immediately.

**Key files**: `:service:roaming/RoamingEngine.kt`, `:core:network/OsrmClient.kt`, `:feature:settings/RoamingConfigScreen.kt`

---

### Export / Import

**Behavior**: Settings → export all data to JSON, import from previous export. Covers routes, favorites, speed profiles, widget config, roaming defaults.

Export schema version: **1** (increment on breaking changes).

JSON structure:
```json
{
  "schemaVersion": 1,
  "exportedAt": "2025-01-01T00:00:00Z",
  "speedProfiles": {
    "walk": 1.4,
    "run": 3.0,
    "bike": 5.5,
    "active": "walk"
  },
  "routes": [
    {
      "id": 1,
      "name": "Morning loop",
      "isLoop": true,
      "createdAt": "2025-01-01T00:00:00Z",
      "waypoints": [
        { "lat": 48.8566, "lon": 2.3522, "orderIndex": 0 }
      ]
    }
  ],
  "favorites": [
    {
      "id": 1,
      "name": "Home",
      "lat": 48.8566,
      "lon": 2.3522,
      "createdAt": "2025-01-01T00:00:00Z"
    }
  ],
  "widgetItems": ["speed_selector", "start_stop"],
  "roamingDefaults": {
    "radiusMeters": 2000,
    "durationMinutes": 30,
    "roadFollowing": false
  }
}
```

Export flow:
1. Serialize to JSON via `kotlinx.serialization`.
2. Write to `getExternalFilesDir(null)` as `locationjoystick-export-YYYYMMDD.json`.
3. Share via `FileProvider` + `Intent.ACTION_SEND`.

Import flow:
1. File picker via `ActivityResultContracts.OpenDocument` MIME `application/json`.
2. Parse JSON. Validate `schemaVersion == 1`. Reject unknown versions.
3. Confirmation: "This will replace all existing data. Continue?"
4. On confirm: clear all Room tables + DataStore keys, insert imported data.

**Edge cases**:
- Malformed JSON → catch `SerializationException`, show "Invalid file".
- Missing fields → `@SerialName` with defaults for backward compat.
- Fresh install import → no confirmation if database empty.
- Large exports → `Dispatchers.IO` only. Never main thread.

**Key files**: `:feature:settings/ExportImportScreen.kt`, `:core:data/ExportRepository.kt`

---

### Setup / Onboarding

**Behavior**: First launch → multi-step onboarding for permissions + mock location setup.

**Steps**:
1. Welcome — brief app explanation.
2. Grant `ACCESS_FINE_LOCATION` — needed for map centering.
3. Grant `SYSTEM_ALERT_WINDOW` — needed for joystick/widget overlays.
4. Enable mock location — deep link to Developer Options. "Check again" button re-checks `AppOpsManager.OPSTR_MOCK_LOCATION`.
5. Done → main map screen.

Track completion:
```kotlin
val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
```

On launch: false → `OnboardingScreen`, true → `MapScreen`.

Permission checks per step:
- `ACCESS_FINE_LOCATION`: `ContextCompat.checkSelfPermission`
- `SYSTEM_ALERT_WINDOW`: `Settings.canDrawOverlays(context)`
- Mock location: `AppOpsManager.checkOpNoThrow(OPSTR_MOCK_LOCATION, ...)`

**Edge cases**:
- User skips permission → mark skipped, allow proceed. Show banner for missing perms.
- Permission revoked post-onboarding → detect on `onResume`, show non-blocking warning.
- Developer Options hidden until "Build number" tapped 7× — include in onboarding copy.

**Key files**: `:feature:onboarding/OnboardingScreen.kt`, `:feature:onboarding/OnboardingViewModel.kt`

---

## Domain Models Reference

All in `:core:model`. Pure Kotlin — no Android imports, no Room annotations. Room entities in `:core:database` mirror these with `@Entity` and map via extension functions.

```kotlin
// LatLon — used everywhere
data class LatLon(val lat: Double, val lon: Double)

// Route
data class Route(
    val id: Long = 0,
    val name: String,
    val waypoints: List<LatLon>,
    val createdAt: Instant,
    val isLoop: Boolean = false
)

// FavoriteLocation
data class FavoriteLocation(
    val id: Long = 0,
    val name: String,
    val lat: Double,
    val lon: Double,
    val createdAt: Instant
)

// SpeedProfile
enum class SpeedProfileType { WALK, RUN, BIKE }

data class SpeedProfile(
    val type: SpeedProfileType,
    val speedMs: Double   // meters per second
)

// RoamingConfig
data class RoamingConfig(
    val centerLat: Double,
    val centerLon: Double,
    val radiusMeters: Double,
    val durationMinutes: Int,
    val roadFollowing: Boolean
)

// ExportBundle — used for export/import
data class ExportBundle(
    val schemaVersion: Int = 1,
    val exportedAt: Instant,
    val speedProfiles: Map<SpeedProfileType, Double>,
    val activeProfile: SpeedProfileType,
    val routes: List<Route>,
    val favorites: List<FavoriteLocation>,
    val widgetItems: Set<String>,
    val roamingDefaults: RoamingConfig
)
```

---

## Key Services

### MockLocationService
`ForegroundService` in `:service:location`. Owns `LocationManager` test provider. Exposes `StateFlow<SpoofState>` (idle/active/paused). Commands via bound interface: `startSpoofing(lat, lon)`, `updatePosition(lat, lon)`, `stopSpoofing()`.

### JoystickOverlayService
`Service` (not foreground) in `:feature:joystick`. Manages `WindowManager` overlay. Reads joystick input, computes direction vectors, calls `LocationRepository.updatePosition()`.

### FloatingWidgetService
`Service` in `:feature:widget`. Manages widget overlay. Binds to `MockLocationService` to read state and send commands.

### RoamingEngine
Not a service — class instantiated by `MockLocationService`. Runs on service coroutine scope. Owns OSRM client and random waypoint picker.

---

## Permissions Matrix

| Permission | Type | When Required | Manifest |
|---|---|---|---|
| `ACCESS_FINE_LOCATION` | Dangerous | Map centering and route recording | Yes |
| `ACCESS_COARSE_LOCATION` | Dangerous | Fallback if fine denied | Yes |
| `SYSTEM_ALERT_WINDOW` | Special (AppOps) | Floating joystick and widget overlays | Yes |
| `FOREGROUND_SERVICE` | Normal | Running `MockLocationService` as foreground | Yes |
| `FOREGROUND_SERVICE_LOCATION` | Normal (API 34+) | `foregroundServiceType="location"` | Yes (API 34+) |
| `ACCESS_MOCK_LOCATION` | Special (Developer Options) | Injecting fake GPS via `addTestProvider` | No (Dev Options only) |

Notes:
- `ACCESS_MOCK_LOCATION` not manifest permission. Granted in Developer Options → "Select mock location app". Check via `AppOpsManager`.
- `SYSTEM_ALERT_WINDOW` not granted via `requestPermissions`. Requires `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`.
- API 34+: `FOREGROUND_SERVICE_LOCATION` must be in manifest or `startForeground` crashes.

---

## Technical Constraints

- **Min SDK**: API 31 (Android 12). `ProviderProperties.Builder` is API 31+. Don't use deprecated `addTestProvider` overload with raw ints.
- **No Play Services**: MapLibre, not Google Maps. No Firebase.
- **Offline-first**: All core features work without internet. OSRM opt-in, degrades gracefully.
- **No Thread.sleep()**: Use `delay()` in coroutines.
- **No empty catch blocks**: Every `catch` must log or handle.
- **No GlobalScope**: Use `viewModelScope`, `lifecycleScope`, or scoped `CoroutineScope`.
- **No memory leaks**: Every `WindowManager.addView` → matching `removeView` in `onDestroy`. Every scope cancelled in `onDestroy`/`onCleared`.
- **1 Hz location updates**: Don't push faster or slower.
- **Battery**: `IMPORTANCE_LOW` channel. No wake locks unless absolutely necessary.

---

## Code Style Rules

### General
- Kotlin only. No Java.
- Package declaration in every file matching module structure.
- No wildcard imports (`import com.foo.*`).
- Max line length: 120 chars.

### Compose
- State hoisting: ViewModels hold state, Composables receive as params.
- `collectAsStateWithLifecycle()` (not `collectAsState()`).
- No business logic in Composables. Call lambdas, ViewModels handle logic.
- `@Preview` for every non-trivial Composable.
- `remember { }` for expensive computations, `rememberSaveable { }` for state surviving recomposition/process death.

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
- ViewModels: `FeatureViewModel` (e.g. `RouteViewModel`, `MapViewModel`).
- Screens: `FeatureScreen` (e.g. `RouteListScreen`, `MapScreen`).
- DAOs: `EntityDao` (e.g. `RouteDao`, `FavoriteDao`).
- Repositories: `FeatureRepository` (e.g. `RouteRepository`, `LocationRepository`).
- Services: descriptive + `Service` suffix (e.g. `MockLocationService`, `JoystickOverlayService`).

---

## Testing Strategy

### Unit Tests (`:core:*`)
- Repository logic with fake DAO (in-memory Room).
- Route replay interpolation: waypoints A+B → assert position after N ticks.
- RDP simplification: known path → assert simplified output.
- Bearing calculation: known lat/lon pairs → expected bearing.
- `randomPointInRadius`: output always within specified radius.
- Export/import serialization: round-trip full `ExportBundle` through JSON.

### Integration Tests (`:feature:*`)
- Hilt testing with `@HiltAndroidTest`.
- Full route save → list → replay with in-memory Room.
- Favorites: add → list → teleport → delete.

### UI Tests (Compose)
- `ComposeTestRule` for screen-level tests.
- Onboarding flow: mock permission states, assert correct screen transitions.
- Route editor: add waypoints, assert polyline updates.

### What NOT to test
- MapLibre rendering (GPU, not unit-testable).
- `WindowManager` overlay behavior (requires real device).
- `LocationManager.addTestProvider` (requires real device with Developer Options).

---

## Common Patterns

### Bound Service Communication

```kotlin
// In the service
inner class LocalBinder : Binder() {
    fun getService(): MockLocationService = this@MockLocationService
}

// In the client (ViewModel or other service)
private val connection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        locationService = (binder as MockLocationService.LocalBinder).getService()
    }
    override fun onServiceDisconnected(name: ComponentName) {
        locationService = null
    }
}
context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
// Unbind in onDestroy / onCleared
```

### DataStore Read

```kotlin
val walkSpeed: Flow<Double> = dataStore.data.map { prefs ->
    prefs[WALK_SPEED] ?: 1.4
}
```

### DataStore Write

```kotlin
suspend fun setWalkSpeed(speed: Double) {
    dataStore.edit { prefs -> prefs[WALK_SPEED] = speed }
}
```

### Room One-to-Many Query

```kotlin
@Transaction
@Query("SELECT * FROM routes WHERE id = :routeId")
fun getRouteWithWaypoints(routeId: Long): Flow<RouteWithWaypoints>
```

### Haversine Distance (meters)

```kotlin
fun haversineMeters(a: LatLon, b: LatLon): Double {
    val R = 6371000.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val sinDLat = sin(dLat / 2)
    val sinDLon = sin(dLon / 2)
    val h = sinDLat * sinDLat +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sinDLon * sinDLon
    return 2 * R * asin(sqrt(h))
}
```

### Advancing Position by Distance

```kotlin
fun advancePosition(from: LatLon, bearingDeg: Double, distanceMeters: Double): LatLon {
    val R = 6371000.0
    val d = distanceMeters / R
    val brng = Math.toRadians(bearingDeg)
    val lat1 = Math.toRadians(from.lat)
    val lon1 = Math.toRadians(from.lon)
    val lat2 = asin(sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(brng))
    val lon2 = lon1 + atan2(sin(brng) * sin(d) * cos(lat1), cos(d) - sin(lat1) * sin(lat2))
    return LatLon(Math.toDegrees(lat2), Math.toDegrees(lon2))
}
```

---

*Last updated: May 2026. schemaVersion for export/import: 1.*

## Code Exploration Policy

Always use jCodemunch-MCP tools for code navigation. Never fall back to Read, Grep, Glob, or Bash for code exploration.
**Exception:** Use `Read` when you need to edit a file — the agent harness requires a `Read` before `Edit`/`Write` will succeed. Use jCodemunch tools to *find and understand* code, then `Read` only the specific file you're about to modify.

**Start any session:**
1. `resolve_repo { "path": "." }` — confirm the project is indexed. If not: `index_folder { "path": "." }`
2. `suggest_queries` — when the repo is unfamiliar

**Finding code:**
- symbol by name → `search_symbols` (add `kind=`, `language=`, `file_pattern=`, `decorator=` to narrow)
- decorator-aware queries → `search_symbols(decorator="X")` to find symbols with a specific decorator (e.g. `@property`, `@route`); combine with set-difference to find symbols *lacking* a decorator (e.g. "which endpoints lack CSRF protection?")
- string, comment, config value → `search_text` (supports regex, `context_lines`)
- database columns (dbt/SQLMesh) → `search_columns`

**Reading code:**
- before opening any file → `get_file_outline` first
- one or more symbols → `get_symbol_source` (single ID → flat object; array → batch)
- symbol + its imports → `get_context_bundle`
- specific line range only → `get_file_content` (last resort)

**Repo structure:**
- `get_repo_outline` → dirs, languages, symbol counts
- `get_file_tree` → file layout, filter with `path_prefix`

**Relationships & impact:**
- what imports this file → `find_importers`
- where is this name used → `find_references`
- is this identifier used anywhere → `check_references`
- file dependency graph → `get_dependency_graph`
- what breaks if I change X → `get_blast_radius`
- what symbols actually changed since last commit → `get_changed_symbols`
- find unreachable/dead code → `find_dead_code`
- class hierarchy → `get_class_hierarchy`

## Session-Aware Routing

**Opening move for any task:**
1. `plan_turn { "repo": "...", "query": "your task description", "model": "<your-model-id>" }` — get confidence + recommended files; the `model` parameter narrows the exposed tool list to match your capabilities at zero extra requests.
2. Obey the confidence level:
   - `high` → go directly to recommended symbols, max 2 supplementary reads
   - `medium` → explore recommended files, max 5 supplementary reads
   - `low` → the feature likely doesn't exist. Report the gap to the user. Do NOT search further hoping to find it.

**Interpreting search results:**
- If `search_symbols` returns `negative_evidence` with `verdict: "no_implementation_found"`:
  - Do NOT re-search with different terms hoping to find it
  - Do NOT assume a related file (e.g. auth middleware) implements the missing feature (e.g. CSRF)
  - DO report: "No existing implementation found for X. This would need to be created."
  - DO check `related_existing` files — they show what's nearby, not what exists
- If `verdict: "low_confidence_matches"`: examine the matches critically before assuming they implement the feature

**After editing files:**
- If PostToolUse hooks are installed (Claude Code only), edited files are auto-reindexed
- Otherwise, call `register_edit` with edited file paths to invalidate caches and keep the index fresh
- For bulk edits (5+ files), always use `register_edit` with all paths to batch-invalidate

**Token efficiency:**
- If `_meta` contains `budget_warning`: stop exploring and work with what you have
- If `auto_compacted: true` appears: results were automatically compressed due to turn budget
- Use `get_session_context` to check what you've already read — avoid re-reading the same files

## Model-Driven Tool Tiering

Your jcodemunch-mcp server narrows the exposed tool list based on the model you are running as. To avoid wasting requests on primitives when a composite would do, always include `model="<your-model-id>"` in your opening `plan_turn` call.

Replace `<your-model-id>` with your active model:
- Claude Opus variants → `claude-opus-4-7` (or any `claude-opus-*`)
- Claude Sonnet variants → `claude-sonnet-4-6`
- Claude Haiku variants → `claude-haiku-4-5`
- GPT-4o / GPT-5 / o1 / Llama → use the model id as printed by your runner

The `model=` parameter rides on the existing `plan_turn` call — it does **not** add a separate tool invocation. If `plan_turn` is not appropriate for a non-code task, call `announce_model(model="...")` once instead.


<!-- code-review-graph MCP tools -->
## MCP Tools: code-review-graph

**IMPORTANT: This project has a knowledge graph. ALWAYS use the
code-review-graph MCP tools BEFORE using Grep/Glob/Read to explore
the codebase.** The graph is faster, cheaper (fewer tokens), and gives
you structural context (callers, dependents, test coverage) that file
scanning cannot.

### When to use graph tools FIRST

- **Exploring code**: `semantic_search_nodes` or `query_graph` instead of Grep
- **Understanding impact**: `get_impact_radius` instead of manually tracing imports
- **Code review**: `detect_changes` + `get_review_context` instead of reading entire files
- **Finding relationships**: `query_graph` with callers_of/callees_of/imports_of/tests_for
- **Architecture questions**: `get_architecture_overview` + `list_communities`

Fall back to Grep/Glob/Read **only** when the graph doesn't cover what you need.

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

1. The graph auto-updates on file changes (via hooks).
2. Use `detect_changes` for code review.
3. Use `get_affected_flows` to understand impact.
4. Use `query_graph` pattern="tests_for" to check coverage.
