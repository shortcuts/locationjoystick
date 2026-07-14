package com.locationjoystick.core.location

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.MockLocationState
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
 * @property mode Active [MockMode] at snapshot time.
 * @property jitterIdleRadiusMeters Gaussian noise radius applied while stationary (TELEPORT mode).
 * @property jitterMovingRadiusMeters Gaussian noise radius applied while moving.
 * @property shouldApplyMovingJitter Pre-computed gate: true when the jitter interval has elapsed.
 *   [buildLocation] uses this directly without any clock arithmetic.
 * @property shouldApplyIdleJitter Pre-computed gate: true when the idle jitter interval has elapsed.
 *   [buildLocation] uses this to gate TELEPORT mode jitter.
 * @property altitudeMeters Seed altitude for the Gaussian random walk this tick; written back from
 *   [LocationFix.altitudeMeters] after each successful tick.
 * @property warmupStartMs Wall-clock ms when startSpoofing was called.
 *   Intentionally NOT reset on pause/resume so the warmup curve is continuous.
 * @property warmupEnabled Whether the accuracy warm-up envelope feature is active.
 * @property bearingHoldEnabled Whether to hold the last non-zero bearing when stationary.
 * @property altitudeEnabled Whether to simulate altitude with a Gaussian random walk.
 * @property satelliteExtrasEnabled Whether to attach satellite count extras to each fix.
 * @property speedIdleVariationPct Percentage of active profile speed to use as range for idle speed variation (0 = off).
 * @property speedMovingVariationPct Percentage of current speed to use as symmetric noise for moving speed variation (0 = off).
 * @property activeProfileSpeedMs Active speed profile speed in m/s, used as the scale for idle variation.
 * @property suspendedPhaseStartMs Timestamp of the last phase transition in the push/pause cycle.
 * @property isSuspendedPhase True when currently in the pause window of the push/pause cycle;
 *   [buildLocation] returns null for the entire duration of this phase.
 * @property cachedSatelliteCount Slow-churn total satellite count, refreshed every
 *   [AppConstants.RealismConstants.SATELLITE_UPDATE_INTERVAL_MS] ms by captureSnapshot.
 * @property cachedUsedInFixCount Slow-churn in-fix satellite count, updated alongside
 *   [cachedSatelliteCount].
 */
internal data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val speedMs: Float,
    val bearing: Float,
    val lastNonZeroBearing: Float,
    val mode: MockMode,
    val jitterIdleRadiusMeters: Double,
    val jitterMovingRadiusMeters: Double,
    val shouldApplyMovingJitter: Boolean,
    val shouldApplyIdleJitter: Boolean,
    val altitudeMeters: Double,
    val warmupStartMs: Long,
    val warmupEnabled: Boolean,
    val bearingHoldEnabled: Boolean,
    val altitudeEnabled: Boolean,
    val satelliteExtrasEnabled: Boolean,
    val speedIdleVariationPct: Int,
    val speedMovingVariationPct: Int,
    val activeProfileSpeedMs: Double,
    val suspendedPhaseStartMs: Long,
    val isSuspendedPhase: Boolean,
    val cachedSatelliteCount: Int,
    val cachedUsedInFixCount: Int,
    val humanAltitudeOffsetMeters: Double,
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
 * @property accuracyMeters Horizontal accuracy, either from the warm-up envelope or perturbed fine accuracy.
 * @property verticalAccuracyMeters Fixed vertical accuracy constant.
 * @property bearingAccuracyDegrees Fixed bearing accuracy constant.
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
    val accuracyMeters: Float,
    val verticalAccuracyMeters: Float,
    val bearingAccuracyDegrees: Float,
    val speedAccuracyMps: Float,
    val satelliteCount: Int?,
    val usedInFixCount: Int?,
    val humanAltitudeOffsetMeters: Double,
)

/** Atomic push/pause phase state for suspended mocking. */
internal data class SuspendedPhaseState(
    val isActive: Boolean,
    val startMs: Long,
)

