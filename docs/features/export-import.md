# Export / Import

Settings → export all data to JSON, import from a previous export.

Key files: `:feature:settings:impl/SettingsScreen.kt`, `:core:data/SettingsRepository.kt`

## Scope

Covers: routes, favorites, speed profiles, widget/map feature config + shared display order, roaming defaults, jitter settings, hot locations state, hot routes state, sort preferences.

`AppSettings.featureOrder`/`enabledWidgetFeatures`/`enabledMapFeatures` (`AppFeature` enum) round-trip through `enabledWidgetFeatures`/`enabledMapFeatures`/`featureOrder` JSON arrays. Old exports from before the `WidgetFeature`/`MapFabFeature` merge still import correctly — `SettingsExportCodec` aliases the legacy `ROUTES_FLOATING`/`FAVORITES_FLOATING` names to `AppFeature.ROUTES`/`AppFeature.FAVORITES`, and missing `enabledMapFeatures`/`featureOrder` fields fall back to defaults.

Each entry in `favoriteLocations` includes the optional `category` field (`FavoriteLocation.category`). Old exports without it import cleanly — a missing or `null` `category` defaults to `null`.

Each entry in `routes` includes the optional `speedProfileId` field (`Route.speedProfileId`), round-tripping the same way `FavoriteLocation.category` does. Old exports without it import cleanly — a missing or `null` `speedProfileId` defaults to `null`.

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
- If a file has no `<trk>`/`<rte>` elements, bare top-level `<wpt>` points (some generators, e.g. pokedex100, emit these with no track/route wrapper) are imported as a single "Imported Route".
- A file with no `<trk>`/`<rte>`/`<wpt>` points at all shows an "Import failed" error rather than creating an empty route.

Key function: `parseGpxRoutes` in `:feature:routes:impl/RoutesViewModel.kt`.

## Third-Party Imports

Settings screen → "More actions" overflow menu → Import section offers:

- **Import from GPS Joystick** — imports routes from GPS Joystick app format.
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
