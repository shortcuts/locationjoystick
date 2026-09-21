# Route System

Waypoints on map → polyline. Save, edit, replay, loop, record real-time.

Key files: `:feature:routes:impl/RoutesScreen.kt`, `:feature:routes:impl/RouteCreatorScreen.kt`, `:feature:routes:impl/PasteCoordinatesScreen.kt`, `:feature:routes:impl/RoutesViewModel.kt`, `:core:common/util/GpxRoutes.kt`, `:core:data/GpxOpenRepository.kt`, `:core:database/RouteDao.kt`, `:core:routing/RouteReplayEngine.kt`

## Route Types

- **STRAIGHT** (`RouteType.STRAIGHT`): straight segments, no network.
- **GUIDED** (`RouteType.GUIDED`): OSRM road-following. On fail → `osrmError = true` in `CreatorState`. No silent fallback.
- **TELEPORT** (`RouteType.TELEPORT`): instant jumps between waypoints, waiting at each — see "Teleport Routes" below.

## Storage

`RouteEntity` + `WaypointEntity` one-to-many. Waypoints: `routeId`, `lat`, `lon`, `orderIndex`. Query via `@Transaction @Query` → `Flow<RouteWithWaypoints>`.

Routes can also be imported from GPX files via the Routes screen add FAB → "Import GPX file". Max file size: 10 MB. Parsed and saved as `RouteType.STRAIGHT` routes. Individual routes with more than `AppConstants.ExportConstants.MAX_GPX_ROUTE_WAYPOINTS` (2000) points are skipped and the user is told how many were dropped.

Android's Open-with / Share sheet can send a `.gpx` file straight into the app (`ACTION_VIEW` / `ACTION_SEND`). Chat apps such as LINE often use `application/octet-stream` (sometimes `application/xml`) on a `content://` URI whose path has no `.gpx` (the filename is only in `OpenableColumns.DISPLAY_NAME`). The manifest registers GPX MIME types, `.gpx` path patterns, XML types, and `application/octet-stream` on `content`/`file` **without** `BROWSABLE` and without `VIEW text/plain` (that would steal maps URL shares). It does **not** register `*/*` or a scheme-only `content`/`file` filter: those match photos (`image/*`) and APKs (`application/vnd.android.package-archive`). `MainActivity` gates on `shouldTryOpenAsGpx` (any VIEW/SEND `content`/`file` URI), reads the URI (same 10 MB cap), sniffs `<gpx` via `looksLikeGpxContent`, then parses with `parseGpxRoutes` / `loadGpxForOpen` (`:core:common`). `GpxOpenRepository` delivers the first playable track to the map paste-coordinates sheet. The sheet title is **Open GPX**; points are pre-filled; **Save route** pre-fills the name from the GPX `<name>` or the original filename. A one-point file offers Teleport / Walk / Walk via roads; a multi-point file shows route actions and **Start route**. Multi-track files use the first track that is ≤ 2000 points (same skip rule as Import GPX). A parse/read failure or a non-GPX file shows "Couldn't open that GPX file".

Pending GPX is a `StateFlow` held until the user dismisses or uses the paste sheet (`consume()`), not when `MapViewModel` first collects it. `ON_STOP` redirects the map to Idle and tears down that ViewModel; consuming on collect let the dying collector wipe replay before the next map started, so a second open (or an open after using paste) showed the map with no Open GPX sheet. Swipe-dismiss is ignored while the activity is not at least `STARTED`, so sheet teardown on Idle redirect does not consume either. A new `id` on each `setPending` re-opens the same file.

The overflow menu on each saved route is **Edit**, **Share**, **Export**, **Delete**. Share opens a dialog with the route's waypoints as paste-format `lat, lon` lines, with Copy and Send as message. Add actions live on a FAB that opens an action sheet (**Draw on map**, **Draw on map (follow roads)**, **Paste coordinates**, **Import GPX file**).

## List search

A search field at the top of the Routes screen filters by route name (case-insensitive substring
via `Route.matchesSearch`). Hidden when there are no routes. No matches shows
"No routes match your search". Query is `remember` (not `rememberSaveable`).

The map routes sheet and widget routes panel use the same filter (`RoutesPickerList` /
`FloatingPickerShell`). Widget UI matches favorites: search icon next to Close, field shown on
tap, query cleared when the panel closes or search is hidden. The widget routes panel uses
`mapPanelLayoutParams()` so the search field can take IME focus. Its route detail also supports
Rename, Delete, and Share. Share sends the route's paste-format coordinate sequence and closes the
overlay before launching the system chooser so the chooser cannot be covered.

