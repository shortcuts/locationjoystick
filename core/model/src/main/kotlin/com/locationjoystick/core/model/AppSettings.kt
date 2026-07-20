package com.locationjoystick.core.model

data class AppSettings(
    val activeSpeedProfileId: String = "walk",
    val enabledSpeedProfileIds: Set<String> = setOf("walk", "run", "bike"),
    val joystickStyle: JoystickStyle = JoystickStyle.FLOATING,
    val featureOrder: List<AppFeature> = AppFeature.DEFAULT_ORDER,
    val enabledWidgetFeatures: Set<AppFeature> = AppFeature.DEFAULT_WIDGET_ENABLED,
    val enabledMapFeatures: Set<AppFeature> = AppFeature.DEFAULT_MAP_ENABLED,
    val mapFollowsLocation: Boolean = true,
    val useRoadSnappingByDefault: Boolean = false,
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val roamingDefaults: RoamingDefaults = RoamingDefaults(),
    val bearingHoldOnIdle: Boolean = true,
    val altitudeEnabled: Boolean = true,
    val warmupEnabled: Boolean = false,
    val satelliteExtrasEnabled: Boolean = true,
    val suspendedMockingEnabled: Boolean = false,
    val pedometerMockingEnabled: Boolean = false,
)
