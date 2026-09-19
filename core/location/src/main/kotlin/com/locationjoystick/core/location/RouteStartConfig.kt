package com.locationjoystick.core.location

import com.locationjoystick.core.common.constants.AppConstants

/**
 * Bundles the route-start sheet's flags so callers cannot silently swap two adjacent
 * booleans — see docs/features/routes.md, "Start Flow".
 */
data class RouteStartConfig(
    val isLooping: Boolean = false,
    val isReverse: Boolean = false,
    val isReturnToLocation: Boolean = false,
    val followRoadsToStart: Boolean = false,
    val isPlanting: Boolean = false,
    val teleportBetweenWaypoints: Boolean = false,
    val teleportBetweenDelaySeconds: Int = AppConstants.RouteConstants.TELEPORT_BETWEEN_DEFAULT_DELAY_SECONDS,
)
