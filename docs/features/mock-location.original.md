# Mock Location Engine

Injects fake GPS into Android's location system. All apps receive spoofed coordinates as real GPS.

Key files: `:core:location/MockLocationService.kt`, `:core:data/LocationRepository.kt`

## Core Mechanics

- Update rate: `AppConstants.LocationConstants.UPDATE_INTERVAL_MS` (1 Hz, matches real GPS cadence)
- On service stop: call `locationManager.removeTestProvider`. Failure leaves a ghost provider that breaks real GPS until reboot.

## Edge Cases

- Another app holds the mock location slot → `addTestProvider` throws `IllegalArgumentException`. Catch it, show a clear error to the user.
- `elapsedRealtimeNanos` must be monotonically increasing. Never set a fixed value.
- `accuracy` below `1.0f` can trigger anti-cheat detection. Keep within `AppConstants.JitterConstants.ACCURACY_MIN`–`AppConstants.JitterConstants.ACCURACY_MAX`.

## Internal Architecture

Each tick, `captureSnapshot()` reads all `@Volatile` service fields into an immutable `LocationSnapshot`. This eliminates TOCTOU races between fields read in `buildLocation`.

The pure function `buildLocation(state, nowMs, random)` takes that snapshot and returns a `LocationFix` (or `null` during suspended-phase). It has no Android imports — `Random` is injected for deterministic unit testing.

`applyToProvider()` translates `LocationFix` → `android.location.Location` and pushes it to `LocationManager`.

Execution order inside `buildLocation`:
1. Suspended-phase check
2. Altitude walk
3. Bearing hold
4. Position jitter
5. Warm-up accuracy envelope
6. Accuracy perturbation
7. Satellite extras

## GPS Realism

Five independent toggles stored in `AppSettings` (persisted via DataStore).

Defaults: `bearingHoldOnIdle = true`, `satelliteExtrasEnabled = true`. All others default to `false`.

| Setting | `AppSettings` field | Behaviour |
|---|---|---|
| Bearing hold | `bearingHoldOnIdle` | When `speedMs == 0`, reports `lastNonZeroBearing` instead of 0° — avoids resetting compass to north. |
| Altitude drift | `altitudeEnabled` | Gaussian random walk (σ = `RealismConstants.ALTITUDE_SIGMA_METERS`, drift = `ALTITUDE_DRIFT_PER_SECOND_METERS`) clamped within `±ALTITUDE_CLAMP_RADIUS_METERS` of `DEFAULT_ALTITUDE_METERS`. |
| Warm-up envelope | `warmupEnabled` | Accuracy degrades at session start and converges to nominal over `RealismConstants.WARMUP_DURATION_MS` (≈ 30 s). `warmupStartMs` is set once in `startSpoofing` and never reset on pause/resume. |
| Satellite extras | `satelliteExtrasEnabled` | Attaches `Bundle` extras with slow-churning total + in-fix satellite counts. Counts refreshed every `RealismConstants.SATELLITE_UPDATE_INTERVAL_MS`. |
| Suspended mocking | `suspendedMockingEnabled` | Push/pause cycle: pushes updates for `RealismConstants.SUSPENDED_PUSH_DURATION_MS`, then skips ticks for `RealismConstants.SUSPENDED_PAUSE_DURATION_MS`. Automatically disabled during `ROUTE_REPLAY` mode. |

All realism tuning values live in `AppConstants.RealismConstants`.
