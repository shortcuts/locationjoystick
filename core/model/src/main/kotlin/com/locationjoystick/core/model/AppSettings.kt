package com.locationjoystick.core.model

data class AppSettings(
    val activeSpeedProfileId: String = "walk",
    val joystickStyle: JoystickStyle = JoystickStyle.FLOATING,
    val enabledWidgetFeatures: List<WidgetFeature> =
        listOf(
            WidgetFeature.JOYSTICK_TOGGLE,
            WidgetFeature.SPEED_CYCLE,
        ),
    val mapFollowsLocation: Boolean = true,
    val useRoadSnappingByDefault: Boolean = false,
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val roamingDefaults: RoamingDefaults = RoamingDefaults(),
    val bearingHoldOnIdle: Boolean = true,
    val altitudeEnabled: Boolean = true,
    val warmupEnabled: Boolean = false,
    val satelliteExtrasEnabled: Boolean = true,
    val suspendedMockingEnabled: Boolean = false,
)
