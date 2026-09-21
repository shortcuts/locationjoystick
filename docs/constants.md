# Constants

All constants → `:core:common/constants/AppConstants.kt`.

## Nested Objects

| Object | Contents |
|--------|----------|
| `LocationConstants` | Update interval, earth radius, walk threshold |
| `ProfileConstants` | Slow Walk/Walk/Run/Bike/Drive speed presets, enabled speed-cycle set, route-jump default, min/max speed, anti-cheat threshold |
| `JitterConstants` | Accuracy min/max, jitter radii, max step per tick, speed variation percentages |
| `RealismConstants` | Altitude sigma/drift/clamp, warmup duration, satellite interval, suspended push/pause durations |
| `PlantingConstants` | Shared planting geometry: max radius, chord length (used by roaming and route planting) |
| `RoamingConstants` | Default radius/distance, planting start/end radius, planting default speed (Bike), spiral pitch/chord, loop defaults, OSRM profile IDs, road-snapping defaults |
| `OsrmConstants` | Demo + FOSSGIS base URLs, ladder backoffs, time budgets, bisection thresholds, route cache size/TTL/coordinate scale, cooldown durations and cap, cooldown prefs file name |
| `MapConstants` | Default coordinates, zoom, favorite street-level zoom, tileset version (tile URLs live on the `MapTileSource` enum), OSM User-Agent app name, OSM cache-bust marker, OSM OkHttp per-host limit, ambient tile cache max bytes, preview max zoom, camera snap distance, map source/layer IDs (including preview) |
| `NominatimConstants` | Search + reverse endpoints, debounce, timeouts, min request interval, search cache size/TTL |
| `ElevationConstants` | Open-Meteo endpoint, timeouts, elevation cache size and coordinate scale |
| `ExportConstants` | Schema version, MIME type, GPX version/creator, max GPX import size, max GPX route waypoint count |
| `CooldownConstants` | Distance-tiered teleport cooldown table (distance → seconds) |
| `AnimationConstants` | Spring damping ratio, stiffness values for nav transitions |
| `TimeConstants` | Time-related constants |
| `NotificationConstants` | Channel IDs |
| `ServiceConstants` | Service action strings |
| `DataStoreConstants` | DataStore preference keys |
| `JoystickConstants` | Joystick sizing/sensitivity and overlay fill/knob alphas |
| `RouteConstants` | Route-related defaults, planting circle radius/vertex clamps, reserved paste-play route id/name |
| `DatabaseConstants` | DB name, version |
| `TopBarConstants` | Max characters for the idle Start place-name suffix |
| `AppInfo` | Version name, fork changelog/releases URL, upstream issues/docs/troubleshooting URLs, Capture setup guide URL (`CAPTURE_GUIDE_URL`) |
| `UpdateCheckConstants` | GitHub API URL, connect/read timeouts, check interval, release-tag URL builder |
| `WhatsNewConstants` | APK asset file name for the per-version changelog JSON |
| `FollowerRestorationConstants` | Follower boot restoration retry delay, max delay, max attempts, jitter range |
| `SyncConstants` | Group Sync/QR transfer: poll interval/timeout, server backlog, stale-position threshold, NSD service type and discovery timeout, group code length, max poll failures, NSD re-discovery retries, export fetch timeout |
| `TapToWalkConstants` | Tap to Walk default/min/max map scale (m/px) |
| `CompassTrackingConstants` | Compass detection search window and icon-blob size bounds |
| `LocaleConstants` | Locale SharedPreferences file name and language-tag key |

## Rules

- No new top-level/companion constant outside `AppConstants`. Add there.
- Modules needing constants: declare `implementation(project(":core:common"))` in `build.gradle.kts`.
- Exception: `:core:model` is pure JVM, cannot depend on `core:common`. Constants only used in `:core:model` stay there.