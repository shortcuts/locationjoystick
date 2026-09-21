package com.locationjoystick.core.model

data class RoamingDefaults(
    val radiusMeters: Double = 500.0,
    val distanceMeters: Double = 1_000.0,
    val speedProfileId: String = "walk",
    val followRoads: Boolean = true,
    val returnToInitialLocation: Boolean = true,
    val kind: RoamingKind = RoamingKind.WALK_AROUND,
    val plantingStartRadiusMeters: Double = 5.0,
    val plantingEndRadiusMeters: Double = 39.0,
    val plantingInfiniteLoops: Boolean = true,
    val plantingLoopCount: Int = 1,
    /** Planting's own start speed. Independent of walk-around [speedProfileId]. Default Bike. */
    val plantingSpeedProfileId: String = "bike",
)

/** Speed that should start a session of [kind]. Live widget/app changes override after start. */
fun RoamingDefaults.speedProfileIdForKind(): String = if (kind == RoamingKind.PLANTING) plantingSpeedProfileId else speedProfileId

/**
 * Converts defaults into a [RoamingConfig] for a specific [centerPosition].
 * This is the single translation point for the followRoads → useRoadSnapping rename
 * and for resolving planting vs walk-around speed.
 */
fun RoamingDefaults.toConfig(centerPosition: LatLng): RoamingConfig =
    RoamingConfig(
        centerPosition = centerPosition,
        radiusMeters = radiusMeters,
        distanceMeters = distanceMeters,
        speedProfileId = speedProfileIdForKind(),
        useRoadSnapping = followRoads,
        returnToInitialLocation = returnToInitialLocation,
        kind = kind,
        plantingStartRadiusMeters = plantingStartRadiusMeters,
        plantingEndRadiusMeters = plantingEndRadiusMeters,
        plantingInfiniteLoops = plantingInfiniteLoops,
        plantingLoopCount = plantingLoopCount,
    )
