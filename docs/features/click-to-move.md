# Click-to-Move / Teleport

Long-press map → bottom sheet with "Walk here" or "Teleport here".

Tapping the map while spoofing and opening a deep link pin a `LatLng` (`pendingTapPosition` + `isPendingTapSheetOpen`) and show this same confirm sheet. The map's paste-coordinates sheet does not pin: Teleport / Walk / Walk via roads under the paste field run those actions directly on the **first** valid pair (`parsePastedCoordinates`). Extra points do not change those three. A future "open location in Maps" intent should skip the paste field and pin a `LatLng` the same way as a deep link.

With two or more valid points, the same sheet can **Save route** (new UUID, cloned waypoints) or **Start** a named `ROUTE_REPLAY` via a reserved Room row (`paste_temp_route` / "Temp Route from Paste") — see @docs/features/routes.md, "Paste coordinates (map and widget)". Start is real route replay, not ephemeral "Add next point". Teleport is hidden when `hideTeleportFeatures` is on; Start still walks to the first stop (`StartRouteReplayUseCase`).

Key files: `:feature:map:impl/MapViewModel.kt`, `:core:location/EphemeralReplayController.kt`, `:core:data/WalkCoordinator.kt`

## Walk Here

- Bearing computed from current position to target.
- Advances at `currentSpeed` m/s per tick.
- Snaps to target when within `AppConstants.LocationConstants.WALK_ARRIVAL_THRESHOLD_METERS`.

## Teleport

- Sets position directly.
- Pushes one GPS update immediately (does not wait for the 1 Hz loop). Does not need a network.
- User-initiated jumps (`TeleportUseCase.execute`, default `resetMovement = true`) stop any walk, roam, or route session first so the engine cannot overwrite the new position. That includes an in-flight Follow-roads route start: mode is still TELEPORT until OSRM returns, so STOP is sent even when `currentMode` is not yet `ROUTE_REPLAY`, and `ReplayOrchestrator.abortInFlightStart()` cancels the planning job before the teleport intent is applied. Favorite, map, and pasted-coordinate teleports all go through this path. Starting a saved route teleports to the first stop with `resetMovement = false` so the replay that follows is not cancelled.
- Parked spoofing (widget long-press Pause: mock GPS IDLE, widget still shown) does **not** resume on teleport. Feature buttons stay faded until Start. That is intentional — teleport is not a substitute for Start.

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
