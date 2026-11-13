package com.locationjoystick.feature.settings.impl

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.AppSettings
import com.locationjoystick.core.model.SpeedProfile

sealed interface SettingsScreenState {
    data object Loading : SettingsScreenState

    data class Loaded(
        val state: SettingsUiState,
    ) : SettingsScreenState
}

/** [settings] holds every persisted setting; the other fields are what [AppSettings] lacks. */
data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val speeds: Map<String, Double> = SpeedProfile.defaultProfiles().associate { it.id to it.speedMetersPerSecond },
    val jitterIdleRadiusMeters: Double = AppConstants.JitterConstants.DEFAULT_IDLE_RADIUS_METERS,
    val jitterMovingRadiusMeters: Double = 1.0,
    val jitterMaxStepMeters: Double = AppConstants.JitterConstants.DEFAULT_STEP_METERS_PER_TICK,
    val jitterSpeedIdleVariationPct: Int = AppConstants.JitterConstants.SPEED_IDLE_VARIATION_PCT_DEFAULT,
    val jitterSpeedIdleWobbleProbabilityPct: Int = AppConstants.JitterConstants.SPEED_IDLE_WOBBLE_PROBABILITY_PCT_DEFAULT,
    val jitterSpeedMovingVariationPct: Int = AppConstants.JitterConstants.SPEED_MOVING_VARIATION_PCT_DEFAULT,
    val hotLocationsEnabled: Boolean = false,
    val selectedHotLocationIds: Set<String> = emptySet(),
    val hotRoutesEnabled: Boolean = false,
    val selectedHotRouteIds: Set<String> = emptySet(),
    val isScreenServiceGranted: Boolean = false,
    val accessibilityDisclosureAccepted: Boolean = false,
    val hasAltitudeOverride: Boolean = false,
    val captureModeEnabled: Boolean = false,
    val captureEnabled: Boolean = false,
    val jumpEnabled: Boolean = false,
    val capturePreviousBrowserPackage: String? = null,
    val captureSetupReset: Boolean = false,
    val launchAfterLinkPackage: String? = null,
    val launchAfterLinkChosen: Boolean = false,
)
