package com.locationjoystick.core.model

/** How a route replay behaves at its last stop: stop, restart from the first stop, or turn around. */
enum class LoopMode { OFF, RESTART, WALK_BACK }

/**
 * Bundles the route-start sheet's flags so callers cannot silently swap two adjacent
 * booleans — see docs/features/routes.md, "Start Flow". `StartRouteReplayUseCase` derives
 * [teleportToStart] and [teleportBetweenWaypoints]; callers of the use case do not set them.
 */
data class RouteStartConfig(
    val loopMode: LoopMode = LoopMode.OFF,
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
