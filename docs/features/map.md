# Map (MapLibre)

Main screen. OSM centered on `AppConstants.MapConstants.DEFAULT_LAT` / `AppConstants.MapConstants.DEFAULT_LON` first load. Scroll on by default.

Key files: `:feature:map:impl/MapScreen.kt`, `:feature:map:impl/MapViewModel.kt`

## Library

MapLibre Android SDK 12.x. Not osmdroid, not Google Maps.

- OSM tile source via `RasterSource`.
- Location marker: `SymbolLayer` backed by GeoJSON. Update coords — no remove/re-add.
- Route polylines: `LineLayer` backed by GeoJSON `FeatureCollection`.
- Jitter radius overlay: `FillLayer` + `LineLayer` backed by a real-world-meters GeoJSON polygon (not `CircleLayer` — its radius is screen pixels, not meters).
- Offline tiles via `OfflineManager.downloadRegion()`.

## Navigation

- TopAppBar hamburger opens nav drawer via `onOpenDrawer: () -> Unit`. Drawer owned by `LjApp`, not `LjNavHost`.
- Start/stop spoofing is controlled solely from the top bar's `LjScaffold`/`LjTopBar` toggle (see @docs/features/mock-location.md, "Global Start/Stop Control") — there is no separate start/stop FAB on the map screen.

## Interactions

- Long-press → bottom sheet with "Walk here" / "Teleport here".
- Tap route point → select.
- Tap empty map in edit mode → add waypoint.
- Camera follow: disabled on `REASON_API_GESTURE`. Re-enabled via re-center FAB.

## Configurable FABs

`MapFabColumn` renders Favorites/Routes/Roaming/Search in the shared `AppFeature` order (see @docs/features/widget.md, "Configurability"), filtered to features enabled for the `MAP` surface — configured in Settings → Menus → "App Features". Routes and Roaming also force-show while actively in progress, even if toggled off, so the user can still control a running session.

## Jitter Radius Overlay

When Settings → Menus → Debug → "Debug stats" (`AppSettings.debugStatsEnabled`)
is on, a translucent circle is drawn on the map centered on the current
spoofed position, radius = the position-jitter radius currently in effect
(idle or moving, whichever applies this tick — see @docs/features/mock-location.md,
"Position jitter"). Lets the user visually calibrate Settings → Location
Randomness jitter values against the map instead of guessing from a raw
meters number. Rendered as a real-world-meters GeoJSON polygon
(`buildCirclePolygonGeoJson`, `:core:map`) via `FillLayer` + `LineLayer` —
not `CircleLayer`, whose radius is always screen pixels, not meters. Main
map screen only, not the floating map.

## Lifecycle

- Forward all lifecycle events to `MapView`.
- Never call MapLibre APIs before `onMapReady`.