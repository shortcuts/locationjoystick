# Known Issues & Backlog

## Documentation Outdated Items

No outstanding documentation issues.

---

## Backlog

### Route replay reports bearing = 0 throughout replay

During route replay, `MockLocationService.onPositionUpdate` callback only updates `currentLat`/`currentLon`. `currentBearing` is set to `0.0f` at replay start and never updated during replay ticks. All consumers of `Location.bearing` see `0` regardless of actual movement direction.

Fix lives in `RouteReplayEngine` — it should compute per-tick bearing from consecutive waypoints and pass it through the callback to `currentBearing`. Out of scope for the GPS Realism Parity plan.
