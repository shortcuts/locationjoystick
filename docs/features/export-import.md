# Export / Import

Settings → export all data to JSON, import from a previous export.

Key files: `:feature:settings:impl/SettingsScreen.kt`, `:core:data/SettingsRepository.kt`

## Scope

Covers: routes, favorites, speed profiles, widget config, roaming defaults, jitter settings, hot locations state, hot routes state, sort preferences.

Schema version: `AppConstants.ExportConstants.SCHEMA_VERSION`.

## Export Flow

1. Serialize via `kotlinx.serialization`.
2. Write to `getExternalFilesDir(null)`.
3. Share via `FileProvider` + `Intent.ACTION_SEND`.

## Import Flow

1. File picker (`OpenDocument`, MIME `AppConstants.ExportConstants.MIME_TYPE`).
2. Parse + validate `schemaVersion == AppConstants.ExportConstants.SCHEMA_VERSION`.
3. Confirm "replace all data?".
4. Clear Room + DataStore.
5. Insert new data.

All I/O runs on `Dispatchers.IO`.

## GPX Import (Routes only)

Routes can be imported from GPX files via the Routes screen overflow menu → "Import GPX".

- File picker with `application/gpx+xml` MIME type.
- Max file size: `AppConstants.ExportConstants.MAX_GPX_IMPORT_SIZE_BYTES` (10 MB).
- A GPX file may describe multiple routes — every `<trk>` and `<rte>` element is imported as its own separate `RouteType.STRAIGHT` route, named from that element's own `<name>` child (falling back to "Imported Route"/"Imported Route N" when absent). Elements are never merged together, even if unnamed.
- A file with no `<trk>`/`<rte>` waypoints shows an "Import failed" error rather than creating an empty route.

Key function: `parseGpxRoutes` in `:feature:routes:impl/RoutesViewModel.kt`.

## Third-Party Imports

Settings screen → Import icon → dropdown menu offers:

- **Import from GPS Joystick** — imports routes from GPS Joystick app format.
- **Import from YAMLA** — imports routes from YAMLA JSON format.

All imported routes are saved as `RouteType.STRAIGHT` segments.

## Edge Cases

- Malformed JSON → show "Invalid file" error.
- Missing fields → use `@SerialName` defaults.
- Skip confirmation on fresh install (empty DB).
- GPX file exceeds max size → show "File too large" error.
- Invalid third-party format → show "Invalid file" error.
