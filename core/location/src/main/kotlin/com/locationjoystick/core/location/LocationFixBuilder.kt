package com.locationjoystick.core.location

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.MockMode
import kotlin.random.Random

/**
 * Immutable snapshot of all @Volatile service state, captured once at the start of each tick by
 * [captureSnapshot][MockLocationService] to avoid TOCTOU races between reading individual fields in [buildLocation].
 *
 * @property latitude Current spoofed latitude.
 * @property longitude Current spoofed longitude.
 * @property speedMs Current speed in m/s; 0 when stationary.
 * @property bearing Current heading in degrees; only meaningful when [speedMs] > 0.
 * @property lastNonZeroBearing The most recent bearing from a tick where [speedMs] was non-zero.
 *   Used to hold the displayed heading when the device stops moving.
 * @property hasEverMoved True once any tick in the current session has reported [speedMs] > 0.
 *   Real GPS reports no bearing at all before the first fix with motion — [buildLocation] mirrors
 *   that by leaving [LocationFix.hasBearing] false until this flips true (issue #56).
 * @property mode Active [MockMode] at snapshot time.
 * @property jitterOffsetNorthM Persistent position-jitter offset (north meters), resolved by
 *   [PositionJitterCoordinator.step] in captureSnapshot — replaces the old interval-gated Gaussian
 *   draw (issue #60).
 * @property jitterOffsetEastM Persistent position-jitter offset (east meters), paired with
 *   [jitterOffsetNorthM].
 * @property altitudeMeters Seed altitude for the Gaussian random walk this tick; written back from
 *   [LocationFix.altitudeMeters] after each successful tick.
 * @property warmupStartMs Wall-clock ms when startSpoofing was called.
 *   Intentionally NOT reset on pause/resume so the warmup curve is continuous.
 * @property warmupEnabled Whether the accuracy warm-up envelope feature is active.
 * @property bearingHoldEnabled Whether to hold the last non-zero bearing when stationary.
 * @property altitudeEnabled Whether to simulate altitude with a Gaussian random walk.
 * @property satelliteExtrasEnabled Whether to attach satellite count extras to each fix.
 * @property speedIdleVariationPct Percentage (of [AppConstants.JitterConstants.IDLE_SPEED_WOBBLE_MAX_MPS], not the
 *   active speed profile — decoupled per issue #56) used as the range for idle speed variation (0 = off).
 * @property speedIdleWobbleProbabilityPct Percentage chance (0-100) that an idle tick fires a wobble at all —
 *   independent of speedIdleVariationPct, which only controls the wobble's magnitude when it fires.
 * @property speedMovingVariationPct Percentage of current speed to use as symmetric noise for moving speed variation (0 = off).
 * @property suspendedPhaseStartMs Timestamp of the last phase transition in the push/pause cycle.
 * @property isSuspendedPhase True when currently in the pause window of the push/pause cycle;
 *   [buildLocation] reports a stationary fix (speed `0`, no jitter) for the entire duration of
 *   this phase instead of skipping the push — see docs/features/mock-location.md.
 * @property cachedSatelliteCount Slow-churn total satellite count, refreshed every
 *   [AppConstants.RealismConstants.SATELLITE_UPDATE_INTERVAL_MS] ms by captureSnapshot.
 * @property cachedUsedInFixCount Slow-churn in-fix satellite count, updated alongside
 *   [cachedSatelliteCount].
 * @property baseAltitudeMeters The clamp-center anchor for the altitude Gaussian walk — the
 *   resolved default/fetched/overridden base altitude, not the hardcoded constant.
 * @property altitudeJitterRadiusMeters Gaussian sigma for the altitude random walk (user-configurable).
 * @property jitterRadiusMeters The resolved jitter radius for this tick (idle or moving, from
 *   `resolveJitterStepRequest`) — carried through only for `DebugStats` publishing; `buildLocation`
 *   does not read it directly (it reads the already-applied `jitterOffsetNorthM`/`jitterOffsetEastM`
 *   instead).
 */
internal data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val speedMs: Float,
    val bearing: Float,
    val lastNonZeroBearing: Float,
    val hasEverMoved: Boolean,
    val mode: MockMode,
    val jitterOffsetNorthM: Double,
    val jitterOffsetEastM: Double,
    val altitudeMeters: Double,
    val warmupStartMs: Long,
    val warmupEnabled: Boolean,
    val bearingHoldEnabled: Boolean,
    val altitudeEnabled: Boolean,
    val satelliteExtrasEnabled: Boolean,
    val speedIdleVariationPct: Int,
    val speedIdleWobbleProbabilityPct: Int,
    val speedMovingVariationPct: Int,
    val suspendedPhaseStartMs: Long,
    val isSuspendedPhase: Boolean,
    val cachedSatelliteCount: Int,
    val cachedUsedInFixCount: Int,
    val baseAltitudeMeters: Double,
    val altitudeJitterRadiusMeters: Double,
    val jitterRadiusMeters: Double,
)

