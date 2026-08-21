# Route System

Waypoints on map → polyline. Save, edit, replay, loop, record real-time.

Key files: `:feature:routes:impl/RoutesScreen.kt`, `:feature:routes:impl/RouteCreatorScreen.kt`, `:feature:routes:impl/RoutesViewModel.kt`, `:core:database/RouteDao.kt`, `:core:routing/RouteReplayEngine.kt`

## Route Types

- **STRAIGHT** (`RouteType.STRAIGHT`): straight segments, no network.
- **GUIDED** (`RouteType.GUIDED`): OSRM road-following. On fail → `osrmError = true` in `CreatorState`. No silent fallback.

## Storage

`RouteEntity` + `WaypointEntity` one-to-many. Waypoints: `routeId`, `lat`, `lon`, `orderIndex`. Query via `@Transaction @Query` → `Flow<RouteWithWaypoints>`.

Routes can also be imported from GPX files via the Routes screen overflow menu → "Import GPX". Max file size: 10 MB. Parsed and saved as `RouteType.STRAIGHT` routes.

## Replay

- Interpolate waypoints at speed (m/s).
- Bearing via `atan2`.
- Advance: `speed * deltaTime`.
- Snap at `AppConstants.LocationConstants.WALK_ARRIVAL_THRESHOLD_METERS`.
- Loop: smooth interpolation last→first waypoint.
- Pausing keeps pushing the frozen position to the mock location provider
  every tick (1 Hz) instead of stopping ticks entirely — otherwise the mock
  fix goes stale and some location consumers fall back to the device's real
  GPS position until the replay resumes.

### Per-Route Speed Profile

A route may pin a speed profile via the Route Detail (edit) screen — a segmented control below the name field, showing "None" plus all 5 presets. Default: `null` ("None"), meaning replay uses whatever speed profile is currently active globally (today's behavior). When a route pins a profile, replay uses that profile's speed for the entire session — it stays locked even if the globally active profile changes mid-replay (e.g. via the widget's Speed Cycle button). Resolved via `SettingsRepository.getRouteSpeedMs(route.speedProfileId)`.

### Next / Previous Waypoint (Teleport)

While a named route replay is active (running or paused), "Previous
waypoint" / "Next waypoint" buttons instantly teleport the spoofed position
to the adjacent stop in the route — skipping interpolation between them.
Available on all three route-control surfaces (see
@docs/features/widget.md, "Route Controls Across Surfaces"), alongside
Pause/Resume/Stop.

Implemented as `RouteReplayEngine.jumpToNextWaypoint()` /
`jumpToPreviousWaypoint()`, reusing the engine's existing
`resumeWaypointIndex` pointer rather than tracking a separate discrete
index. When Follow roads has expanded the waypoint list, jumps snap to the
nearest *named* waypoint via the engine's boundary-index list rather than
the nearest expanded point. Not available for ephemeral (walk-here "Add next point") replay,
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
sheet, widget panel/floating map) with four checkboxes — Loop, Reverse,
Return to location, and **Follow roads** — plus a standalone **Teleport**
button and, at the bottom, **Cancel** / **Start**.

- **Follow roads** — when checked, road-following (via OSRM, foot profile)
  applies to both the walk from the current position to the route's first
  waypoint AND every leg between the route's own saved waypoints during
  replay. Unchecked, both use straight-line interpolation (today's default
  behavior). Per-leg OSRM failures fall back to a straight line for that
  leg only; if any between-waypoint legs fell back, one summary message is
  reported via `RoutingErrorReporter` (e.g. "Road-following partially
  unavailable — 2 of 5 legs used straight-line paths"), mirroring
  `RoamingEngine.planRoadFollowingRoute` (@docs/features/roaming.md).
- **Teleport** — instantly teleports to the route's first waypoint (last,
  if Reverse is checked). Does not start replay; the sheet stays open so
  the user can still press Start afterward. Hidden when
  `hideTeleportFeatures` is on (@docs/features/hide-teleport.md).
- **Start** — walks (straight or via roads, per Follow roads) from the
  current position to the first waypoint, then begins replay honoring
  Loop/Reverse/Return to location and Follow roads for the between-waypoint
  legs too.

Implemented via a `followRoadsToStart: Boolean` flag (name unchanged, scope
widened) threaded through `StartRouteReplayUseCase` / `RoutesViewModel.startReplay()`
into `ReplayOrchestrator.handleStart()`, which now expands the route's
waypoint list into a road-resolved path (`expandWaypointsForFollowRoads`,
via `OsrmClient.resolveRoute()` per leg) before handing it to
`RouteReplayEngine`, in addition to resolving the walk-to-start leg the
same way it already did. `RouteReplayEngine` tracks which indices in the
(possibly expanded) waypoint list are the route's real, named stops
("boundary indices"), so Previous/Next Waypoint (below) keeps jumping
between actual stops rather than the denser road-following points.

The same `replayWaypoints` list feeds `LocationRepository.routeWaypoints`, so the
map's polyline (main screen and floating widget map, via `MapController`'s existing
generic `routeTrace` plumbing) shows the resolved road-following path during replay,
not the route's saved straight-line waypoints — reverting to the saved shape once the
session ends or restarts without Follow roads.

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