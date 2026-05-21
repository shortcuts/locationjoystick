package com.locationjoystick.feature.settings.impl

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.SpeedProfile
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.core.model.WidgetFeature

data class SettingsUiState(
    val isLoading: Boolean = true,
    val walkSpeed: Double = SpeedProfile.WALK_SPEED_MPS,
    val runSpeed: Double = SpeedProfile.RUN_SPEED_MPS,
    val bikeSpeed: Double = SpeedProfile.BIKE_SPEED_MPS,
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val enabledWidgetFeatures: Set<WidgetFeature> = WidgetFeature.entries.toSet(),
    val rememberLastLocation: Boolean = true,
    val mapFollowsLocation: Boolean = true,
    val jitterIdleRadiusMeters: Double = 0.0,
    val jitterMovingRadiusMeters: Double = 1.0,
    val jitterIntervalSeconds: Int = 3,
    val realismBearingHoldIdle: Boolean = AppConstants.RealismConstants.BEARING_HOLD_ON_IDLE_DEFAULT,
    val realismAltitudeEnabled: Boolean = AppConstants.RealismConstants.ALTITUDE_ENABLED_DEFAULT,
    val realismWarmupEnabled: Boolean = AppConstants.RealismConstants.WARMUP_ENABLED_DEFAULT,
    val realismSatelliteExtrasEnabled: Boolean = AppConstants.RealismConstants.SATELLITE_EXTRAS_ENABLED_DEFAULT,
    val realismSuspendedMockingEnabled: Boolean = AppConstants.RealismConstants.SUSPENDED_MOCKING_ENABLED_DEFAULT,
    val isDirty: Boolean = false,
)
