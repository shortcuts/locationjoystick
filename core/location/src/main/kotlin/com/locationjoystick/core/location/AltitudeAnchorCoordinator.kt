package com.locationjoystick.core.location

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.ElevationRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Owns the altitude-anchor convergence state (real-elevation lookup + manual override), extracted
 * from [MockLocationService], mirroring [FollowerCatchUpCoordinator]: state ownership + per-tick
 * step logic live in one small class instead of scattered @Volatile fields on the service.
 */
internal class AltitudeAnchorCoordinator(
    private val elevationRepository: ElevationRepository,
    private val settingsRepository: SettingsRepository,
    private val locationRepository: LocationRepository,
) {
    /** Clamp-center anchor for the altitude Gaussian walk; converges toward [targetBaseAltitudeMeters] each tick. */
    @Volatile private var currentBaseAltitudeMeters: Double = AppConstants.RealismConstants.DEFAULT_ALTITUDE_METERS

    /** Where [currentBaseAltitudeMeters] is converging to — set by a real-elevation fetch or a manual override. */
    @Volatile private var targetBaseAltitudeMeters: Double = AppConstants.RealismConstants.DEFAULT_ALTITUDE_METERS

    /** Wall-clock ms of the last real-elevation fetch attempt; gates [maybeFetchElevation]'s interval check. */
    @Volatile private var lastElevationFetchMs: Long = 0L

    /** Guards against stacking a new elevation fetch while one is still in flight. */
    @Volatile private var elevationFetchInFlight: Boolean = false

    /** Resets both anchor and target to the flat default — called from `startSpoofing()`. */
    fun reset() {
        currentBaseAltitudeMeters = AppConstants.RealismConstants.DEFAULT_ALTITUDE_METERS
        targetBaseAltitudeMeters = AppConstants.RealismConstants.DEFAULT_ALTITUDE_METERS
    }

    /**
     * Re-applies an existing manual override at spoof start. Resolves async (DataStore read) — the
     * live [observe] collector re-applies it instantly anyway once running, and [maybeFetchElevation]
     * re-checks the override before every fetch, so the sliver of a window before this completes is
     * harmless.
     */
    fun applyPendingOverride(scope: CoroutineScope) {
        scope.launch {
            settingsRepository.getBaseAltitudeOverride().first()?.let { override ->
                currentBaseAltitudeMeters = override
                targetBaseAltitudeMeters = override
            }
        }
    }

    /**
     * Starts an async real-elevation fetch if due: not already in flight, [realElevationEnabled],
     * the fetch interval has elapsed, and no manual override is active (checked again inside the
     * launched coroutine, since the override may have been set since this call started). Shared by
     * `startSpoofing()` and every tick's [stepConverge] call so "fetch now if due" is one code path.
     *
     * [instant], set by `startSpoofing()`'s and teleport's fetches, jumps [currentBaseAltitudeMeters]
     * straight to the fetched value instead of leaving it for [stepConverge] to approach gradually
     * — nothing has been reported at the new position yet at that point, so there's no jump to
     * smooth over (issue #51). [force] skips the fetch-interval throttle for the same reason a
     * teleport needs it: the position just changed outright, so "last fetch was under 60s ago" (for
     * the *old* position) shouldn't block looking up the new one.
     */
    fun maybeFetchElevation(
        scope: CoroutineScope,
        nowMs: Long,
        lat: Double,
        lon: Double,
        realElevationEnabled: Boolean,
        instant: Boolean = false,
        force: Boolean = false,
    ) {
        if (elevationFetchInFlight) return
        if (!realElevationEnabled) return
        if (!force && nowMs - lastElevationFetchMs < AppConstants.RealismConstants.ELEVATION_FETCH_INTERVAL_MS) return
        lastElevationFetchMs = nowMs
        elevationFetchInFlight = true
        scope.launch {
            if (settingsRepository.getBaseAltitudeOverride().first() == null) {
                elevationRepository.fetchElevationMeters(lat, lon)?.let {
                    val jittered = jitterElevationReading(it, Random.Default)
                    targetBaseAltitudeMeters = jittered
                    if (instant) currentBaseAltitudeMeters = jittered
                }
            }
            elevationFetchInFlight = false
        }
    }

    /** Steps the anchor toward its target by at most [maxStep] and returns the new anchor value. */
    fun stepConverge(maxStep: Double): Double {
        currentBaseAltitudeMeters = stepToward(currentBaseAltitudeMeters, targetBaseAltitudeMeters, maxStep)
        return currentBaseAltitudeMeters
    }

    /**
     * Applies a manual altitude override (widget button) to a running session immediately —
     * DataStore write -> Flow emission -> this collector — with no Intent/service command needed.
     * Skipped when no override exists yet (null -> no-op) so it never fights [applyPendingOverride]'s
     * own resolution order at `startSpoofing()`.
     */
    fun observe(scope: CoroutineScope) {
        scope.launch {
            settingsRepository.getBaseAltitudeOverride().collect { override ->
                if (override != null) {
                    currentBaseAltitudeMeters = override
                    targetBaseAltitudeMeters = override
                    locationRepository.setReportedAltitude(override)
                }
            }
        }
    }
}

/** Moves [current] toward [target] by at most [maxStep], reaching it exactly once within range. */
internal fun stepToward(
    current: Double,
    target: Double,
    maxStep: Double,
): Double {
    val delta = target - current
    return if (kotlin.math.abs(delta) <= maxStep) target else current + maxStep * kotlin.math.sign(delta)
}

/** Adds a small random sub-meter offset to a DEM elevation reading so it's never an exact round number. */
internal fun jitterElevationReading(
    elevationMeters: Double,
    random: Random,
): Double =
    elevationMeters +
        random.nextDouble(
            -AppConstants.RealismConstants.ELEVATION_FRACTIONAL_JITTER_METERS,
            AppConstants.RealismConstants.ELEVATION_FRACTIONAL_JITTER_METERS,
        )
