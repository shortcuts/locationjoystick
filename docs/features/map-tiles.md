# Map tiles (OSM HTTP)

OpenStreetMap raster tiles on every MapLibre surface. Blank/black/slow maps
are almost always this stack, not a Compose or overlay bug. Follow this
recipe whenever you touch MapLibre, OkHttp, R8, or a new `MapView`.

Key files: `:core:map/MapTileHttp.kt`, `:core:map/MapViews.kt`, `:core:map/MapCamera.kt`, `:core:map/MapLibreStyleExt.kt`.

Details of map UX (FABs, paste, jitter overlay) stay in @docs/features/map.md.
Overlay compositing is also noted in @docs/features/widget.md.

## Required recipe

These must all stay true. Breaking any one of them blanks or crawls the map.

1. **`MapTileHttp.install(context)` is the only `MapLibre.getInstance` path.**
   `LjApplication.onCreate` calls it. Every `MapView` is created by
   `createMapView` / `rememberMapView` (`:core:map`), which call `install`
   first. Never `MapLibre.getInstance(...)` then `MapView(context)` at a
   call site.
2. **Identifying User-Agent on every tile request.** MapLibre 13's
   `HttpRequestImpl.executeRequest` does `addHeader("User-Agent", userAgentString)`
   on each request, overwriting OkHttp defaults. Generic values (`okhttp/…`,
   `Dalvik/…`, empty) make OSM return a **blocked placeholder** (HTTP 200,
   `x-blocked`, ~6987 B PNG). A real tile is ~11 KB. Interceptors must
   **replace** the header with `.header("User-Agent", …)` (not `addHeader`)
   and set `Referer` to the docs URL. Use both an application interceptor
   **and** a network interceptor. Target UA:
   `locationjoystick/<version> (+<docs url>; Android <release>)`
   (`mapTileUserAgent()`).
3. **Overwrite `HttpRequestImpl` statics.** After `setOkHttpClient`, write
   `client` and `userAgentString` on `HttpRequestImpl` so R8 cannot leave
   executeRequest talking to `DEFAULT_CLIENT`.
4. **One OkHttp client per process, 20 requests per host.**
   `OSM_MAX_REQUESTS_PER_HOST = 20` matches MapLibre's dispatcher (native
   `http_file_source` cap). OkHttp's default is **5** — a cold cache then
   sits on the empty-style canvas for a long time. `install` must not
   `Builder().build()` again on every `MapView` (leaks connection pools,
   cold TLS).
5. **R8 keep rules** in `app/proguard-rules.pro`:
   `HttpRequestUtil`, `HttpRequestImpl` (including `userAgentString` and
   `client`), and `com.locationjoystick.core.map.maplibre.**`.
6. **One-shot native cache wipe after a UA fix.** OSM's blocked PNG is HTTP
   200, so MapLibre caches it. Bump `OSM_TILE_CACHE_BUST_MARKER` (currently
   `osm_ua_cache_bust_4`) so `purgePoisonedTileCacheIfNeeded` deletes
   `mbgl*` / `mapbox*` files in filesDir / cacheDir / externalFilesDir
   once. Marker lives in `cacheDir` — Android "Clear cache" removes it and
   the next launch wipes/rebuilds again (expected).
7. **Overlay maps use TextureView.** `rememberMapView(overlay = true)` sets
   `textureMode`. A default GLSurfaceView in `TYPE_APPLICATION_OVERLAY`
   punches a transparent hole through to the app underneath (location dot
   and FABs visible, no streets).
8. **Empty style has a background layer.** `asset://empty.json` lives in
   `:core:map` assets (not `:feature:map:impl`). A light background means
   "tiles not here yet", not a black void.

Tests: `MapTileUserAgentTest` — UA string, interceptor replaces `okhttp`
and empty UA + sets Referer, dispatcher is 20/host, cache-file name matcher.

## Symptoms

| What you see | Likely cause | What to do |
|---|---|---|
| Black map, location dot/FABs still draw | OSM blocked placeholder and/or cached blocked tiles | Confirm outgoing `User-Agent`; bump `OSM_TILE_CACHE_BUST_MARKER`; force-close once |
| Light/white canvas for a long time, then streets | `maxRequestsPerHost` fell back to 5, client rebuilt per MapView, a genuine cold cache after wipe/clear, **or** `animateCamera` across a long teleport (path-tile flood) / street-level zoom waiting on slow z18 tiles | Keep 20/host and a process-singleton client. Cold cache after clear storage is expected once. Long jumps must `moveCamera` (see "Teleport / long camera jumps") |
| Transparent hole in the floating map; the game shows through | GLSurfaceView in an overlay window | `rememberMapView(overlay = true)` |
| Map never loads, no internet | First-time download | Need network once per area; then native cache serves offline |

A 200 status is **not** proof tiles are real. Compare body size / `x-blocked`.

## Teleport / long camera jumps

A 500 ms `animateCamera` across a large distance makes MapLibre request tiles along the
interpolated path, so the destination stays on the empty-style canvas until those finish.
Street-level favorite zoom (18) can then wait on slower high-detail tiles.

Do:

- Snap with `moveCamera` when displacement ≥ `SNAP_CAMERA_DISTANCE_METERS` (`followOrSnapTo`
  in `:core:map`). Keep the 500 ms animation only for walking-scale follow.
- Keep a low-zoom preview raster (`OSM_PREVIEW_MAX_ZOOM` 12) **under** the detail layer so a
  few z12 tiles can paint immediately; z15–19 fill in on top. Same OSM URL, process-singleton
  OkHttp client, and identifying UA — do not build a second client.
- Overlay maps pass `PANEL_OSM_PREVIEW` source/layer IDs so they do not collide with the
  main map.

Do **not** bump `OSM_TILE_CACHE_BUST_MARKER` for this. That marker is only for UA / blocked-PNG
cache wipes.

## Do not

- Remove `MapTileHttp.install()` from `LjApplication.onCreate`
- Construct `MapView` without `createMapView` / `rememberMapView`
- Set User-Agent only as an OkHttp default header — MapLibre overwrites it
- Use interceptor `addHeader` instead of `header()` (must replace)
- Drop the R8 `-keep` rules listed above
- Skip the cache-bust marker bump after shipping a UA fix
- Build the tile client without `OSM_MAX_REQUESTS_PER_HOST = 20`
- Rebuild that client on every `MapView`
- Point overlay maps at the default GL surface
- Animate the camera across a teleport-scale jump (`followOrSnapTo` / `moveCamera` instead)

## Key files

| Piece | Where |
|---|---|
| Install + interceptor + cache wipe + 20/host client | `:core:map` `MapTileHttp.kt` |
| `MapView` factory | `:core:map` `MapViews.kt` (`createMapView` / `rememberMapView`) |
| Camera snap vs animate on long jumps | `:core:map` `MapCamera.kt` |
| Preview + detail OSM rasters | `:core:map` `MapLibreStyleExt.kt` |
| App startup | `LjApplication.onCreate` |
| Empty style | `core/map/src/main/assets/empty.json` |
| OSM URL / UA app name / cache marker / 20/host / preview zoom / snap distance | `AppConstants.MapConstants` |
| R8 | `app/proguard-rules.pro` |
| Tests | `MapTileUserAgentTest`, `MapCameraTest` |
