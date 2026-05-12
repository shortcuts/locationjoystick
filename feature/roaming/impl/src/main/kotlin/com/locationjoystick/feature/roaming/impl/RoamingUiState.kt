package com.locationjoystick.feature.roaming.impl

import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RoamingConfig

data class RoamingUiState(
    val config: RoamingConfig =
        RoamingConfig(
            centerPosition = LatLng(0.0, 0.0),
            radiusMeters = 2000.0,
            durationSeconds = 1800L,
            useRoadSnapping = false,
        ),
    val isRoaming: Boolean = false,
    val elapsedSeconds: Long = 0L,
    val currentPosition: LatLng? = null,
    val transportMode: String = "walk",
)
