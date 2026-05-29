package com.locationjoystick.core.model

data class RoamingConfig(
    val centerPosition: LatLng,
    val radiusMeters: Double,
    val distanceMeters: Double,
    val speedProfileId: String = "walk",
    val useRoadSnapping: Boolean = false,
    val returnToInitialLocation: Boolean = true,
    /** Pre-computed first-leg waypoints from a preview. When non-null, the engine uses this route
     * for the first segment instead of generating a new random destination. */
    val previewWaypoints: List<LatLng>? = null,
)
