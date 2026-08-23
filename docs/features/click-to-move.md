# Click-to-Move / Teleport

Long-press map → bottom sheet with "Walk here" or "Teleport here".

Key files: `:feature:map:impl/MapViewModel.kt`, `:core:location/EphemeralReplayController.kt`, `:core:data/WalkCoordinator.kt`

## Walk Here

- Bearing computed from current position to target.
- Advances at `currentSpeed` m/s per tick.
- Snaps to target when within `AppConstants.LocationConstants.WALK_ARRIVAL_THRESHOLD_METERS`.

## Teleport

- Sets position directly.
- Pushes one update.

## Walk via Roads

- Long-press map → bottom sheet → "Walk via roads".
- Fetches OSRM route from current position to target; walks it segment by segment.
- On OSRM failure (after the backend/profile ladder and bisection — see @docs/features/roaming.md), falls back to a straight-line walk and reports a reason-specific message via `RoutingErrorReporter` (`:core:routing`), e.g. "Routing server unavailable — using straight walk".

## Add Next Point (Ephemeral Replay)

While a walk-here is active, the user can tap "Add next point" (straight line) or "Add next
point via roads" on the map to chain waypoints without saving a route. Each tap picks
road-following independently for that leg only — it is not inherited from how the walk or
any earlier point was started.

Managed by `EphemeralReplayController` (`@Singleton`, `:core:location`), injected by both `MapViewModel` and `FloatingWidgetService`:

- **First tap** (walk active): cancels the walk via `WalkCoordinator`, builds a 3-point list (walkStart → walkTarget → newPoint), starts `RouteReplayEngine` in ephemeral mode.
  - The `walkTarget → newPoint` leg is resolved via OSRM (`followRoads = true`) only if the
    tapped button was "via roads" — independent of whether the walk itself was via roads.
- **Subsequent taps** (already in `ROUTE_REPLAY`): appends the new point to the live route, using that tap's own `followRoads` choice.
- **No active walk**: no-op.

`MapController.addEphemeralWaypoint(position, followRoads)` takes `followRoads` as a caller-supplied
parameter rather than deriving it from `WalkMode` — deriving it from prior state mixed up
road/no-road legs across taps (e.g. two straight-line taps followed by a road-following one
would silently reuse a stale flag).

This eliminates duplicated state-machine logic that previously existed in both `MapViewModel` and `FloatingWidgetService`.

If a road-following leg falls back to a straight line (OSRM backend/profile ladder and bisection exhausted), `EphemeralReplayController` reports a reason-specific message via the shared `RoutingErrorReporter` (`:core:routing`), e.g. "No road route found — using straight line for part of the route".

## Edge Cases

- New walk-here cancels the previous one.
- Walk-here while route replay is active → show confirmation dialog to stop replay before proceeding.
- "Add next point" while in roaming mode → no-op (only valid during walk-to or active ephemeral replay).
