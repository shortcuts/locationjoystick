package com.locationjoystick.feature.settings.impl

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
    val jitterIdleRadiusMeters: Double = 0.0,
    val jitterMovingRadiusMeters: Double = 10.0,
    val isDirty: Boolean = false,
)