/**
 * Pure output of [buildLocation]: a GPS fix expressed in domain types, with no Android imports.
 * Translated into an [android.location.Location] only inside applyToProvider.
 *
 * @property latitude Spoofed latitude, possibly perturbed by jitter.
 * @property longitude Spoofed longitude, possibly perturbed by jitter.
 * @property altitudeMeters Result of the Gaussian altitude random walk for this tick.
 * @property speedMs Speed in m/s to report to the provider.
 * @property bearing Heading in degrees after bearing-hold logic is applied.
 * @property hasBearing Whether the provider should report a bearing at all — false before the
 *   first tick with motion in the session (see [LocationSnapshot.hasEverMoved]).
 * @property accuracyMeters Horizontal accuracy, either from the warm-up envelope or perturbed fine accuracy.
 * @property verticalAccuracyMeters Fixed vertical accuracy constant.
 * @property bearingAccuracyDegrees Bearing accuracy in degrees — widens near a stop, tightens with speed.
 * @property speedAccuracyMps Fixed speed accuracy constant.
 * @property satelliteCount Total visible satellite count, or null when satellite extras are disabled.
 * @property usedInFixCount Satellites contributing to this fix, or null when satellite extras are disabled.
 */
internal data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val speedMs: Float,
    val bearing: Float,
    val hasBearing: Boolean,
    val accuracyMeters: Float,
    val verticalAccuracyMeters: Float,
    val bearingAccuracyDegrees: Float,
    val speedAccuracyMps: Float,
    val satelliteCount: Int?,
    val usedInFixCount: Int?,
)

/**
 * Pure throttle check for persisting the live position to DataStore. `pushLocationUpdate()` runs
 * at 1 Hz; writing every tick would churn flash storage, so the write is skipped unless
 * [AppConstants.LocationConstants.LAST_LOCATION_PERSIST_INTERVAL_MS] has elapsed since the last one.
 */
internal fun shouldPersistLastLocation(
    lastPersistedMs: Long,
    nowMs: Long,
): Boolean = nowMs - lastPersistedMs >= AppConstants.LocationConstants.LAST_LOCATION_PERSIST_INTERVAL_MS

/** Atomic push/pause phase state for suspended mocking. */
internal data class SuspendedPhaseState(
    val isActive: Boolean,
    val startMs: Long,
)

/**
 * Pure transition function for the suspended-mocking push/pause state machine.
 *
 * Returns the next [SuspendedPhaseState] given the current state and clock. No side effects.
 * Disabled or mode-gated: resets isActive to false (startMs updated to now).
 */
internal fun advanceSuspendedPhase(
    current: SuspendedPhaseState,
    now: Long,
    enabled: Boolean,
    mode: MockMode,
    random: Random,
): SuspendedPhaseState {
    if (!enabled || mode == MockMode.ROUTE_REPLAY || mode == MockMode.WALK_TO) {
        // Return current unchanged if already in the idle (not-active) state to avoid
        // spurious log spam — no state transition is needed.
        return if (!current.isActive) current else SuspendedPhaseState(isActive = false, startMs = now)
    }
    val elapsed = now - current.startMs
    return when {
        !current.isActive && elapsed >= AppConstants.RealismConstants.SUSPENDED_PUSH_DURATION_MS -> {
            SuspendedPhaseState(isActive = true, startMs = now)
        }

        current.isActive -> {
            val pauseDur =
                AppConstants.RealismConstants.SUSPENDED_PAUSE_DURATION_MS +
                    random.nextLong(0, AppConstants.RealismConstants.SUSPENDED_PAUSE_JITTER_MS)
            if (elapsed >= pauseDur) SuspendedPhaseState(isActive = false, startMs = now) else current
        }

        else -> {
            current
        }
    }
}

/**
 * Applies a 2-D Gaussian displacement of [radiusMeters] to ([lat], [lon]) using Box-Muller —
 * the isotropic (no preferred direction) case of [gaussianLatLonOffsetLateral].
 */
internal fun gaussianLatLonOffset(
    lat: Double,
    lon: Double,
    radiusMeters: Double,
    random: Random,
): Pair<Double, Double> = gaussianLatLonOffsetLateral(lat, lon, radiusMeters, 0f, 1.0, random)

/**
 * Bearing-aware position jitter. Applies full Gaussian noise perpendicular to [bearingDeg]
 * and a fraction of that noise along [bearingDeg], so moving jitter does not fight the
 * intended direction of travel.
 *
 * bearingDeg: 0 = North, 90 = East (Android convention).
 */
