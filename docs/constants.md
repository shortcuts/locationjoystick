The error is clear: the second `:core:model` inline code was replaced by "there" in the last bullet. I'll restore it.

# Constants

All constants → `:core:common/constants/AppConstants.kt`.

## Nested Objects

| Object | Contents |
|--------|----------|
| `LocationConstants` | Update interval, earth radius, walk threshold, RDP epsilon |
| `ProfileConstants` | Walk/Run/Bike speed presets, min/max speed, anti-cheat threshold |
| `JitterConstants` | Accuracy min/max, jitter radii |
| `RealismConstants` | Altitude sigma/drift/clamp, warmup duration, satellite interval, suspended push/pause durations |
| `RoamingConstants` | OSRM profile, overview, geometries settings |
| `OsrmConstants` | Base URL, endpoint paths |
| `MapConstants` | Default lat/lon |
| `NominatimConstants` | Search endpoint |
| `ExportConstants` | Schema version, MIME type |
| `NotificationConstants` | Channel IDs |
| `ServiceConstants` | Service action strings |
| `DataStoreConstants` | DataStore preference keys |
| `JoystickConstants` | Joystick sizing/sensitivity |
| `WidgetConstants` | Widget sizing |
| `RouteConstants` | Route-related defaults |
| `DatabaseConstants` | DB name, version |
| `AppInfo` | Version name/code |

## Rules

- No new top-level/companion constant outside `AppConstants`. Add there.
- Modules needing constants: declare `implementation(project(":core:common"))` in `build.gradle.kts`.
- Exception: `:core:model` is pure JVM, cannot depend on `core:common`. Constants only used in `:core:model` stay there.