# Roaming Mode

Set a center, radius, and distance. Walks randomly within the radius. Configured via bottom sheet on the Map screen. Default settings (area, distance, speed profile, follow-roads, and return-to-start) can be configured in Settings → Roaming.

Key files: `:core:routing/RoamingEngine.kt`, `:core:routing/OsrmClient.kt`, `:core:data/RoamingRepository.kt`, `:feature:map:impl/MapBottomSheets.kt`, `:feature:map:impl/MapViewModel.kt`

## Modes

- **Simple** (straight-line): no network required.
- **Road-following** (OSRM routes): opt-in. On OSRM failure, the affected segment falls back to a straight line automatically; already-planned road segments are kept.

## Algorithm

The entire route is pre-planned before walking begins. The map shows the complete wandering path upfront rather than updating per-segment.

**Waypoint count**: `numPoints = max(2, round(distanceMeters * 30 / 1000))` — scales linearly (1000 m → 30 points).

**Straight-line mode**: All waypoints generated upfront. With `returnToInitialLocation`, the second half mirrors back toward center with the center appended as the final point.

**Road-following mode (OSRM)**: Picks random points iteratively, fetches OSRM segments, accumulates road distance until budget is met (safety cap: 50 OSRM calls). With `returnToInitialLocation`, a final OSRM segment from the last point back to center is fetched. Always requests the foot profile (never the speed profile's transport mode) — see OSRM Configuration below. If an individual segment request fails, only that segment falls back to a straight line (haversine distance counted toward the budget) — road segments already fetched earlier in the route are kept, not discarded.

**Preview = planning**: `generateRoamingPreview` runs the full planning algorithm. `startRoaming` walks the pre-planned route directly. If no preview exists at start time, planning runs inline.

## Configuration Fields (`RoamingConfig`)

| Field | Description |
|---|---|
| `centerPosition` | Center of the roaming area |
| `radiusMeters` | Radius of the random walk area |
| `distanceMeters` | Total distance to walk before stopping |
| `speedProfileId` | Movement speed only — does not affect OSRM profile selection |
| `useRoadSnapping` | Enables OSRM road-following |
| `returnToInitialLocation` | Walk back to center after roaming completes |

`RoamingConfig` is constructed from `RoamingDefaults` via `RoamingDefaults.toConfig(centerPosition)` — the single translation site for `followRoads → useRoadSnapping`.

## OSRM Configuration

Base URL, overview, geometries, and profile constants in `AppConstants.OsrmConstants` and `AppConstants.RoamingConstants`.

All road-following OSRM requests (roaming, walk-via-roads, route creator, ephemeral replay) start on the foot profile. If OSRM returns `NoSegment` (a waypoint too far from any road, e.g. tapped on water or an unmapped area), `OsrmClient` snaps every waypoint to its nearest road node via the OSRM `/nearest` endpoint and retries the same slot once before the ladder advances.

### Reliability: Backend Ladder, Bisection, and Failure Reporting

Public OSRM servers have no SLA and can throttle, error, or time out unpredictably — worse in high-traffic areas. `OsrmClient` mitigates this with a slot ladder across two servers and three profiles:

- **Two backends**: FOSSGIS (`routing.openstreetmap.de/routed-{foot|bike|car}` — separate real per-profile graphs) is primary; the OSRM demo server (`router.project-osrm.org` — single car-ish graph for every profile) is secondary.
- **Slot ladder**: one slot per (backend, profile) pair — foot, bike, driving on FOSSGIS, then the same on the demo server. Any failure, transient or `NoRouteFound`, advances to the next slot after a backoff (`AppConstants.OsrmConstants.LADDER_BACKOFF_MS`: 200/400/700/1000/1500 ms, ±`RETRY_JITTER_MS` jitter). Consecutive slots differ in profile (covers no-route on one graph) and eventually in backend (covers outages). A route resolved by a later slot may be bike- or driving-shaped — accepted tradeoff over falling back to a straight line.
- **Single-graph skip**: a non-transient failure (`NoRouteFound`/`NoSegment`) on the demo server skips its remaining profile slots — the same graph cannot answer differently for another profile name.
- **Hard time budget**: the whole ladder (slots + waits + snap) is bounded by `AppConstants.OsrmConstants.TOTAL_TIME_BUDGET_MS` (10 s) via coroutine cancellation, and each HTTP attempt by `ATTEMPT_TIMEOUT_MS` (2.5 s) — a server slower than that is treated as down.
- **Classified failures**: every OSRM failure is classified into `OsrmFailureReason` (`Timeout`, `ServerError`, `RateLimited`, `NoRouteFound`, `NetworkUnavailable`, `Unknown`) by exception type, never by parsing message strings. HTTP 429 is classified as `RateLimited`, carrying the response's `Retry-After` value (if numeric) as `retryAfterMs`.
- **Rate-limit wait**: after a 429, the wait before the next slot is the response's `Retry-After` header if present, otherwise `AppConstants.OsrmConstants.RATE_LIMIT_BACKOFF_MS` — instead of the ladder backoff.
- **Bisection for long legs**: a single A→B request beyond `AppConstants.OsrmConstants.BISECTION_MIN_DISTANCE_METERS` that still fails after the ladder is split at the midpoint and each half resolved independently (recursively, up to `BISECTION_MAX_DEPTH`), in parallel, bounded by `BISECTION_TIME_BUDGET_MS` via coroutine cancellation (not polling). Each leaf gets one attempt per backend, no waits; sub-legs that fail even after exhausting depth fall back to a straight line.

`RoamingEngine`'s legs are short enough to stay below the bisection threshold by construction, so roaming continues to rely on its own per-segment straight-line fallback (`fetchSegmentOrFallback`) rather than bisection.

**User-visible errors**: `RoutingErrorReporter` (`:core:routing`, `@Singleton`) is a shared channel for routing failures, replacing the previous per-`MapController` flow. `MapController.walkViaRoads` reports a reason-specific message (e.g. "Routing server unavailable — using straight walk") instead of a generic one. `RoamingEngine.planRoadFollowingRoute` tracks how many planned segments fell back to straight-line and, if any did, emits one summary message after planning completes (e.g. "Road-following partially unavailable — 3 of 9 legs used straight-line paths") instead of staying silent.

## State Management

- `RoamingRepository` owns `isRoaming` and `isRoamingPaused` `StateFlow`s.
- `RoamingEngine` owns `activeJob` and the coroutine scope. Only one session active at a time — starting a new one awaits cancellation of the previous via `cancelAndJoin` before movement begins.
- Completion (natural loop exit) fires `onComplete` callback → `RoamingRepository` resets mode and clears route waypoints.
- `RoamingRepository` reports live travel-direction bearing between consecutive ticks via `LocationRepository.currentBearing`, the same mechanism route replay uses (@docs/features/routes.md, "Replay") — previously frozen at 0°/north for the whole roaming session.
