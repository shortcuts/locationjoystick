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
7. Bearing accuracy
8. Satellite extras

## GPS Realism

Five independent toggles in `AppSettings` (persisted via DataStore).

Defaults: `bearingHoldOnIdle = true`, `altitudeEnabled = true`, `satelliteExtrasEnabled = true`. Others default `false`.

| Setting | `AppSettings` field | Behaviour |
|---|---|---|
| Bearing hold | `bearingHoldOnIdle` | `speedMs == 0` → reports `lastNonZeroBearing` not 0° — no compass reset to north. |
| No bearing before first move | — (always on) | `Location.hasBearing()` stays `false` until the first tick with `speedMs > 0` in the session (`hasEverMoved`, `MockLocationService`) — real GPS reports no bearing before its first fix with motion. Once true, bearing reporting stays on for the rest of the session, including while stationary (bearing-hold above still applies). Reset on `startSpoofing()`. |
| Dynamic bearing accuracy | — (always on) | `bearingAccuracyDegrees` widens to `BEARING_ACCURACY_STOPPED_DEGREES` (180°) near a stop (heading is undefined), otherwise tightens toward `BEARING_ACCURACY_MOVING_MIN_DEGREES` (5°) as speed rises toward `BEARING_ACCURACY_REFERENCE_SPEED_MPS`, plus noise — replaces a static 3° reported regardless of motion (issue #56). Gated by the same `hasEverMoved`/`hasBearing` rule as bearing itself (`applyToProvider`, `MockLocationService`) — not reported until the first tick with motion, matching real GPS (issue #58). |
| Altitude drift | `altitudeEnabled` | Each tick draws a fresh Gaussian offset (σ = user-configurable `AppSettings.altitudeJitterRadiusMeters`, Settings → Location Randomness → "Altitude variation", default `RealismConstants.ALTITUDE_SIGMA_METERS`) from the resolved base altitude (see "Real Elevation Lookup" below) instead of the hardcoded `DEFAULT_ALTITUDE_METERS` — not a random walk accumulated tick-to-tick, which let drift wander far past the configured radius (issue #54). Clamped as an outer safety bound within `±ALTITUDE_CLAMP_RADIUS_METERS` of the anchor. |
| Warm-up envelope | `warmupEnabled` | Accuracy degrades at start, converges over `RealismConstants.WARMUP_DURATION_SECONDS` (≈ 30 s). `warmupStartMs` set once in `startSpoofing`, never reset on pause/resume. |
| Satellite extras | `satelliteExtrasEnabled` | Attaches `Bundle` extras with slow-churning total + in-fix satellite counts. Refreshed every `RealismConstants.SATELLITE_UPDATE_INTERVAL_MS`. |
| Suspended mocking | `suspendedMockingEnabled` | Push/pause cycle: reports normal jittered movement for `RealismConstants.SUSPENDED_PUSH_DURATION_MS`, then a stationary, unjittered fix for `RealismConstants.SUSPENDED_PAUSE_DURATION_MS` + random jitter up to `SUSPENDED_PAUSE_JITTER_MS` — the tick is still pushed to the provider every second so it never goes stale (see the same fix applied to paused route replay in `docs/features/routes.md`). Auto-disabled in `ROUTE_REPLAY` and `WALK_TO` modes. |

**Idle speed wobble** (`AppSettings.jitterSpeedIdleVariationPct`, Settings → Location Randomness): scales off
`AppConstants.JitterConstants.IDLE_SPEED_WOBBLE_MAX_MPS` (0.1 m/s), not the active speed profile — the two were
previously coupled, so switching profiles silently changed idle noise magnitude (issue #56). Fires on a
configurable percentage of idle ticks — `jitterSpeedIdleWobbleProbabilityPct` (Settings → Location Randomness
→ "Idle wobble frequency (%)", default 15%, range 0–50%, same range as the amount setting above) — independent
of `jitterSpeedIdleVariationPct`, which only controls the wobble's magnitude once it fires; the rest of idle
ticks report exactly `0.0` m/s, matching real GPS (previously every idle tick drew a nonzero speed).

**Position jitter** (`AppSettings.jitterIdleRadiusMeters`/`jitterMovingRadiusMeters`, Settings →
Location Randomness → "Wobble when still"/"Wobble while moving"): `PositionJitterCoordinator`
(`:core:location`) owns a persistent offset that wanders inside the configured radius, stepping
at most `AppSettings.jitterMaxStepMeters` (Settings → Location Randomness → "Max step per tick",
default 1.0 m, range 0.1–5.0 m) in a fresh random direction every tick, clamped back to the disc
edge on overshoot — the reported position drifts continuously around the real anchor instead of
sitting exact between fires and teleporting to a random point and back (issue #60, replacing the
previous interval-gated single Gaussian draw). An earlier version of this fix picked one distant
random target per leg and beelined straight to it, which read as a DVD-logo bounce off the
deviation radius (issue #60 follow-up) — redrawing the heading every tick instead of holding it
until a target is reached fixes that. Runs unconditionally every tick — there is no longer an
"how often" interval setting. While moving, the deviation disc is squished into an ellipse along
the direction of travel (`AppConstants.JitterConstants.LONGITUDINAL_JITTER_FRACTION`), same
anisotropy as before.
Mode/radius selection (`resolveJitterStepRequest`, `LocationLoopPolicy.kt`) lives outside
`buildLocation`, which now just adds the precomputed offset to the anchor. The resolved radius
is also published every tick via `DebugStats.jitterRadiusMeters` for the map's jitter-radius
overlay (@docs/features/map.md).

All realism tuning values in `AppConstants.RealismConstants`.

## Real Elevation Lookup

`AppSettings.realElevationEnabled` (default `true`) fetches the actual ground elevation at the
spoofed position from Open-Meteo's keyless elevation endpoint (`ElevationRepository`, `:core:data`)
instead of anchoring the altitude Gaussian walk to a flat `DEFAULT_ALTITUDE_METERS` (~35 m).

- **Trigger**: fetched on `startSpoofing()`, then re-fetched every
  `AppConstants.RealismConstants.ELEVATION_FETCH_INTERVAL_MS` (60 s) while spoofing is
  `RUNNING`/`PAUSED`, checked inline in `MockLocationService.captureSnapshot()` via
  `AltitudeAnchorCoordinator.maybeFetchElevation()` — no separate coroutine loop. A fetch already
  in flight is never duplicated.
- **Instant on start and teleport, gradual after**: `startSpoofing()`'s fetch and
  `MockLocationService.updatePosition()`'s (the sole caller — see `TeleportUseCase`) both pass
  `instant = true, force = true`, so a successful lookup jumps the anchor straight to the real
  elevation instead of leaving it to approach tick by tick — nothing has been reported at the new
  position yet, so there's no jump to smooth over (issue #51). `force` bypasses the 60 s
  fetch-interval throttle, since that throttle exists to rate-limit repeated lookups at roughly the
  *same* position, not a lookup for a position that just changed outright. Every later periodic
  fetch (mid-session drift while stationary or moving) omits both flags and keeps the gradual
  behavior below.
- **Gradual convergence (mid-session only)**: a periodic fetched value becomes a *target*
  (`targetBaseAltitudeMeters`), not an instant reassignment — the effective anchor
  (owned by `AltitudeAnchorCoordinator`, what `buildLocation`'s clamp actually centers on) steps toward it
  by at most `AppConstants.RealismConstants.ALTITUDE_TARGET_STEP_METERS_PER_TICK` (0.5 m) per
  tick, so a 100 m elevation change converges over roughly 3.5 minutes instead of jumping.
- **Never a bare integer**: the fetched value (Open-Meteo's DEM data is
  whole-meter resolution) is perturbed by a small sub-meter random offset
  (`AppConstants.RealismConstants.ELEVATION_FRACTIONAL_JITTER_METERS`,
  ±0.49 m) before becoming the convergence target — otherwise the anchor
  itself would lock onto a flat round number once converged (issue #52).
- **Failure/disabled**: the target simply doesn't move — the anchor stays wherever it last
  converged to (or the 35 m default on first failure).
- **Manual override wins**: see "Altitude Override Button" in @docs/features/widget.md. Setting
  an override suspends the periodic fetch entirely (checked before every fetch) until Settings →
  GPS → "Reset elevation override" clears it, at which point the next periodic cycle resumes
  fetching automatically.