internal fun gaussianLatLonOffsetLateral(
    lat: Double,
    lon: Double,
    radiusMeters: Double,
    bearingDeg: Float,
    longitudinalFraction: Double,
    random: Random,
): Pair<Double, Double> {
    val u1 = random.nextDouble().coerceAtLeast(Double.MIN_VALUE)
    val u2 = random.nextDouble()
    val mag = kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1))
    val theta = 2.0 * kotlin.math.PI * u2
    val gLateral = mag * kotlin.math.cos(theta) * radiusMeters
    val gLongitudinal = mag * kotlin.math.sin(theta) * radiusMeters * longitudinalFraction

    val bearingRad = Math.toRadians(bearingDeg.toDouble())
    val northFwd = kotlin.math.cos(bearingRad)
    val eastFwd = kotlin.math.sin(bearingRad)
    val northLat = -kotlin.math.sin(bearingRad)
    val eastLat = kotlin.math.cos(bearingRad)

    val northOffsetM = gLateral * northLat + gLongitudinal * northFwd
    val eastOffsetM = gLateral * eastLat + gLongitudinal * eastFwd

    return offsetLatLon(lat, lon, northOffsetM, eastOffsetM)
}

/** Converts a north/east meter offset into a lat/lon delta from ([lat], [lon]). */
internal fun offsetLatLon(
    lat: Double,
    lon: Double,
    northM: Double,
    eastM: Double,
): Pair<Double, Double> {
    val metersPerDeg = AppConstants.LocationConstants.METERS_PER_LATITUDE_DEGREE
    return Pair(
        lat + northM / metersPerDeg,
        lon + eastM / (metersPerDeg * kotlin.math.cos(Math.toRadians(lat))),
    )
}

/** Adds bounded Gaussian noise to [base] accuracy, clamped to [[ACCURACY_MIN], [ACCURACY_MAX]]. */
internal fun perturbAccuracy(
    base: Float,
    random: Random,
): Float =
    (
        base +
            (
                random.nextDouble() * AppConstants.JitterConstants.ACCURACY_PERTURBATION_RANGE -
                    AppConstants.JitterConstants.ACCURACY_PERTURBATION_RANGE / 2
            ).toFloat()
    ).coerceIn(AppConstants.JitterConstants.ACCURACY_MIN, AppConstants.JitterConstants.ACCURACY_MAX)

/**
 * Pure, side-effect-free GPS fix builder. No Android imports; [random] is injectable for testing.
 *
 * Execution order: suspended-phase check → altitude Gaussian walk → bearing hold + noise → speed perturbation
 * → position jitter → warm-up accuracy envelope → accuracy perturbation → bearing accuracy → satellite extras.
 *
 * @param state Immutable snapshot of all service state for this tick.
 * @param nowMs Elapsed realtime ms at the start of the tick, used for the warm-up curve.
 * @param random Source of randomness; pass [Random.Default] in production.
 * @return Always non-null — the suspended-phase window still returns a fix, just a
 *   stationary/unjittered one.
 */
