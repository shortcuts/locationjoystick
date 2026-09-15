package com.locationjoystick.core.model

data class RoamingConfig(
    val centerPosition: LatLng,
    val radiusMeters: Double,
    val distanceMeters: Double,
    val speedProfileId: String = "walk",
    val useRoadSnapping: Boolean = false,
    val returnToInitialLocation: Boolean = true,
    /** Pre-planned full route waypoints. When non-null, the engine walks this route directly. */
    val plannedWaypoints: List<LatLng>? = null,
    val kind: RoamingKind = RoamingKind.WALK_AROUND,
    val plantingStartRadiusMeters: Double = 5.0,
    val plantingEndRadiusMeters: Double = 39.0,
    val plantingInfiniteLoops: Boolean = true,
    val plantingLoopCount: Int = 1,
)
