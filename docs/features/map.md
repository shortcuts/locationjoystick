# Map (MapLibre)

Main screen. OSM centered on `AppConstants.MapConstants.DEFAULT_LAT` / `AppConstants.MapConstants.DEFAULT_LON` first load. Scroll on by default.

Key files: `:feature:map:impl/MapScreen.kt`, `:feature:map:impl/MapViewModel.kt`

## Library

MapLibre Android SDK 13.2. Not osmdroid, not Google Maps.

- OSM tile source via `RasterSource` (`tile.openstreetmap.org`).
- OSM tile HTTP: **required recipe in @docs/features/map-tiles.md**. Skipping it blanks or crawls the map. Do not re-derive the rules here.
- Location marker: `SymbolLayer` backed by GeoJSON. Update coords — no remove/re-add.
- Route polylines: `LineLayer` backed by GeoJSON `FeatureCollection`.
- Jitter radius overlay: `FillLayer` + `LineLayer` backed by a real-world-meters GeoJSON polygon (not `CircleLayer` — its radius is screen pixels, not meters).
- Offline tiles via `OfflineManager.downloadRegion()`.

## OSM tiles

Required HTTP / MapView / R8 / overlay recipe: @docs/features/map-tiles.md.
Do not invent a second `MapLibre.getInstance` path. The empty style
(`asset://empty.json`) lives in `:core:map` assets.

## Navigation

- TopAppBar hamburger opens nav drawer via `onOpenDrawer: () -> Unit`. Drawer owned by `LjApp`, not `LjNavHost`.
- Start/stop spoofing is controlled solely from the top bar's `LjScaffold`/`LjTopBar` toggle (see @docs/features/mock-location.md, "Global Start/Stop Control") — there is no separate start/stop FAB on the map screen.

## Interactions

- Long-press → bottom sheet with "Walk here" / "Teleport here".
- Tap route point → select.
- Tap empty map in edit mode → add waypoint.
- Camera follow: disabled on `REASON_API_GESTURE`. The center FAB is always available. While
  spoofing is running or paused it preserves the existing behaviour and follows the cached mock
  position again. While spoofing is idle or in an error state it requests a fresh GPS fix from the
  phone (falling back to the last real GPS fix) and centers the camera there. This real-GPS camera
  move is deliberately read-only: it does not update `LocationRepository`, the remembered mock
  position, or teleport cooldown state, so no destructive confirmation is needed.
  Walking follow uses a short `animateCamera`. Teleport-scale jumps (`SNAP_CAMERA_DISTANCE_METERS`,
  2 km) and favorite/search/recenter jumps use `moveCamera` so MapLibre does not request tiles
  along a flyover path (that left the destination on the empty-style canvas for many seconds).
  Favorites still use street-level zoom (`FAVORITE_CAMERA_ZOOM` 18); a low-zoom OSM preview
  layer paints first (see @docs/features/map-tiles.md).

## Configurable FABs

`MapFabColumn` renders Favorites/Routes/Roaming/Search/Paste coordinates in the shared `AppFeature` order (see @docs/features/widget.md, "Configurability"), filtered to features enabled for the `MAP` surface — configured in Settings → Menus → "App Features". Routes and Roaming also force-show while actively in progress, even if toggled off, so the user can still control a running session.

Paste coordinates (`AppFeature.PASTE_COORDINATES`, WIDGET + MAP, on by default on both) opens a map overlay sheet. The user pastes comma-separated decimal-degree text (example `11.0127769, 79.48065`) or degree-minute-second text (example `37°34'11.4"N 127°00'17.9"E`); `PasteCoordinatesForm` calls `parsePastedCoordinates` (`:core:common`) once. Lines containing unrecognized prose are ignored as a whole, including any unrelated numbers on the line. With exactly one valid pair, Teleport / Walk / Walk via roads sit on one row and use that pair. With more than one pair, those single-location actions are replaced by clearly labelled route actions and **Start route** so input cannot silently act on only the first point. Invalid text shows "No valid coordinates" and does not move. The single-point movement buttons run `ConfirmTeleport` / `LongPressTapToWalk` / `WalkViaRoadsTo` and close the sheet — they do not pin through the confirm sheet. Teleport is hidden when `hideTeleportFeatures` is on. Save favorite is enabled at ≥1 point and intentionally writes the first point; for multi-point input it is labelled **Save first as favorite**. Save route is a separate action for ≥2 points, prompts for a name, and inserts the full sequence as a new UUID route. Loop / Planting / Reverse / Return to location / Follow roads / Teleport between waypoints plus Start route appear for multi-point input; Teleport between waypoints is omitted when `hideTeleportFeatures` is on (delay field included). Loop is checked by default. Start route upserts `paste_temp_route` and runs named `ROUTE_REPLAY` (@docs/features/routes.md, "Paste coordinates (map and widget)"). Deep links still pin via `observeDeepLinkCoords` → `pinCoordinateTarget` (confirm sheet); `applyPastedCoordinates` is that same pin path for tests and a future incoming-intent flow. The floating map FAB column exposes the same control, gated by `enabledMapFeatures`. Clipboard paste on the shared form appends a new line when the field already has text. The compact field has no redundant format helper text, is capped at 10 lines then scrolls, and keeps Select all and Clear all directly below it with 48 dp touch targets. The widget paste control opens a bottom sheet (`PasteCoordinatesFloatingView`) that can shrink so other apps stay usable (@docs/features/widget.md, "Paste coordinates overlay"). Existing installs that already persisted `enabledMapFeatures` / `enabledWidgetFeatures` without `PASTE_COORDINATES` still get it on upgrade (`mergeNewDefaultMapFeatures` / `mergeNewDefaultWidgetFeatures`); turning it off in Settings then sticks. While a route is playing, a `current/total` progress chip is pinned under that column (@docs/features/routes.md, "Route progress").

Capture coordinates is a top-level screen (Home + drawer), not a map FAB. Capture itself is off until toggled on that page. Intercept does not need the floating widget — @docs/features/capture-coordinates.md.

The map favorites and routes sheets (`FavoritesPickerSheet` / `RoutesPickerSheet`) embed
`FavoritesList` / `RoutesPickerList` with `enableSearch = true` — a name filter under the sheet
title. Query is `remember`d in the list, so dismissing the sheet starts from the full list.
See @docs/features/favorites.md and @docs/features/routes.md.

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