/**
 * Decision for what [MockLocationService.observeLocationState] should do to the update loop /
 * test provider on an IDLE or ERROR state transition.
 */
internal enum class LocationLoopAction {
    /** Leave the update loop (and test provider) running untouched. */
    KEEP_ALIVE,

    /** Cancel the update loop and remove the test provider. */
    TEAR_DOWN,

    /** Nothing to do (no active loop to cancel). */
    NO_OP,
}

/**
 * Pure decision for the IDLE/ERROR branch of [MockLocationService.observeLocationState].
 *
 * A group-sync leader must never have its test provider torn down by an *incidental* IDLE
 * transition (e.g. a walk or route replay completing naturally) — only an explicit
 * `stopSpoofing()` call (which removes the provider itself before this collector observes the
 * state change) should end broadcasting to followers. ERROR is always torn down: it represents
 * a genuine unrecoverable failure, not a natural completion, so group sync gets no exception.
 */
internal fun computeIdleOrErrorLoopAction(
    state: MockLocationState,
    leaderSharingEnabled: Boolean,
    hasActiveUpdateJob: Boolean,
): LocationLoopAction =
    when {
        state == MockLocationState.IDLE && leaderSharingEnabled -> LocationLoopAction.KEEP_ALIVE
        hasActiveUpdateJob -> LocationLoopAction.TEAR_DOWN
        else -> LocationLoopAction.NO_OP
    }

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

