package com.locationjoystick.feature.settings.impl

import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.core.model.WidgetFeature

data class SettingsUiState(
    val isLoading: Boolean = true,
    val walkSpeed: Double = 1.4,
    val runSpeed: Double = 3.0,
    val bikeSpeed: Double = 5.5,
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val enabledWidgetFeatures: Set<WidgetFeature> = WidgetFeature.entries.toSet(),
)
