# Map tiles (OSM HTTP)

OpenStreetMap raster tiles on every MapLibre surface. Blank, black or slow maps are almost always this stack, not a Compose or overlay bug. Follow this recipe whenever you touch MapLibre, the tile HTTP client, R8, or a new map view.

Key files: the map module's tile HTTP setup, map view factory, camera helpers, and style extensions. Constants (tile URL, user-agent app name, cache marker, per-host limit, cache cap, preview zoom, snap distance) are in `AppConstants.MapConstants`.

Map UX (buttons, paste, jitter overlay) stays in [map.md](map.md). Overlay compositing is also noted in [widget.md](widget.md).

## Required recipe

Each item guards a failure we have hit. Breaking one blanks or slows the map.

1. **One install path.** The map library is initialised only by the tile HTTP install, which the app calls at startup. Every map view is created through the shared factories, which call install first. A call site must never initialise the library and build a view on its own.
2. **Identifying User-Agent on every tile request.** The map library overwrites the HTTP client's default User-Agent with its own on each request. Generic values make the tile server return a blocked placeholder with HTTP 200. Status 200 is not proof of a real tile. The app must replace the User-Agent with its own identifier and a referrer, using both an application and a network interceptor. The rewrite is scoped to the OSM tile host only. Other providers keep the library's own headers. Replace with `header(...)`, never `addHeader(...)`.
3. **Overwrite the library's static client.** After the app installs its client, it must also write the client and user-agent into the library's request class. R8 can otherwise leave the library on its default client.
4. **One client per process, with a raised per-host limit.** The limit must match the library's native dispatcher limit. The HTTP client's default is much lower, and a cold cache then sits blank for a long time. The client is built once. Building one per map view leaks connection pools and repeats TLS handshakes.
5. **R8 keep rules** for the request classes and the map package. They live in the app's proguard file. Without them, release builds break the tile client while debug builds work.
6. **One-shot native cache wipe after a user-agent fix.** Blocked placeholders are cached by the native layer like real tiles. Bumping the cache marker constant makes the app delete cached tile files once. The marker lives in the app's cache directory, so a user clearing the cache triggers the wipe again on the next launch. That is expected.
7. **Overlay maps use a texture view.** A default surface view inside an overlay window punches a transparent hole through to the app underneath: the location dot and buttons show, the streets do not. Overlay maps must be created with the overlay flag.
8. **The empty style has a background layer.** It lives in the map module's assets. A light background means "tiles not here yet". Without it, the map shows a black void, which looks like a crash.
9. **Ambient cache cap is set on every launch.** The native cache cap is process-local, so it must be set in install each time, after the library is initialised and the purge has run. There is no bulk tile download, because the OSM usage policy forbids it. Do not bump the cache marker for this change.

Tests cover the user-agent string, the interceptor's replacement of a generic agent and its referrer, the per-host limit, and the cache-file name matcher.

## Symptoms

| What you see | Likely cause | What to do |
|---|---|---|
| Black map, location dot and buttons still draw | Blocked placeholder, or cached blocked tiles | Check the outgoing User-Agent. Bump the cache marker. Force-close once. |
| Light canvas for a long time, then streets | Per-host limit fell back to the default, client rebuilt per view, a cold cache after a clear, or a long camera animation | Keep the raised limit and one process-wide client. A cold cache after clear storage is expected once. Long jumps must snap (below). |
| Transparent hole in the floating map; the game shows through | Default surface view in an overlay window | Create the map with the overlay flag. |
| Map never loads | First-time download without network | The map needs network once per area. After that the native cache serves it offline. |

A 200 status is not proof that tiles are real. Compare the body size and the blocked-marker header.

## Teleport and long camera jumps

A camera animation across a large distance makes the library request every tile along the path. The destination then stays blank until those finish. Zoomed-in tiles are the slowest.

- Snap with an instant move when the displacement reaches the snap distance. The helper that chooses between snap and animate is in the map module. Keep the animation only for walking-scale follow.
- A low-zoom preview raster sits under the detail layer, so a few coarse tiles paint at once while the fine ones load. It uses the same URL, the same process-wide client and the same user-agent. Do not build a second client for it.
- Overlay maps use their own preview source and layer ids, so they do not collide with the main map.

Do not bump the cache marker for camera changes. The marker is only for user-agent and blocked-placeholder wipes.

## Do not

- Remove the tile install from application startup.
- Apply the OSM user-agent or referrer to any other host.
- Create a map view without the shared factories.
- Set the user-agent only as a client default header. The library overwrites it.
- Use `addHeader` in the interceptors. It must replace.
- Drop the R8 keep rules.
- Ship a user-agent change without bumping the cache marker.
- Lower the per-host limit, or build the client per view.
- Put an overlay map on the default surface view.
- Animate the camera across a teleport-scale jump.
