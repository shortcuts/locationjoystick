# Route System

Create waypoints on map → polyline in order. Save, edit, replay, loop, or record in real time.

Key files: `:feature:routes:impl/RoutesScreen.kt`, `:feature:routes:impl/RouteCreatorScreen.kt`, `:feature:routes:impl/RoutesViewModel.kt`, `:core:database/RouteDao.kt`, `:core:routing/RouteReplayEngine.kt`

## Route Types

- **STRAIGHT** (`RouteType.STRAIGHT`): straight-line segments between waypoints, no network required.
- **GUIDED** (`RouteType.GUIDED`): OSRM road-following segments. On OSRM failure, sets `osrmError = true` in `CreatorState`. No silent fallback to straight line.

## Storage

`RouteEntity` + `WaypointEntity` one-to-many. Waypoints store `routeId`, `lat`, `lon`, `orderIndex`. Queried via `@Transaction @Query` returning `Flow<RouteWithWaypoints>`.

## Replay

- Interpolate between waypoints at speed (m/s).
- Compute bearing via `atan2`.
- Advance by `speed * deltaTime`.
- Snap to waypoint when within `AppConstants.LocationConstants.WALK_ARRIVAL_THRESHOLD_METERS`.
- Looping: interpolate smoothly from last to first waypoint.

## Recording

- Collect real location at `AppConstants.LocationConstants.UPDATE_INTERVAL_MS` ms intervals.
- Simplify via Ramer-Douglas-Peucker (epsilon = `AppConstants.LocationConstants.RDP_SIMPLIFICATION_EPSILON_METERS` m).
- Save on stop.

## Edge Cases

- Fewer than 2 waypoints → disable replay.
- Resume replay after service restart: persist current waypoint index in DataStore.
