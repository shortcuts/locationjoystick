# Export / Import

Settings → export all data to JSON, import from a previous export.

Key files: `:feature:settings:impl/SettingsScreen.kt`, `:core:data/SettingsRepository.kt`

## Scope

Covers: routes, favorites, speed profiles, widget/map feature config + shared display order, roaming defaults, jitter settings, hot locations state, hot routes state, sort preferences.

`AppSettings.featureOrder`/`enabledWidgetFeatures`/`enabledMapFeatures` (`AppFeature` enum) round-trip through `enabledWidgetFeatures`/`enabledMapFeatures`/`featureOrder` JSON arrays. Old exports from before the `WidgetFeature`/`MapFabFeature` merge still import correctly — `SettingsExportCodec` aliases the legacy `ROUTES_FLOATING`/`FAVORITES_FLOATING` names to `AppFeature.ROUTES`/`AppFeature.FAVORITES`, and missing `enabledMapFeatures`/`featureOrder` fields fall back to defaults (`DEFAULT_MAP_ENABLED` includes `PASTE_COORDINATES` and `CAPTURE_COORDINATES`; `DEFAULT_WIDGET_ENABLED` includes `PASTE_COORDINATES` and `ROAMING`). An export that already lists `enabledMapFeatures` / `enabledWidgetFeatures` without those newer FABs keeps that set as stored (import writes `map_fab_seen_defaults` / `widget_seen_defaults` so the on-device upgrade merge does not add them back). On-device upgrades that never imported such a file still gain new default-on FABs via `mergeNewDefaultMapFeatures` / `mergeNewDefaultWidgetFeatures`. Captured-coordinate lists and the capture on/off toggle are **not** part of `ExportData`.

`roamingDefaults` JSON includes `kind`, `plantingStartRadiusMeters`, `plantingEndRadiusMeters`, `plantingInfiniteLoops`, `plantingLoopCount`, and `plantingSpeedProfileId`. Old exports without those keys import as Walk around the block with planting defaults (5 m / 39 m, infinite loops, Bike speed).

Each entry in `favoriteLocations` includes the optional `category` field (`FavoriteLocation.category`). Old exports without it import cleanly — a missing or `null` `category` defaults to `null`.

Each entry in `routes` includes the optional `speedProfileId` field (`Route.speedProfileId`), round-tripping the same way `FavoriteLocation.category` does. Old exports without it import cleanly — a missing or `null` `speedProfileId` defaults to `null`.

Each entry in `routes` also includes `randomizeTeleportOrder` (`Route.randomizeTeleportOrder`). Old exports without it import cleanly — a missing field defaults to `false`.

Each waypoint in a route includes `waitSeconds` (`Waypoint.waitSeconds`, used by teleport routes — see @docs/features/routes.md, "Teleport Routes"). Old exports without it import cleanly — a missing `waitSeconds` defaults to `0`.
`routesSortMode` and `favoritesSortMode` preserve the four list sort choices. Older exports with
only `routesSortNewestFirst` / `favoritesSortNewestFirst` migrate to **Newest saved** or
**Oldest saved**. The legacy booleans remain in new exports for backward compatibility.

Schema version: `AppConstants.ExportConstants.SCHEMA_VERSION`.

## Settings Screen Entry Point

All export/import/reset actions live behind a single "More actions" overflow menu
(`LjOverflowMenu`, `:core:designsystem/LjTopBar.kt`) in the top bar of `SettingsHubScreen`,
grouped into three labeled sections (`LjOverflowMenuSectionLabel`): **Export**, **Import**,
**Danger**. There are no standalone Export/Import icon buttons.

## Export Flow

Overflow menu → Export section → "Export settings":

1. Serialize via `kotlinx.serialization`.
2. Write to `getExternalFilesDir(null)`.
3. Share via `FileProvider` + `Intent.ACTION_SEND`.

"Export via QR code" (same section) instead starts the local QR transfer server — see
@docs/features/qr-transfer.md.

## Import Flow

Overflow menu → Import section → "Import from file":

1. File picker (`OpenDocument`, MIME `AppConstants.ExportConstants.MIME_TYPE`).
2. Parse + validate `schemaVersion` is between 1 and `AppConstants.ExportConstants.SCHEMA_VERSION`, inclusive.
3. Confirm "replace all data?".
4. Clear Room + DataStore.
5. Insert new data.

All I/O runs on `Dispatchers.IO`.

"Import from QR code" and "Import via code" (same section) instead fetch the export over the
local network — see @docs/features/qr-transfer.md.

## GPX Import (Routes only)

Routes can be imported from GPX files via the Routes screen overflow menu → "Import GPX".

- File picker with `application/gpx+xml` MIME type.
- Max file size: `AppConstants.ExportConstants.MAX_GPX_IMPORT_SIZE_BYTES` (10 MB).
- A GPX file may describe multiple routes — every `<trk>` and `<rte>` element is imported as its own separate `RouteType.STRAIGHT` route, named from that element's own `<name>` child (falling back to "Imported Route"/"Imported Route N" when absent). Elements are never merged together, even if unnamed.
- If a file has no `<trk>`/`<rte>` elements, bare top-level `<wpt>` points are imported as a single "Imported Route".
- A file with no `<trk>`/`<rte>`/`<wpt>` points at all shows an "Import failed" error rather than creating an empty route.

Key function: `parseGpxRoutes` in `:core:common/util/GpxRoutes.kt` (used by Routes → Import GPX and by opening a GPX from another app).

Opening a `.gpx` from Files / Downloads / Share / a chat app uses the same parser, then the map paste-coordinates sheet (`GpxOpenRepository`) instead of saving every track immediately. See @docs/features/routes.md.

## Third-Party Imports

Settings screen → "More actions" overflow menu → Import section offers:

- **Import from GPS Joystick** — imports routes and favorites from a GPS Joystick export (its Realm database, parsed structurally by `GpsJoystickMigrator`). Keeps the original list order.
  GPX exports are also supported: named `<wpt>` points become favorites and each track or route
  remains separate. Routes over the GPX waypoint limit are skipped with a reported count.
- **Import from YAMLA** — imports routes from YAMLA JSON format.

All imported routes are saved as `RouteType.STRAIGHT` segments.

## Reset All Data

Settings screen → "More actions" overflow menu → Danger section → "Reset all data" → confirmation dialog → clears everything without needing Android's system-level "Clear data & cache".

- Clears all routes (`RouteRepository.deleteAllRoutes()`) and all favorites (`FavoriteRepository.deleteAllFavorites()`).
- Clears all DataStore preferences via `SettingsRepository.resetAllData()` → `PreferencesDataSource.clearAllExceptOnboarding()`.
- **Preserves `ONBOARDING_COMPLETE`** — the user doesn't need to redo permission grants, just their data.
- Irreversible; the confirmation dialog states "All favorites, routes, and settings will be permanently deleted."

Key files: `:feature:settings:impl/SettingsScreen.kt` (`ResetAllDataConfirmDialog`), `:feature:settings:impl/SettingsViewModel.kt` (`resetAllData()`), `:core:data/SettingsRepository.kt` (`resetAllData()`), `:core:datastore/AppPreferencesDataSource.kt` (`clearAllExceptOnboarding()`).

## Edge Cases

- Malformed JSON → show "Invalid file" error.
- Missing fields → use `@SerialName` defaults.
- Skip confirmation on fresh install (empty DB).
- GPX file exceeds max size → show "File too large" error.
- Invalid third-party format → show "Invalid file" error.
