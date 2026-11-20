package com.locationjoystick.core.designsystem.component

import android.text.format.DateFormat
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.designsystem.LjSpacing
import com.locationjoystick.core.designsystem.R
import com.locationjoystick.core.designsystem.tabular
import com.locationjoystick.core.model.LatLng
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.util.Date

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
    val timeLabel = cooldownDurationLabel(remainingSeconds, getString)
    return getString(R.string.cooldown_advisory_label, arrayOf(timeLabel, formatDistance(distanceMeters, getString)))
}

/** "1m 20s" until [endText], localized. */
fun cooldownStandingLabel(
    remainingSeconds: Long,
    endText: String,
    getString: StringLookup,
): String = getString(R.string.cooldown_standing_label, arrayOf(cooldownDurationLabel(remainingSeconds, getString), endText))

private fun cooldownDurationLabel(
    remainingSeconds: Long,
    getString: StringLookup,
): String {
    val hours = remainingSeconds / AppConstants.TimeConstants.SECONDS_PER_HOUR
    val minutes = (remainingSeconds % AppConstants.TimeConstants.SECONDS_PER_HOUR) / AppConstants.TimeConstants.SECONDS_PER_MINUTE
    val seconds = remainingSeconds % AppConstants.TimeConstants.SECONDS_PER_MINUTE
    return when {
        hours > 0 -> getString(R.string.cooldown_time_hours_minutes, arrayOf(hours, minutes))
        minutes > 0 -> getString(R.string.cooldown_time_minutes_seconds, arrayOf(minutes, seconds))
        else -> getString(R.string.cooldown_time_seconds, arrayOf(seconds))
    }
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

/** Live cooldown badge for a teleport to [target]; draws nothing without a target or a [cooldownFor] source. */
@Composable
fun TeleportCooldownBadge(
    target: LatLng?,
    cooldownFor: ((LatLng) -> Flow<Pair<Long, Double>?>)?,
    modifier: Modifier = Modifier,
) {
    if (target == null || cooldownFor == null) return
    val cooling by remember(target) { cooldownFor(target) }.collectAsStateWithLifecycle(initialValue = null)
    CooldownAdvisoryBadge(cooldownBadgeText(cooling, null, rememberCooldownStringLookup()), modifier)
}

/** Standing countdown and finish time for the last teleport's cooldown; draws nothing once [endMs] has passed. */
@Composable
fun TeleportStandingCooldown(
    endMs: Long,
    modifier: Modifier = Modifier,
) {
    val now by produceState(System.currentTimeMillis(), endMs) {
        while (value < endMs) {
            delay(1000L)
            value = System.currentTimeMillis()
        }
    }
    if (now >= endMs) return
    val context = LocalContext.current
    val endText = remember(endMs) { DateFormat.getTimeFormat(context).format(Date(endMs)) }
    val label = cooldownStandingLabel((endMs - now + 999L) / 1000L, endText, rememberCooldownStringLookup())
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = LjSpacing.smMd, vertical = LjSpacing.xs),
        )
    }
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
