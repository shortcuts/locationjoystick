package com.locationjoystick.core.model

sealed class ElevationMode {
    data object Neutral : ElevationMode()
    data object TiltUp : ElevationMode()
    data object TiltDown : ElevationMode()
}
