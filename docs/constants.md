# Constants

All constants → `:core:common/constants/AppConstants.kt`.

## Nested Objects

| Object | Contents |
|--------|----------|
| `LocationConstants` | Update interval, earth radius, walk threshold |
| `ProfileConstants` | Slow Walk/Walk/Run/Bike/Drive speed presets, min/max speed, anti-cheat threshold |
| `JitterConstants` | Accuracy min/max, jitter radii, intervals, speed variation percentages |
| `RealismConstants` | Altitude sigma/drift/clamp, warmup duration, satellite interval, suspended push/pause durations, pedometer mocking enabled default |
| `PedometerConstants` | Max walking speed, stride base, stride speed factor, stride jitter percentage |
| `RoamingConstants` | Default radius/distance, speed profile IDs, arrival threshold, road-snapping defaults |
| `OsrmConstants` | Base URL, overview, geometries format |
| `MapConstants` | Default coordinates, zoom, tile URL, map source/layer IDs |
| `NominatimConstants` | Search endpoint |
| `ExportConstants` | Schema version, MIME type, GPX version/creator, max GPX import size |
| `CooldownConstants` | Walk-to and teleport cooldown durations |
| `UnitConversionConstants` | Speed/distance unit conversion factors |
| `MapColorConstants` | Map active button color, route colors |
| `AnimationConstants` | Spring damping ratio, stiffness values for nav transitions |
| `TimeConstants` | Time-related constants |
| `NotificationConstants` | Channel IDs |
| `ServiceConstants` | Service action strings |
| `DataStoreConstants` | DataStore preference keys |
| `JoystickConstants` | Joystick sizing/sensitivity |
| `WidgetConstants` | Widget sizing |
| `RouteConstants` | Route-related defaults |
| `DatabaseConstants` | DB name, version |
| `AppInfo` | GitHub repo URL, issues URL, privacy policy URL |
| `FollowerRestorationConstants` | Follower boot restoration retry delay, max delay, max attempts, jitter range |

## Rules

- No new top-level/companion constant outside `AppConstants`. Add there.
- Modules needing constants: declare `implementation(project(":core:common"))` in `build.gradle.kts`.
- Exception: `:core:model` is pure JVM, cannot depend on `core:common`. Constants only used in `:core:model` stay there.