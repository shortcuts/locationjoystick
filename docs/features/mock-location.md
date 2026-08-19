# Mock Location Engine

Injects fake GPS into Android. All apps get spoofed coords as real GPS.

Key files: `:core:location/MockLocationService.kt`, `:core:data/LocationRepository.kt`

## Global Start/Stop Control

Every screen's top bar (`LjTopBar`/`LjScaffold`, `:core:designsystem`) shows a full-text toggle button — `> start` / `|| stop` — in the title slot, driving spoofing from anywhere in the app, not just the Map screen.

Backed by `MapController.isSpoofing` (`StateFlow<Boolean>`, derived from `LocationRepository.mockLocationState != IDLE`) and `MapController.toggleSpoofing()` (`:core:location`). Each screen obtains these via the shared `SpoofToggleViewModel` (`hiltViewModel()`), a thin wrapper so feature ViewModels don't need their own `MapController` dependency just for this control.

## Core Mechanics

- Update rate: `AppConstants.LocationConstants.UPDATE_INTERVAL_MS` (1 Hz, real GPS cadence)
- On stop: call `locationManager.removeTestProvider`. Failure = ghost provider, breaks real GPS until reboot.

## Edge Cases

- Another app holds mock slot → `addTestProvider` throws `IllegalArgumentException`. Catch, show clear error.
- `elapsedRealtimeNanos` must be monotonically increasing. Never fixed value.
- `accuracy` below `1.0f` triggers anti-cheat. Stay within `AppConstants.JitterConstants.ACCURACY_MIN`–`AppConstants.JitterConstants.ACCURACY_MAX`.

## Internal Architecture

Each tick, `captureSnapshot()` reads all `@Volatile` fields into immutable `LocationSnapshot`. Eliminates TOCTOU races in `buildLocation`. Both `nowMs` and `nowNanos` are captured together at tick start and passed through — never re-read from the clock inside `applyToProvider`.

Pure function `buildLocation(state, nowMs, random)` takes snapshot, returns `LocationFix` — always non-null; the suspended-phase window reports a stationary, unjittered fix rather than skipping the push (see "Suspended mocking" below). No Android imports — `Random` injected for deterministic tests.

`applyToProvider(fix, nowNanos)` translates `LocationFix` → `android.location.Location`, pushes to `LocationManager`. Receives captured `nowNanos` to guarantee monotonic `elapsedRealtimeNanos` consistent with the position computed in `buildLocation`.

Suspended-phase state is held in `AtomicReference<SuspendedPhaseState>` (an `internal data class(isActive, startMs)`). Transitions are computed by `advanceSuspendedPhase(current, now, enabled, mode, random)` — a pure function extracted to `:core:location` top-level for direct unit testing (see `SuspendedPhaseTest`).

Execution order inside `buildLocation`:
1. Suspended-phase check (forces speed to `0` and skips jitter for the tick — no longer skips the push)
2. Altitude walk
3. Bearing hold
4. Position jitter
5. Warm-up accuracy envelope
6. Accuracy perturbation
7. Satellite extras

## GPS Realism

Five independent toggles in `AppSettings` (persisted via DataStore).

Defaults: `bearingHoldOnIdle = true`, `altitudeEnabled = true`, `satelliteExtrasEnabled = true`. Others default `false`.

| Setting | `AppSettings` field | Behaviour |
|---|---|---|
| Bearing hold | `bearingHoldOnIdle` | `speedMs == 0` → reports `lastNonZeroBearing` not 0° — no compass reset to north. |
| Altitude drift | `altitudeEnabled` | Gaussian random walk (σ = `RealismConstants.ALTITUDE_SIGMA_METERS`, drift = `ALTITUDE_DRIFT_PER_SECOND_METERS`) clamped within `±ALTITUDE_CLAMP_RADIUS_METERS` of `DEFAULT_ALTITUDE_METERS`. |
| Warm-up envelope | `warmupEnabled` | Accuracy degrades at start, converges over `RealismConstants.WARMUP_DURATION_SECONDS` (≈ 30 s). `warmupStartMs` set once in `startSpoofing`, never reset on pause/resume. |
| Satellite extras | `satelliteExtrasEnabled` | Attaches `Bundle` extras with slow-churning total + in-fix satellite counts. Refreshed every `RealismConstants.SATELLITE_UPDATE_INTERVAL_MS`. |
| Suspended mocking | `suspendedMockingEnabled` | Push/pause cycle: reports normal jittered movement for `RealismConstants.SUSPENDED_PUSH_DURATION_MS`, then a stationary, unjittered fix for `RealismConstants.SUSPENDED_PAUSE_DURATION_MS` + random jitter up to `SUSPENDED_PAUSE_JITTER_MS` — the tick is still pushed to the provider every second so it never goes stale (see the same fix applied to paused route replay in `docs/features/routes.md`). Auto-disabled in `ROUTE_REPLAY` and `WALK_TO` modes. |

All realism tuning values in `AppConstants.RealismConstants`.