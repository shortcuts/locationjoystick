# Constants

All constants → `:core:common/constants/AppConstants.kt`.

## Nested Objects

| Object | Contents |
|--------|----------|
| `LocationConstants` | Update interval, earth radius, walk threshold |
| `ProfileConstants` | Slow Walk/Walk/Run/Bike/Drive speed presets, enabled speed-cycle set, route-jump default, min/max speed, anti-cheat threshold |
| `JitterConstants` | Accuracy min/max, jitter radii, max step per tick, speed variation percentages |
| `RealismConstants` | Altitude sigma/drift/clamp, warmup duration, satellite interval, suspended push/pause durations, pedometer mocking enabled default |
| `PedometerConstants` | Max walking speed, stride base, stride speed factor, stride jitter percentage |
| `RoamingConstants` | Default radius/distance, planting start/end radius, planting default speed (Bike), spiral pitch/chord, loop defaults, speed profile IDs, arrival threshold, road-snapping defaults |
| `OsrmConstants` | Demo + FOSSGIS base URLs, ladder backoffs, time budgets, bisection thresholds |
| `MapConstants` | Default coordinates, zoom, favorite street-level zoom, tile URL, OSM User-Agent app name, OSM cache-bust marker, OSM OkHttp per-host limit, preview max zoom, camera snap distance, map source/layer IDs (including preview) |
| `NominatimConstants` | Search endpoint |
| `ExportConstants` | Schema version, MIME type, GPX version/creator, max GPX import size, max GPX route waypoint count |
| `CooldownConstants` | Walk-to and teleport cooldown durations |
| `UnitConversionConstants` | Speed/distance unit conversion factors |
| `MapColorConstants` | Map active button color, route colors |
| `AnimationConstants` | Spring damping ratio, stiffness values for nav transitions |
| `TimeConstants` | Time-related constants |
| `NotificationConstants` | Channel IDs |
| `ServiceConstants` | Service action strings |
| `DataStoreConstants` | DataStore preference keys |
| `JoystickConstants` | Joystick sizing/sensitivity and overlay fill/knob alphas |
| `WidgetConstants` | Widget sizing |
| `RouteConstants` | Route-related defaults, planting circle radius/vertex clamps, reserved paste-play route id/name |
| `DatabaseConstants` | DB name, version |
| `TopBarConstants` | Max characters for the idle Start place-name suffix |
| `AppInfo` | Version name, fork changelog/releases URL, upstream issues/docs/troubleshooting URLs |
| `WhatsNewConstants` | APK asset file name for the per-version changelog JSON |
| `FollowerRestorationConstants` | Follower boot restoration retry delay, max delay, max attempts, jitter range |

## Rules

- No new top-level/companion constant outside `AppConstants`. Add there.
- Modules needing constants: declare `implementation(project(":core:common"))` in `build.gradle.kts`.
- Exception: `:core:model` is pure JVM, cannot depend on `core:common`. Constants only used in `:core:model` stay there.