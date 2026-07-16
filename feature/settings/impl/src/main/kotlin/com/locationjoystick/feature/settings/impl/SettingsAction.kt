package com.locationjoystick.feature.settings.impl

import com.locationjoystick.core.model.AppFeature
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.core.model.ThemeMode

internal sealed class SettingsAction {
    data class SetSlowWalkSpeed(
        val displaySpeed: Double,
    ) : SettingsAction()

    data class SetWalkSpeed(
        val displaySpeed: Double,
    ) : SettingsAction()

    data class SetRunSpeed(
        val displaySpeed: Double,
    ) : SettingsAction()

    data class SetBikeSpeed(
        val displaySpeed: Double,
    ) : SettingsAction()

    data class SetDriveSpeed(
        val displaySpeed: Double,
    ) : SettingsAction()

    data class SetSpeedUnit(
        val unit: SpeedUnit,
    ) : SettingsAction()

    data class SetWidgetFeatures(
        val features: Set<AppFeature>,
    ) : SettingsAction()

    data class SetFeatureOrder(
        val order: List<AppFeature>,
    ) : SettingsAction()

    data class SetRememberLastLocation(
        val enabled: Boolean,
    ) : SettingsAction()

    data class SetMapFollowsLocation(
        val enabled: Boolean,
    ) : SettingsAction()

    data class SetJitterIdleRadius(
        val meters: Double,
    ) : SettingsAction()

    data class SetJitterMovingRadius(
        val meters: Double,
    ) : SettingsAction()

    data class SetJitterIntervalSeconds(
        val seconds: Int,
    ) : SettingsAction()

    data class SetJitterIdleIntervalSeconds(
        val seconds: Int,
    ) : SettingsAction()

    data class SetRealismBearingHoldIdle(
        val enabled: Boolean,
    ) : SettingsAction()

    data class SetRealismAltitudeEnabled(
        val enabled: Boolean,
    ) : SettingsAction()

    data class SetRealismWarmupEnabled(
        val enabled: Boolean,
    ) : SettingsAction()

    data class SetRealismSatelliteExtrasEnabled(
        val enabled: Boolean,
    ) : SettingsAction()

    data class SetRealismSuspendedMockingEnabled(
        val enabled: Boolean,
    ) : SettingsAction()

    data class SetRealismPedometerMockingEnabled(
        val enabled: Boolean,
    ) : SettingsAction()

    data class SetJitterSpeedIdleVariationPct(
        val pct: Int,
    ) : SettingsAction()

    data class SetJitterSpeedMovingVariationPct(
        val pct: Int,
    ) : SettingsAction()

    data class SetHotLocationsEnabled(
        val enabled: Boolean,
    ) : SettingsAction()

    data class SetSelectedHotLocationIds(
        val ids: Set<String>,
    ) : SettingsAction()

    data class SetHotRoutesEnabled(
        val enabled: Boolean,
    ) : SettingsAction()

    data class SetSelectedHotRouteIds(
        val ids: Set<String>,
    ) : SettingsAction()

    data class SetMapFeatures(
        val features: Set<AppFeature>,
    ) : SettingsAction()

    data class UpdateRoamingDefaults(
        val defaults: RoamingDefaults,
    ) : SettingsAction()

    data object Export : SettingsAction()

    data object Import : SettingsAction()

    data object ImportGpsJoystick : SettingsAction()

    data object ImportYamla : SettingsAction()

    data object QrShare : SettingsAction()

    data object QrScan : SettingsAction()

    data object QrEnterCode : SettingsAction()

    data class SetFloatingMapQuickWalk(
        val enabled: Boolean,
    ) : SettingsAction()

    data class SetTapToWalkOverlayEnabled(
        val enabled: Boolean,
    ) : SettingsAction()

    data class SetTapToWalkScaleMpx(
        val scale: Double,
    ) : SettingsAction()

    data class SetCompassTrackingEnabled(
        val enabled: Boolean,
    ) : SettingsAction()

    data class SetCompassRegion(
        val cx: Float,
        val cy: Float,
        val radius: Float,
    ) : SettingsAction()

    data class SetThemeMode(
        val mode: ThemeMode,
    ) : SettingsAction()

    data object SaveChanges : SettingsAction()

    data object DiscardChanges : SettingsAction()

    data object ResetAllData : SettingsAction()
}
