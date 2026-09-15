package com.locationjoystick.core.common.util

import com.locationjoystick.core.common.constants.AppConstants

fun clampTeleportBetweenDelaySeconds(seconds: Int): Int =
    seconds.coerceIn(
        AppConstants.RouteConstants.TELEPORT_BETWEEN_MIN_DELAY_SECONDS,
        AppConstants.RouteConstants.TELEPORT_BETWEEN_MAX_DELAY_SECONDS,
    )

/**
 * How long to stay at a hopped stop. Zero seconds still waits one GPS tick so the hop
 * cannot burst through the whole route in a single frame.
 */
fun hopLingerDurationMs(delaySeconds: Int): Long {
    val seconds = clampTeleportBetweenDelaySeconds(delaySeconds)
    val requestedMs = seconds * 1000L
    return maxOf(AppConstants.LocationConstants.UPDATE_INTERVAL_MS, requestedMs)
}

fun parseTeleportBetweenDelaySeconds(text: String): Int =
    clampTeleportBetweenDelaySeconds(
        text.toIntOrNull() ?: AppConstants.RouteConstants.TELEPORT_BETWEEN_DEFAULT_DELAY_SECONDS,
    )
