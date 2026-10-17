# Map (MapLibre)

Main screen. A raster base map (OpenStreetMap by default) that is the centre of most actions. The app opens on the map while spoofing is running or paused, and on Home otherwise (see [architecture.md](../architecture.md), "Navigation").

Key files: the map feature module (screen, view model, speed strip).

## Library

MapLibre Android SDK. Not osmdroid, not Google Maps.

- Raster tiles come from the selected map source (below), applied as a style. A low-zoom preview layer sits under the full-detail one, so the map paints something before the detailed tiles arrive.
- OSM tile HTTP has a required recipe. Skipping any part blanks or slows the map. **Read [map-tiles.md](map-tiles.md) before changing maps.**
- The location marker updates its coordinates in place. Removing and re-adding it causes flicker and loses state.
- Route lines and the jitter overlay are GeoJSON layers. The jitter overlay is drawn in metres, not screen pixels, because a pixel-radius circle would not match the real jitter.
- Offline tile download uses the library's region download.

## Map source

Settings picks the raster provider for every map surface: the main map, the floating widget map, the favorites picker, the route creator and the paste-coordinates screen. Each surface re-applies its style when the setting changes, keeping the camera on the same real-world point.

Two sources exist, and the code holds their tile URLs, zoom ranges and default centres:

- **OSM** (default): the standard tile service, WGS-84.
- **AMap:** a community raster endpoint with no key. It is faster from mainland China, where OSM tiles are throttled. Its coverage is mainland China only, and it uses a different datum.

No custom-URL option, by design. The setting stays a simple choice, and the app does not ship a free-form network field.

Only AMap clamps the camera to its served zoom range. Without the clamp it shows blank tiles when zoomed past its range. OSM keeps the library's default range, since its tiles overzoom.

### GCJ-02 boundary

AMap uses GCJ-02, an obfuscated datum. Drawing WGS-84 data on it is off by a few hundred metres, so conversion is needed.

Rules that must hold:

- Conversion happens only at the map's presentation edge, in both directions. Values going to the map are converted; values read back from it are converted to WGS-84.
- Everything else stays WGS-84: settings, favorites, routes, the mock location, the geocoders, the routing service and exports.
- Never store or spoof a GCJ-02 coordinate. A stored value would be wrong for every other map source.

Map callbacks read the latest source, so switching in Settings applies to every open map without recreating it.

### Attribution

The library's own attribution button is hidden on every map. Each surface shows its own attribution line in the bottom-left corner. The OSM credit is required by the OSM tile usage policy, so it stays visible on the default source. The attribution line also tells the user which datum the map uses.

## Navigation

- The hamburger opens the navigation drawer. The drawer is owned by the app shell, not by the map.
- Start and stop spoofing is controlled only from the top bar toggle. There is no separate start or stop button on the map.

## Interactions

- Long-press opens a sheet with walk and teleport choices.
- Tapping a route point selects it. In route edit mode, tapping empty map adds a waypoint.
- Camera follow stops when the user drags the map. The centre button is always available.
- While spoofing runs or is paused, the centre button follows the mock position. While idle, it jumps to the marker, or to the last point the camera was moved to when there is no marker. It never asks the device for a GPS fix: a fix request lags the button and stacks up on repeated taps.
- Re-centring is a read-only camera move. It does not change the remembered mock position or the teleport cooldown.
- Short camera moves animate. Long jumps use an instant move, so the map does not fetch tiles along a flyover path, which would leave the destination blank for many seconds.
- Favorites use street-level zoom.

## Floating action buttons

- The share icon sits under the centre button. It is not part of the configurable set ([location-links.md](location-links.md)).
- Every button is neutral when idle. Walk, routes and roaming use the activity colour rule: green while moving, orange while paused.
- Routes and roaming buttons stay visible while their session runs, even if the user turned them off. A running session must always be controllable.
- Paste coordinates is off by default. It is a bottom sheet that accepts decimal or degree-minute-second text. Lines with prose are ignored whole, so a note with numbers in it is not misread as coordinates.
- With one valid point, the sheet offers teleport, walk and walk via roads, and the buttons act at once. With several points, the single-point actions are replaced by route actions. This stops input from silently acting on only the first point.
- Invalid text does nothing. Saving a favorite from several points saves the first, and the label says so.

The button order and visibility rules are in [widget.md](widget.md), "Configurability".

## Speed strip

A small strip at the top left shows the current speed while spoofing runs or is paused. It is hidden while search is open. Units follow the settings. It reads zero when paused or stopped.

## Search

Place search goes through a process-wide geocoder that tries providers in order, with a shared cooldown after a provider fails. Providers that the user has unchecked in settings are never called. The UI refuses to uncheck the last one, since a search with no provider would be useless.

Results are cached briefly. Requests are spaced to respect the public geocoder's usage limit, because a burst gets the app blocked. The reverse lookup for place names uses the same chain.

## Jitter radius overlay

With debug stats on, a translucent circle shows the jitter radius in effect around the spoofed position. It lets the user match the jitter settings to what the map shows, instead of guessing from a number. It is drawn in metres, and it appears only on the main map, not the floating map.

## Lifecycle

- Forward every lifecycle event to the map view.
- Never call the map library before the map reports ready.
