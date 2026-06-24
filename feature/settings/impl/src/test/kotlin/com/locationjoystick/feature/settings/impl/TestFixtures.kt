package com.locationjoystick.feature.settings.impl

import com.locationjoystick.core.model.AppSettings
import com.locationjoystick.core.model.ExportData
import com.locationjoystick.core.model.JoystickStyle
import com.locationjoystick.core.model.SpeedUnit

internal fun minimalExportData(): ExportData =
    ExportData(
        schemaVersion = 1,
        exportedAt = 0L,
        settings =
            AppSettings(
                activeSpeedProfileId = "walk",
                joystickStyle = JoystickStyle.FLOATING,
                enabledWidgetFeatures = emptySet(),
                mapFollowsLocation = true,
                useRoadSnappingByDefault = false,
                speedUnit = SpeedUnit.KMH,
            ),
        speedProfiles = emptyList(),
        routes = emptyList(),
        favoriteLocations = emptyList(),
        jitterIdleRadius = 0.0,
        jitterMovingRadius = 1.0,
        jitterIntervalSeconds = 3,
    )
