package com.locationjoystick.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.designsystem.R

/** Resolves a string resource id plus format args to text. */
typealias StringLookup = (Int, Array<Any>) -> String

@Composable
fun rememberCooldownStringLookup(): StringLookup {
    val resources = LocalResources.current
    return remember(resources) { { id, args -> resources.getString(id, *args) } }
}

/** "45s · 500 m teleport", localized. */
fun cooldownAdvisoryLabel(
    remainingSeconds: Long,
    distanceMeters: Double,
    getString: StringLookup,
): String {
    val hours = remainingSeconds / AppConstants.TimeConstants.SECONDS_PER_HOUR
    val minutes = (remainingSeconds % AppConstants.TimeConstants.SECONDS_PER_HOUR) / AppConstants.TimeConstants.SECONDS_PER_MINUTE
    val seconds = remainingSeconds % AppConstants.TimeConstants.SECONDS_PER_MINUTE
    val timeLabel =
        when {
            hours > 0 -> getString(R.string.cooldown_time_hours_minutes, arrayOf(hours, minutes))
            minutes > 0 -> getString(R.string.cooldown_time_minutes_seconds, arrayOf(minutes, seconds))
            else -> getString(R.string.cooldown_time_seconds, arrayOf(seconds))
        }
    return getString(R.string.cooldown_advisory_label, arrayOf(timeLabel, formatDistance(distanceMeters, getString)))
}

/**
 * Badge text: suggested wait when [cooling] (remainingSeconds to distanceMeters) is set,
 * else distance away when [distanceToTargetMeters] is known, else "no wait needed".
 */
fun cooldownBadgeText(
    cooling: Pair<Long, Double>?,
    distanceToTargetMeters: Double?,
    getString: StringLookup,
): String =
    when {
        cooling != null ->
            getString(
                R.string.cooldown_badge_suggested_wait,
                arrayOf(cooldownAdvisoryLabel(cooling.first, cooling.second, getString)),
            )
        distanceToTargetMeters != null ->
            getString(R.string.cooldown_badge_distance_away, arrayOf(formatDistance(distanceToTargetMeters, getString)))
        else -> getString(R.string.cooldown_badge_no_wait, emptyArray())
    }

private fun formatDistance(
    meters: Double,
    getString: StringLookup,
): String =
    if (meters >= 1000.0) {
        getString(R.string.cooldown_distance_km, arrayOf(meters / 1000.0))
    } else {
        getString(R.string.cooldown_distance_m, arrayOf(meters))
    }
