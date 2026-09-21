package com.locationjoystick.core.model

/**
 * Bundles the route-start sheet's flags so callers cannot silently swap two adjacent
 * booleans — see docs/features/routes.md, "Start Flow". `StartRouteReplayUseCase` derives
 * [teleportToStart] and [teleportBetweenWaypoints]; callers of the use case do not set them.
 */
data class RouteStartConfig(
    val isLooping: Boolean = false,
    val isReverse: Boolean = false,
    val isReturnToLocation: Boolean = false,
    val teleportToStart: Boolean = false,
    val followRoadsToStart: Boolean = false,
    val isPlanting: Boolean = false,
    val teleportBetweenWaypoints: Boolean = false,
    // Cannot use AppConstants — core:model is a pure JVM module with no core:common dependency.
    // Keep in sync with AppConstants.RouteConstants.TELEPORT_BETWEEN_DEFAULT_DELAY_SECONDS.
    val teleportBetweenDelaySeconds: Int = 8,
)
