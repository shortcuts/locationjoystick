# Roaming Mode

Set a center, radius, and distance. Walks randomly within the radius. Configured via bottom sheet on the Map screen.

Key files: `:core:routing/RoamingEngine.kt`, `:core:routing/OsrmClient.kt`, `:core:data/RoamingRepository.kt`, `:feature:map:impl/RoamingSheet.kt`, `:feature:map:impl/MapViewModel.kt`

## Modes

- **Simple** (straight-line): no network required.
- **Road-following** (OSRM routes): opt-in. On OSRM failure, falls back to straight-line automatically.

## Algorithm

1. Pick a random destination within the radius using uniform disk distribution.
2. Fetch OSRM route (or straight-line) to that destination.
3. Walk the route via `walkRouteSegment()` (pause-check → interpolate → position callback → delay loop).
4. Repeat until `remainingMeters <= 0` (controlled by `RoamingConfig.distanceMeters`).
5. If `returnToInitialLocation` is enabled, walk back to the start position after the main loop.

## Configuration Fields (`RoamingConfig`)

| Field | Description |
|---|---|
| `centerPosition` | Center of the roaming area |
| `radiusMeters` | Radius of the random walk area |
| `distanceMeters` | Total distance to walk before stopping |
| `speedProfileId` | Selects OSRM profile (`"bike"` → cycling, else foot) |
| `useRoadSnapping` | Enables OSRM road-following |
| `returnToInitialLocation` | Walk back to center after roaming completes |

`RoamingConfig` is constructed from `RoamingDefaults` via `RoamingDefaults.toConfig(centerPosition)` — the single translation site for `followRoads → useRoadSnapping`.

## OSRM Configuration

Base URL, overview, geometries, and profile constants in `AppConstants.OsrmConstants` and `AppConstants.RoamingConstants`.

## State Management

- `RoamingRepository` owns `isRoaming` and `isRoamingPaused` `StateFlow`s.
- `RoamingEngine` owns `activeJob` and the coroutine scope. Only one session active at a time — starting a new one awaits cancellation of the previous via `cancelAndJoin` before movement begins.
- Completion (natural loop exit) fires `onComplete` callback → `RoamingRepository` resets mode and clears route waypoints.