The Sort menu is shared by the main Routes screen and widget routes panel. It offers **A–Z**,
**Z–A**, **Newest saved**, and **Oldest saved**. The saved-time choices use `createdAt`, so they
preserve the order in which routes were added in either direction. The selected mode persists and
also controls route ordering on the map and widget surfaces.

## Paste coordinates

Routes + menu → **Paste coordinates**. Cleans messy pasted lat/lon text (numbered lists, emoji, typo'd dots, degree-minute-second with N/S E/W, optional swap lat/lon) with per-line coordinate recognition, then builds **one** saved route.

Leaving the app (switching to copy more coordinates from Maps, Notes, etc.) keeps this screen
until the user saves or goes back. `LjApp` otherwise redirects most destinations to Idle on
`ON_STOP` to unload MapLibre; paste coordinates is exempt, matching Settings (file pickers).

Key files: `:feature:routes:impl/PasteCoordinatesScreen.kt`, `:feature:routes:impl/PasteCoordinatesViewModel.kt`, `:core:common/PastedCoordinates.kt`, `:core:common/PlantingPath.kt`

### Cleanup

`parsePastedCoordinates` (`:core:common`) is a dedicated messy-text parser. It also accepts Google Maps-style DMS (`37°34'11.4"N 127°00'17.9"E`). Do not reuse `parseRawLatLng` here — that stays the single-pair search parser (decimal or one DMS pair).

Unrecognized prose lines are ignored as complete lines. ASCII words prevent the decimal fallback
from extracting unrelated numbers on that line, so headings and notes such as “route for some
windflowers: ends in 1 hour” do not become coordinates. Recognized DMS compass letters remain supported.

### Build modes

- **As-is** — cleaned points as a straight polyline. Saved as `RouteType.STRAIGHT`. Needs ≥2 points.
- **Walkable path** — OSRM foot routing between consecutive points (`OsrmClient.resolveRoute`). Dense geometry persisted. Saved as `RouteType.GUIDED`. Per-leg failure falls back to a straight segment and reports a summary via `RoutingErrorReporter.reportRoadFollowingFallbacks`.
- **Planting mode** — closed clockwise circle around each pasted point as the center (default `RouteConstants.PLANTING_DEFAULT_RADIUS_METERS` = 35 m, clamp 5–200 m). The center is not a waypoint. Vertex count follows ~8 m chord (`PLANTING_CHORD_METERS`), clamped 8–48. Circles stay geometric even when road-following is on. To circle stops on an already-saved route without rewriting waypoints, use the Planting checkbox on the start-route sheet instead (see Start Flow).

Point order: keep original, or **Optimize proximity** (`orderByProximity`, nearest-neighbor from min lat+lon). This reorders one route; it does not split into multiple routes.

### Planting travel

Between circles, chosen at build time:

- **Straight** — last vertex of one ring to the nearest rim vertex of the next. Saved `STRAIGHT`.
- **Via roads** — same endpoints, OSRM foot profile for the connector only. Saved `GUIDED`.

A single pasted point is valid in Planting mode (one closed circle). As-is and walkable still require two points.

## Paste coordinates (map and widget)

The map FAB, floating map, and widget paste box share `PasteCoordinatesForm` (`:core:designsystem`). They use the same `parsePastedCoordinates` parser as the Routes-menu builder. Clipboard paste appends a new line when the field is not empty (`mergeClipboardIntoPasteText`). The compact coordinates field has no redundant format helper text, grows up to 10 lines, then scrolls inside the field, with a fade at the top and bottom edges so overflow is obvious. **Select all** and **Clear all** sit directly below it with 48 dp touch targets. The widget paste overlay is a bottom sheet that can shrink to a bar so other apps stay usable (@docs/features/widget.md, "Paste coordinates overlay"). Do not change `PasteCoordinatesScreen` except to keep sharing that parser.

Layout:

1. **Exactly one valid point** — Teleport | Walk | Walk via roads appear in one tight row. Teleport is omitted when `hideTeleportFeatures` is on.
2. **More than one valid point** — the single-location movement row is removed, a “Route actions” count appears, and the primary playback button says **Start route**. Loop, Planting, Reverse, Return to location, Follow roads, and Teleport between waypoints use the same checkbox rules as `LjRouteStartOptions` (Loop default; Planting forces Loop and disables Return; Loop/Return exclusive). Teleport between waypoints is omitted when `hideTeleportFeatures` is on.
3. **Save favorite | Save route** stay separate. Save favorite intentionally uses the first valid point and says **Save first as favorite** for a route input. Save route requires ≥2 points and inserts the entire sequence as a new UUID via `RouteRepository.insertNamedPastedRoute`; it never writes `paste_temp_route` on this path.

**Play without saving:** Start route (≥2 points) upserts a reserved Room route then calls `StartRouteReplayUseCase` / `MapController.startPastedRouteReplay`:

- id: `AppConstants.RouteConstants.PASTE_TEMP_ROUTE_ID` (`paste_temp_route`) — must not use the `hot_route_` prefix or name matching
- name: `AppConstants.RouteConstants.PASTE_TEMP_ROUTE_NAME` (`Temp Route from Paste`)
- the next paste Start overwrites that id only
- a Save-route row (e.g. "Saved Route 1") gets a new UUID; later pastes must not overwrite it, even if its name equals the temp title

This is named `ROUTE_REPLAY` so Loop/Planting/Reverse/Return/Follow roads/Teleport between waypoints work. It is not ephemeral walk "add next point". Playing it takes the same movement priority as any other saved route. When `hideTeleportFeatures` is on, Start still walks to the first point.

The temp row appears in the Routes list until the next paste Start replaces it, or the user deletes it.

## Replay

- Interpolate waypoints at speed (m/s).
- Bearing: recomputed each tick from the previous to the current interpolated position
  via `calculateBearing` (`core/common/util/GeoUtils.kt`), published through
  `LocationRepository.currentBearing` and picked up by `MockLocationService` — covers
  route replay, ephemeral replay, the walk-to-start-of-route phase, and waypoint jumps.
- Advance: `speed * deltaTime`. `RouteInterpolator.interpolateAlongRoute()` consumes this whole
  per-tick budget across as many consecutive waypoints as it spans in one call (dense geometry,
  e.g. many closely spaced saved waypoints, can put several within a single tick's travel
  distance) rather than carrying leftover distance forward only one segment and dropping any
  remainder beyond it (issue #75).
- Snap at `AppConstants.LocationConstants.WALK_ARRIVAL_THRESHOLD_METERS`.
- Loop: smooth interpolation last→first waypoint.
- Pausing keeps pushing the frozen position to the mock location provider
  every tick (1 Hz) instead of stopping ticks entirely — otherwise the mock
  fix goes stale and some location consumers fall back to the device's real
  GPS position until the replay resumes.
- Playing route replay takes precedence over roaming and the joystick: starting
  roam is a no-op, and joystick *movement* is ignored (widget/map roam and joystick
  controls fade toward their background). Widget joystick show/hide and lock still
  open and lock the overlay while faded. Pause or stop the route first to start roaming (walk-around
  or planting). A paused route is stopped before roam starts so the two engines
  never write together. While a route is paused, the joystick can steer; resume
  still jumps to the next named stop via
  `jumpToNextWaypoint`, then continues interpolation from there.

### Route progress (`current/total`)

While replay is active (running or paused), a `current/total` chip is pinned
at the **bottom** of the map FAB column, the floating widget icon list, and
the floating-map FAB column. `current` is the 1-based last named stop
reached; `total` is the named-stop count (`boundaryIndices`, so Follow-roads
vertices and planting-circle points are not counted). Hidden when replay
stops. Implemented as `RouteProgress` on `LocationRepository.routeProgress`,
derived by `computeRouteProgress(resumeWaypointIndex, boundaryIndices)`.

### Per-Route Speed Profile

A route may pin a speed profile via the Route Detail (edit) screen — a dropdown (`ExposedDropdownMenuBox`) below the name field, showing "None" plus all 5 presets. Default: `null` ("None"), meaning replay starts at whatever speed profile is currently active globally. When a route pins a profile, replay starts at that profile's speed instead. Either way, this only seeds the replay's starting speed (resolved via `SettingsRepository.getRouteSpeedMs(route.speedProfileId)` in `StartRouteReplayUseCase`) — the user can still change speed mid-replay via the widget's Speed Cycle button (or Settings → GPS) like any other movement mode; a pin does not lock the speed for the session.

The speed profile control is hidden entirely for `RouteType.TELEPORT` routes, since teleport replay never reads `speedProfileId` (see "Teleport Routes" below).

### Next / Previous Waypoint (Teleport)

While a named route replay is active (running or paused), "Previous
waypoint" / "Next waypoint" buttons instantly teleport the spoofed position
to the adjacent stop in the route — skipping interpolation between them.
Available on all three route-control surfaces (see
@docs/features/widget.md, "Route Controls Across Surfaces"), alongside
Pause/Resume/Stop. When Teleport between waypoints is on, a jump lands on
that stop and lingers for the hop delay before the next automatic hop —
otherwise Next would skip two named stops (e.g. 20/31 → 22/31) and Previous
would hop forward again (looking like a no-op).

Implemented as `RouteReplayEngine.jumpToNextWaypoint()` /
`jumpToPreviousWaypoint()`, reusing the engine's existing
`resumeWaypointIndex` pointer rather than tracking a separate discrete
index. When Follow roads or Planting has expanded the waypoint list, jumps snap to the
nearest *named* waypoint via the engine's boundary-index list rather than
the nearest expanded point (road vertices, or planting-circle vertices). Not available for ephemeral (walk-here "Add next point") replay,
which has no persisted waypoint list. Gated by `hideTeleportFeatures` like every other teleport entry point
(@docs/features/hide-teleport.md), **and** by a separate, independent
opt-in toggle — `AppSettings.showRouteJumpButtons` (Settings → Menus →
Privacy → "Show route jump buttons", DataStore key
`show_route_jump_buttons`, default `false`). Both must allow the buttons
for them to show: `!hideTeleportFeatures && showRouteJumpButtons`.
Jumping to the last waypoint while replay is running lets it complete
naturally on the next tick, same as reaching it by walking.

### Start Flow

Starting a saved route shows a bottom sheet (Routes screen, map long-press
sheet, widget panel/floating map) with six checkboxes — Loop (checked by
default), **Planting**, Reverse, Return to location, **Follow roads**, and
**Teleport between waypoints** — plus a standalone **Teleport**
button and, at the bottom, **Cancel** / **Start**. The sheet opens fully expanded
(`skipPartiallyExpanded`). Loop / Planting / Reverse / Return / Follow roads /
Teleport between waypoints
disable the 48.dp Material checkbox min-size (`LocalMinimumInteractiveComponentSize`
= 0.dp) so the rows sit at ~24.dp each and Cancel / Start stay on a phone
without dragging the sheet up. Settings checkboxes keep the 48.dp target.

- **Loop** — checked by default. After the last stop, replay starts over until
  the user stops it. Exclusive with Return to location. Forced on (and Return
  off) while Planting is checked.
- **Follow roads** — when checked, road-following (via OSRM, foot profile)
  applies to every leg between the route's own saved waypoints during
  replay. Unchecked, those legs use straight-line interpolation. Per-leg OSRM
  failures fall back to a straight line for that leg only; if any
  between-waypoint legs fell back, one summary message is reported via
  `RoutingErrorReporter` (e.g. "Road-following partially unavailable — 2 of 5
  legs used straight-line paths"), mirroring `RoamingEngine.planRoadFollowingRoute`
  (@docs/features/roaming.md). When teleport-to-start is off (`hideTeleportFeatures`),
  Follow roads also applies to the walk from the current position to the first waypoint.
  Combined with Planting, Follow roads applies only to the connectors between
  rings — the circles themselves stay geometric, matching paste Planting's
  "via roads" travel. Combined with **Teleport between waypoints**, Follow roads
  is skipped for those between-stop / between-ring legs (the hop replaces them);
  it still applies to the walk-to-start when `hideTeleportFeatures` is on.
  A user teleport while this planning is still in flight
  (`ReplayOrchestrator.abortInFlightStart`, STOP before UPDATE) cancels the start
  so a late OSRM result cannot snap GPS to the route.
- **Planting** — when checked, replay walks a closed clockwise circle around
  each saved waypoint (radius `RouteConstants.PLANTING_DEFAULT_RADIUS_METERS` = 35 m)
  instead of through its center. The saved `Route` / waypoints are not rewritten;
  expansion happens at replay start only (`expandWaypointsForPlanting`). The
  center is not a path point. Reverse reverses the saved centers, then plants.
  Jump next/prev targets the first vertex of each ring via boundary indices.
  A single saved waypoint is enough (one circle). Unchecked by default.
  Planting always loops: after the last circle, replay returns to the first
  vertex of the first circle and repeats until the user stops. The Loop
  checkbox is forced on (and Return to location off) while Planting is checked.
  `handleStart()` sets `isLooping = true` whenever `isPlanting` is true.
  Combined with **Teleport between waypoints**, each circle is walked fully,
  then replay hops to the first vertex of the next circle (same snap as
  skip-to-next-stop). Connectors are omitted (`stitchRingsWithoutConnectors`);
  Follow-roads OSRM is not called for those hops. After the last circle, loop
  still snaps to the first ring start.
- **Teleport between waypoints** — when checked, replay hops to each named
  stop instead of interpolating (or Follow-roads walking) the leg. Per-start
  only — not stored on `Route` / `ExportData`. Unchecked by default. Hidden
  (removed from the tree, not merely disabled) when `hideTeleportFeatures` is
  on; `StartRouteReplayUseCase` also ANDs the extra with `teleportToStart` so
  a stale intent cannot hop. Leftover interpolator carry is clamped at the
  ring-exit / previous stop so a tick cannot walk into the next hop target
  (`snapCarryIfCrossingBoundary`). Not used for ephemeral walk "add next point".
  A compact `delay (s)` field on the same checkbox row defaults to
  `TELEPORT_BETWEEN_DEFAULT_DELAY_SECONDS` (8) and is clamped 0–600. Hop-mode
  `start()` sets `lingerBeforeNextHop` so playback waits at the first stop
  before hopping to the second — matching Next/Previous and the loop wrap.
  After every hop — including the last stop before complete — `RouteReplayEngine`
  lingers for `hopLingerDurationMs` while still emitting GPS ticks so the mock
  provider does not go stale. Zero still waits one `UPDATE_INTERVAL_MS` tick
  so hops cannot burst the whole route in one frame. When looping: linger at
  last, snap to first, linger at first, then continue. The delay is the
  `EXTRA_TELEPORT_BETWEEN_DELAY_SECONDS` intent extra, not a `Route` field.
- **Teleport** — instantly teleports to the route's first waypoint (last,
  if Reverse is checked). Does not start replay; the sheet stays open so
  the user can still press Start afterward. Hidden when
  `hideTeleportFeatures` is on (@docs/features/hide-teleport.md). Still
  jumps to the saved stop itself when Planting is on; Start then snaps to
  the first rim vertex.
- **Start** — teleports to the first waypoint (last, if Reverse is checked),
  then begins replay honoring Loop/Reverse/Return to location, Follow roads,
  Planting, and Teleport between waypoints. Planting forces looping regardless of the Loop checkbox.
  When Teleport between waypoints is on, Start lingers at that first stop
  for the hop delay before jumping to the next. When `hideTeleportFeatures`
  is on, Start instead walks (straight or via roads, per Follow roads) from
  the current position to the first replay point (the first rim vertex when
  Planting is on), then begins replay.

Implemented via `RouteStartConfig` fields: `teleportToStart` (default `false`, derived by
`StartRouteReplayUseCase` from Teleport between waypoints and `hideTeleportFeatures`), a
`followRoadsToStart: Boolean` flag (name unchanged, scope
widened), an `isPlanting: Boolean` flag (default `false`), a
`teleportBetweenWaypoints: Boolean` flag (default `false`), and a
`teleportBetweenDelaySeconds: Int` (default 8, clamp 0–600). They are threaded through
`StartRouteReplayUseCase` / `RoutesViewModel.startReplay()` as intent extras, rebuilt once into a
`RouteStartConfig` in `MockLocationService`, and passed into `ReplayOrchestrator.handleStart()`. `handleStart()` expands the route's
waypoint list into a road-resolved path (`expandWaypointsForFollowRoads`,
via `OsrmClient.resolveRoute()` per leg) before handing it to
`RouteReplayEngine` when Follow roads is on, Planting is off, and Teleport
between waypoints is off. When Teleport between waypoints is on and Planting
is off, the raw named stops are used (Follow-roads expansion is skipped).
When Planting
is on, `expandWaypointsForPlanting` builds circles around the saved centers
and optionally OSRM-resolves ring-to-ring connectors unless Teleport between
waypoints is on (then rings are concatenated with no connectors). The pre-replay approach to the
first waypoint is a teleport by default; the walk-to-start leg is only
resolved when `teleportToStart` is false. `RouteReplayEngine` tracks which indices in the
(possibly expanded) waypoint list are the route's real, named stops
("boundary indices"), so Previous/Next Waypoint (below) keeps jumping
between actual stops rather than the denser road-following or planting-circle points.

The same `replayWaypoints` list feeds `LocationRepository.routeWaypoints`, so the
map's polyline (main screen and floating widget map, via `MapController`'s existing
generic `routeTrace` plumbing) shows the resolved road-following or planting path during replay,
not the route's saved straight-line waypoints — reverting to the saved shape once the
session ends or restarts without those options.

## Teleport Routes

A `RouteType.TELEPORT` route replays by instantly jumping between waypoints instead of
interpolating movement — driven by `TeleportRouteEngine` (`:core:routing`), not
`RouteReplayEngine`. Each `Waypoint.waitSeconds` (min
`AppConstants.RouteConstants.MIN_TELEPORT_WAIT_SECONDS`, default
`AppConstants.RouteConstants.DEFAULT_TELEPORT_WAIT_SECONDS`) is how long the spoofed
position stays frozen at that stop before jumping to the next one. Like paused route
replay, the frozen position is still pushed to the mock provider every tick so the fix
never goes stale.

- **Placement**: in the route creator, placing a point on a `TELEPORT` route (map tap,
  search result, or favorite) shows a modal asking for the wait duration in seconds
  before the point is added. The Route Detail (edit) screen also lets each waypoint's
  wait duration be edited after the route is saved (an edit icon on each waypoint row,
  teleport routes only) — reuses the same validation as the creator's placement-time
  modal (minimum `AppConstants.RouteConstants.MIN_TELEPORT_WAIT_SECONDS`). Saves live on
  confirm, like the per-route speed profile control above.
- **Bulk wait-time edit**: the Route Detail screen (teleport routes only) shows a "Set
  wait time for all waypoints" button above the waypoint list, reusing the same wait
  dialog and validation as the per-waypoint edit. Overwrites `waitSeconds` on every
  waypoint in the route in one write (`RouteRepository.setAllWaypointsWaitSeconds`,
  `RouteDao.updateWaitSecondsForRoute`) — added so a large route (dozens of stops) can
  be retimed without opening each waypoint individually (issue #72 follow-up).
- **Randomize order**: a per-route toggle (`Route.randomizeTeleportOrder`, Route Detail
  screen, teleport routes only) shuffles the waypoint jump order. `TeleportRouteEngine`
  shuffles once on `start()` and again every time the loop restarts (`isLooping = true`
  and the last waypoint is reached), never mid-loop. Loop/no-loop is controlled entirely
  by the existing Loop checkbox on the start sheet — this toggle only changes the order,
  not whether it repeats. Persists on the route (round-trips through `ExportData` like
  `speedProfileId`) rather than being chosen per-start, matching the speed-profile pin
  pattern above.
- **No road-following**: "Follow roads" is hidden entirely on the start sheet for a
  teleport route — it has no meaning when nothing walks between points.
- **Loop / Reverse / Return to location**: unaffected — these only decide which
  waypoints replay in what order, not whether movement between them is instant.
- **Next / Previous waypoint jumps**: using the route-jump buttons
  (@docs/features/widget.md, "Route Controls Across Surfaces") while a teleport replay
  is waiting at a point resets that point's wait timer — landing on a waypoint, from
  either direction, always restarts its full configured wait.
- **Speed profile**: a teleport route's `speedProfileId` is never read during replay —
  nothing moves at a "speed" — so the widget's Speed Cycle button reports 0 m/s while a
  teleport replay is active.

## Recording

- Collect location every `AppConstants.LocationConstants.UPDATE_INTERVAL_MS` ms.
- Simplify via Ramer-Douglas-Peucker.
- Save on stop.

## Hot Routes

Settings → Routes → "Show hot routes" toggle (default off). When enabled, upserts curated GPX-based routes into the routes DB. When disabled, removes only entries this feature inserted.

Key files: `:core:data/RouteRepository.kt` (`HOT_ROUTES` list + `upsertHotRoutes`/`removeHotRoutes`), `:core:datastore/AppPreferencesDataSource.kt` (`hot_routes_enabled` key)

**Upsert rule**: match by name + city. IDs prefixed with `hot_route_`. If a route with same name already exists, coordinates are updated and original ID is preserved.

**Remove rule**: delete all routes whose ID starts with `hot_route_`.

**Export/import**: `hotRoutesEnabled` + `selectedHotRouteIds` fields in `ExportData`. Importing a backup with it `true` re-applies the upsert.

Route assets are bundled GPX files under `assets/hot_routes/`. All hot routes are saved as `RouteType.STRAIGHT` or `RouteType.GUIDED` depending on the asset.

## Edge Cases

- <2 waypoints → replay disabled.
- Resume after restart: persist waypoint index in DataStore.