internal fun buildLocation(
    state: LocationSnapshot,
    nowMs: Long,
    random: Random,
): LocationFix {
    // Altitude jitter: fresh Gaussian offset from the anchor each tick (not a random walk —
    // accumulating onto the previous tick's altitude let drift wander far past the configured
    // radius before the unrelated ALTITUDE_CLAMP_RADIUS_METERS safety clamp kicked in, issue #54).
    val newAltitude =
        if (state.altitudeEnabled) {
            val u1 = random.nextDouble().coerceAtLeast(Double.MIN_VALUE)
            val u2 = random.nextDouble()
            val mag =
                state.altitudeJitterRadiusMeters *
                    kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2.0 * kotlin.math.PI * u2)
            (state.baseAltitudeMeters + mag)
                .coerceIn(
                    state.baseAltitudeMeters - AppConstants.RealismConstants.ALTITUDE_CLAMP_RADIUS_METERS,
                    state.baseAltitudeMeters + AppConstants.RealismConstants.ALTITUDE_CLAMP_RADIUS_METERS,
                )
        } else {
            state.baseAltitudeMeters
        }

    // Bearing hold + noise
    val rawBearing =
        when {
            state.speedMs == 0f && state.bearingHoldEnabled -> state.lastNonZeroBearing
            state.speedMs == 0f -> 0f
            else -> state.bearing
        }
    val outBearing =
        if (state.speedMs > 0f) {
            val noise = (random.nextFloat() - 0.5f) * 2f * AppConstants.RealismConstants.BEARING_NOISE_DEGREES
            ((rawBearing + noise) % 360f + 360f) % 360f
        } else {
            rawBearing
        }
    val outHasBearing = state.hasEverMoved || state.speedMs > 0f

    // Speed perturbation
    val outSpeed =
        when {
            state.isSuspendedPhase -> 0f

            // Sparse, small idle wobble — decoupled from the active speed profile (issue #56):
            // real GPS reports 0.0 m/s on most idle ticks, with only occasional tiny deviations.
            state.speedMs == 0f && state.speedIdleVariationPct > 0 -> {
                if (random.nextDouble() < state.speedIdleWobbleProbabilityPct / 100.0) {
                    val sigma =
                        AppConstants.JitterConstants.IDLE_SPEED_WOBBLE_MAX_MPS * state.speedIdleVariationPct / 100.0
                    (random.nextDouble() * sigma).toFloat()
                } else {
                    0f
                }
            }

            state.speedMs > 0f && state.speedMovingVariationPct > 0 -> {
                val range = state.speedMs * state.speedMovingVariationPct / 100.0f
                (state.speedMs + (random.nextFloat() - 0.5f) * 2f * range).coerceAtLeast(0f)
            }

            else -> {
                state.speedMs
            }
        }

    // Jitter (position): the persistent offset is already resolved per-tick by
    // PositionJitterCoordinator.step() in captureSnapshot (issue #60) — buildLocation just adds it.
    val (outLat, outLon) =
        if (state.isSuspendedPhase) {
            Pair(state.latitude, state.longitude)
        } else {
            offsetLatLon(state.latitude, state.longitude, state.jitterOffsetNorthM, state.jitterOffsetEastM)
        }

    // Accuracy with warm-up envelope
    val outAccuracy =
        if (state.warmupEnabled) {
            val elapsedSec = (nowMs - state.warmupStartMs) / 1000.0
            if (elapsedSec <= AppConstants.RealismConstants.WARMUP_DURATION_SECONDS) {
                val t = (elapsedSec / AppConstants.RealismConstants.WARMUP_DURATION_SECONDS).toFloat().coerceIn(0f, 1f)
                AppConstants.RealismConstants.WARMUP_INITIAL_ACCURACY_METERS +
                    t * (
                        AppConstants.LocationConstants.LOCATION_ACCURACY_FINE -
                            AppConstants.RealismConstants.WARMUP_INITIAL_ACCURACY_METERS
                    )
            } else {
                perturbAccuracy(AppConstants.LocationConstants.LOCATION_ACCURACY_FINE, random)
            }
        } else {
            perturbAccuracy(AppConstants.LocationConstants.LOCATION_ACCURACY_FINE, random)
        }

    // Bearing accuracy: undefined heading near a stop widens toward BEARING_ACCURACY_STOPPED_DEGREES;
    // moving, it tightens toward BEARING_ACCURACY_MOVING_MIN_DEGREES as speed rises, with noise —
    // static 3° regardless of motion was unrealistic (issue #56).
    val outBearingAccuracy =
        if (outSpeed <= 0.05f) {
            AppConstants.RealismConstants.BEARING_ACCURACY_STOPPED_DEGREES
        } else {
            val speedFactor =
                (outSpeed / AppConstants.RealismConstants.BEARING_ACCURACY_REFERENCE_SPEED_MPS).coerceIn(0.0, 1.0)
            val base =
                AppConstants.RealismConstants.BEARING_ACCURACY_MOVING_MAX_DEGREES -
                    speedFactor.toFloat() * (
                        AppConstants.RealismConstants.BEARING_ACCURACY_MOVING_MAX_DEGREES -
                            AppConstants.RealismConstants.BEARING_ACCURACY_MOVING_MIN_DEGREES
                    )
            val noise =
                (random.nextFloat() - 0.5f) * AppConstants.RealismConstants.BEARING_ACCURACY_MOVING_NOISE_DEGREES
            (base + noise).coerceIn(
                AppConstants.RealismConstants.BEARING_ACCURACY_MOVING_MIN_DEGREES,
                AppConstants.RealismConstants.BEARING_ACCURACY_MOVING_MAX_DEGREES,
            )
        }

    return LocationFix(
        latitude = outLat,
        longitude = outLon,
        altitudeMeters = newAltitude,
        speedMs = outSpeed,
        bearing = outBearing,
        hasBearing = outHasBearing,
        accuracyMeters = outAccuracy,
        verticalAccuracyMeters = AppConstants.RealismConstants.VERTICAL_ACCURACY_METERS,
        bearingAccuracyDegrees = outBearingAccuracy,
        speedAccuracyMps = AppConstants.RealismConstants.SPEED_ACCURACY_MPS,
        satelliteCount = if (state.satelliteExtrasEnabled) state.cachedSatelliteCount else null,
        usedInFixCount = if (state.satelliteExtrasEnabled) state.cachedUsedInFixCount else null,
    )
}
