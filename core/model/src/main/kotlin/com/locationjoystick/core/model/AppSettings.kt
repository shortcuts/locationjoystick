package com.locationjoystick.core.model

data class AppSettings(
    val activeSpeedProfileId: String = "walk",
    val joystickStyle: JoystickStyle = JoystickStyle.FLOATING,
    val enabledWidgetFeatures: List<WidgetFeature> = listOf(
        WidgetFeature.SPEED_PROFILE,
        WidgetFeature.MOCK_STATE,
    ),
    val mapFollowsLocation: Boolean = true,
    val useRoadSnappingByDefault: Boolean = false,
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
)
