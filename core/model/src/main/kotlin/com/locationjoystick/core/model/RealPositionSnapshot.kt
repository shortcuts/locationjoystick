package com.locationjoystick.core.model

data class RealPositionSnapshot(
    val position: LatLng,
    val takenAtMs: Long,
)
