package com.locationjoystick.feature.settings.impl

import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.core.model.WidgetFeature

data class SettingsUiState(
    val isLoading: Boolean = true,
    val walkSpeed: Double = 0.556,
    val runSpeed: Double = 2.222,
    val bikeSpeed: Double = 4.167,
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val enabledWidgetFeatures: Set<WidgetFeature> = WidgetFeature.entries.toSet(),
    val isDirty: Boolean = false,
)
