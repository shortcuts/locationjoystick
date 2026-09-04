package com.locationjoystick.core.location

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Holds the GPS-realism settings that [MockLocationService] observes from [SettingsRepository] and
 * reads each tick in [MockLocationService.captureSnapshot]. Extracted from the service so the ~15
 * settings-collection coroutines and their backing `@Volatile` fields live in one cohesive place.
 *
 * All fields are `@Volatile` because they are written from collection coroutines (running on an
 * arbitrary dispatcher) and read from the tick loop. Each field maps 1:1 to a `SettingsRepository`
 * flow wired up in [observe].
 */
internal class RealismSettingsState {
    @Volatile var jitterIdleRadiusMeters: Double = 0.0
        private set

    @Volatile var jitterMovingRadiusMeters: Double = 1.0
        private set

    @Volatile var jitterMaxStepMeters: Double = AppConstants.JitterConstants.DEFAULT_STEP_METERS_PER_TICK
        private set

    @Volatile var bearingHoldEnabled: Boolean = AppConstants.RealismConstants.BEARING_HOLD_ON_IDLE_DEFAULT
        private set

    @Volatile var altitudeEnabled: Boolean = AppConstants.RealismConstants.ALTITUDE_ENABLED_DEFAULT
        private set

    @Volatile var warmupEnabled: Boolean = AppConstants.RealismConstants.WARMUP_ENABLED_DEFAULT
        private set

    @Volatile var satelliteExtrasEnabled: Boolean = AppConstants.RealismConstants.SATELLITE_EXTRAS_ENABLED_DEFAULT
        private set

    @Volatile var suspendedMockingEnabled: Boolean = AppConstants.RealismConstants.SUSPENDED_MOCKING_ENABLED_DEFAULT
        private set

    @Volatile var rememberLastLocation: Boolean = false
        private set

    @Volatile var speedIdleVariationPct: Int = AppConstants.JitterConstants.SPEED_IDLE_VARIATION_PCT_DEFAULT
        private set

    @Volatile var speedIdleWobbleProbabilityPct: Int =
        AppConstants.JitterConstants.SPEED_IDLE_WOBBLE_PROBABILITY_PCT_DEFAULT
        private set

    @Volatile var speedMovingVariationPct: Int = AppConstants.JitterConstants.SPEED_MOVING_VARIATION_PCT_DEFAULT
        private set

    @Volatile var activeProfileSpeedMs: Double = AppConstants.ProfileConstants.WALK_SPEED_MPS
        private set

    @Volatile var realElevationEnabled: Boolean = AppConstants.RealismConstants.REAL_ELEVATION_ENABLED_DEFAULT
        private set

    @Volatile var altitudeJitterRadiusMeters: Double = AppConstants.RealismConstants.ALTITUDE_SIGMA_METERS
        private set

    /**
     * Launches one collection coroutine per realism setting on [scope]. Each coroutine writes its
     * `@Volatile` field as the source flow emits. Mirrors the prior inline wiring in
     * `MockLocationService.observeLocationState()` exactly.
     */
    fun observe(
        scope: CoroutineScope,
        settingsRepository: SettingsRepository,
    ) {
        scope.collectInto(settingsRepository.getJitterIdleRadius()) { jitterIdleRadiusMeters = it }
        scope.collectInto(settingsRepository.getJitterMovingRadius()) { jitterMovingRadiusMeters = it }
        scope.collectInto(settingsRepository.getJitterMaxStepMeters()) { jitterMaxStepMeters = it }
        scope.collectInto(settingsRepository.getRealismBearingHoldIdle()) { bearingHoldEnabled = it }
        scope.collectInto(settingsRepository.getRealismAltitudeEnabled()) { altitudeEnabled = it }
        scope.collectInto(settingsRepository.getRealismWarmupEnabled()) { warmupEnabled = it }
        scope.collectInto(settingsRepository.getRealismSatelliteExtrasEnabled()) { satelliteExtrasEnabled = it }
        scope.collectInto(settingsRepository.getRealismSuspendedMockingEnabled()) { suspendedMockingEnabled = it }
        scope.collectInto(settingsRepository.getRememberLastLocation()) { rememberLastLocation = it }
        scope.collectInto(settingsRepository.getJitterSpeedIdleVariationPct()) { speedIdleVariationPct = it }
        scope.collectInto(settingsRepository.getJitterSpeedIdleWobbleProbabilityPct()) {
            speedIdleWobbleProbabilityPct = it
        }
        scope.collectInto(settingsRepository.getJitterSpeedMovingVariationPct()) { speedMovingVariationPct = it }
        scope.collectInto(settingsRepository.getActiveSpeedProfile()) { activeProfileSpeedMs = it.speedMetersPerSecond }
        scope.collectInto(settingsRepository.getRealismRealElevationEnabled()) { realElevationEnabled = it }
        scope.collectInto(settingsRepository.getAltitudeJitterRadius()) { altitudeJitterRadiusMeters = it }
    }

    private fun <T> CoroutineScope.collectInto(
        flow: Flow<T>,
        assign: (T) -> Unit,
    ) {
        launch { flow.collect(assign) }
    }
}
