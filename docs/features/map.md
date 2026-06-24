# Map (MapLibre)

Main screen. OSM centered on `AppConstants.MapConstants.DEFAULT_LAT` / `AppConstants.MapConstants.DEFAULT_LON` first load. Scroll on by default.

Key files: `:feature:map:impl/MapScreen.kt`, `:feature:map:impl/MapViewModel.kt`

## Library

MapLibre Android SDK 12.x. Not osmdroid, not Google Maps.

- OSM tile source via `RasterSource`.
- Location marker: `SymbolLayer` backed by GeoJSON. Update coords — no remove/re-add.
- Route polylines: `LineLayer` backed by GeoJSON `FeatureCollection`.
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

## Lifecycle

- Forward all lifecycle events to `MapView`.
- Never call MapLibre APIs before `onMapReady`.