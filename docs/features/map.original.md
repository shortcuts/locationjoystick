# Map (MapLibre)

Main screen. Shows OpenStreetMap centered on `AppConstants.MapConstants.DEFAULT_LAT` / `AppConstants.MapConstants.DEFAULT_LON` on first load. Scroll gestures enabled by default.

Key files: `:feature:map:impl/MapScreen.kt`, `:feature:map:impl/MapViewModel.kt`

## Library

MapLibre Android SDK 12.x. Not osmdroid, not Google Maps.

- OSM tile source via `RasterSource`.
- Location marker: `SymbolLayer` backed by GeoJSON. Update coords — do not remove/re-add the layer.
- Route polylines: `LineLayer` backed by GeoJSON `FeatureCollection`.
- Offline tiles via `OfflineManager.downloadRegion()`.

## Navigation

- TopAppBar with hamburger icon opens the nav drawer via `onOpenDrawer: () -> Unit` lambda. The drawer is owned by `LjApp`, not `LjNavHost`.

## Interactions

- Long-press → bottom sheet with "Walk here" / "Teleport here".
- Tap route point → select.
- Tap empty map in edit mode → add waypoint.
- Camera follow: disabled on `REASON_API_GESTURE`. Re-enabled via re-center FAB.

## Lifecycle

- Forward all lifecycle events to `MapView`.
- Never call MapLibre APIs before `onMapReady`.