/** Applies a 2-D Gaussian displacement of [radiusMeters] to ([lat], [lon]) using Box-Muller. */
internal fun gaussianLatLonOffset(
    lat: Double,
    lon: Double,
    radiusMeters: Double,
    random: Random,
): Pair<Double, Double> {
    val u1 = random.nextDouble().coerceAtLeast(Double.MIN_VALUE)
    val u2 = random.nextDouble()
    val mag = radiusMeters * kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1))
    val angle = 2.0 * kotlin.math.PI * u2
    val metersPerDeg = AppConstants.LocationConstants.METERS_PER_LATITUDE_DEGREE
    val dlat = mag * kotlin.math.cos(angle) / metersPerDeg
    val dlon = mag * kotlin.math.sin(angle) / (metersPerDeg * kotlin.math.cos(Math.toRadians(lat)))
    return Pair(lat + dlat, lon + dlon)
}

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

    val metersPerDeg = AppConstants.LocationConstants.METERS_PER_LATITUDE_DEGREE
    return Pair(
        lat + northOffsetM / metersPerDeg,
        lon + eastOffsetM / (metersPerDeg * kotlin.math.cos(Math.toRadians(lat))),
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
 * → position jitter → warm-up accuracy envelope → accuracy perturbation → satellite extras.
 *
 * @param state Immutable snapshot of all service state for this tick.
 * @param nowMs Elapsed realtime ms at the start of the tick, used for the warm-up curve.
 * @param random Source of randomness; pass [Random.Default] in production.
 * @return A completed [LocationFix], or null when [state.isSuspendedPhase] is true (skip this tick).
 */
internal fun buildLocation(
    state: LocationSnapshot,
    nowMs: Long,
    random: Random,
): LocationFix? {
    if (state.isSuspendedPhase) return null

    // Altitude with Gaussian random walk
    val newAltitude =
        if (state.altitudeEnabled) {
            val u1 = random.nextDouble().coerceAtLeast(Double.MIN_VALUE)
            val u2 = random.nextDouble()
            val mag =
                AppConstants.RealismConstants.ALTITUDE_SIGMA_METERS *
                    kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2.0 * kotlin.math.PI * u2)
            (state.altitudeMeters + mag + AppConstants.RealismConstants.ALTITUDE_DRIFT_PER_SECOND_METERS)
                .coerceIn(
                    AppConstants.RealismConstants.DEFAULT_ALTITUDE_METERS -
                        AppConstants.RealismConstants.ALTITUDE_CLAMP_RADIUS_METERS,
                    AppConstants.RealismConstants.DEFAULT_ALTITUDE_METERS +
                        AppConstants.RealismConstants.ALTITUDE_CLAMP_RADIUS_METERS,
                )
        } else {
            AppConstants.RealismConstants.DEFAULT_ALTITUDE_METERS
        }

    // Human-hold offset random walk: ±5% of base per tick, clamped to [50%, 150%] of base
    val humanBase = AppConstants.RealismConstants.ALTITUDE_HUMAN_OFFSET_METERS
    val humanStep = humanBase * AppConstants.RealismConstants.ALTITUDE_HUMAN_OFFSET_JITTER_PCT
    val humanClamp = humanBase * AppConstants.RealismConstants.ALTITUDE_HUMAN_OFFSET_CLAMP_FACTOR
    val newHumanOffset =
        (state.humanAltitudeOffsetMeters + (random.nextDouble() * 2.0 - 1.0) * humanStep)
            .coerceIn(humanBase - humanClamp, humanBase + humanClamp)

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

    // Speed perturbation
    val outSpeed =
        when {
            state.speedMs == 0f && state.speedIdleVariationPct > 0 -> {
                val sigma = state.activeProfileSpeedMs * state.speedIdleVariationPct / 100.0
                (random.nextDouble() * sigma).toFloat().coerceAtLeast(0.01f)
            }

            state.speedMs > 0f && state.speedMovingVariationPct > 0 -> {
                val range = state.speedMs * state.speedMovingVariationPct / 100.0f
                (state.speedMs + (random.nextFloat() - 0.5f) * 2f * range).coerceAtLeast(0f)
            }

            else -> {
                state.speedMs
            }
        }

    // Jitter (position)
    // FOLLOWER with speedMs == 0 is treated like TELEPORT: the position is a placed target,
    // so idle jitter settings govern. When the leader is moving (speedMs > 0), the follower
    // falls into the moving branch below and applies lateral jitter using the leader's bearing.
    val (outLat, outLon) =
        when {
            (
                state.mode == MockMode.TELEPORT ||
                    (state.mode == MockMode.FOLLOWER && state.speedMs == 0f)
            ) &&
                state.shouldApplyIdleJitter &&
                state.jitterIdleRadiusMeters > 0.0 -> {
                gaussianLatLonOffset(state.latitude, state.longitude, state.jitterIdleRadiusMeters, random)
            }

            state.mode != MockMode.TELEPORT && state.shouldApplyMovingJitter &&
                state.jitterMovingRadiusMeters > 0.0 -> {
                if (state.speedMs > 0f) {
                    gaussianLatLonOffsetLateral(
                        state.latitude,
                        state.longitude,
                        state.jitterMovingRadiusMeters,
                        state.bearing,
                        AppConstants.JitterConstants.LONGITUDINAL_JITTER_FRACTION,
                        random,
                    )
                } else {
                    gaussianLatLonOffset(state.latitude, state.longitude, state.jitterMovingRadiusMeters, random)
                }
            }

            else -> {
                Pair(state.latitude, state.longitude)
            }
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

    return LocationFix(
        latitude = outLat,
        longitude = outLon,
        altitudeMeters = newAltitude + newHumanOffset,
        humanAltitudeOffsetMeters = newHumanOffset,
        speedMs = outSpeed,
        bearing = outBearing,
        accuracyMeters = outAccuracy,
        verticalAccuracyMeters = AppConstants.RealismConstants.VERTICAL_ACCURACY_METERS,
        bearingAccuracyDegrees = AppConstants.RealismConstants.BEARING_ACCURACY_DEGREES,
        speedAccuracyMps = AppConstants.RealismConstants.SPEED_ACCURACY_MPS,
        satelliteCount = if (state.satelliteExtrasEnabled) state.cachedSatelliteCount else null,
        usedInFixCount = if (state.satelliteExtrasEnabled) state.cachedUsedInFixCount else null,
    )
}
