package com.locationjoystick.core.data

import com.locationjoystick.core.common.constants.AppConstants
import java.util.Locale

/** Represents whether a cooldown advisory is in effect for a teleport action. */
sealed class CooldownState {
    /** No cooldown — safe to teleport. */
    data object Ready : CooldownState()

    /**
     * A cooldown is suggested before teleporting.
     *
     * @param remainingSeconds Seconds remaining until cooldown expires. Always ≥ 1.
     * @param totalSeconds Total cooldown duration for this distance tier.
     * @param distanceMeters Distance in meters between last position and target.
     */
    data class Cooling(
        val remainingSeconds: Long,
        val totalSeconds: Long,
        val distanceMeters: Double,
    ) : CooldownState() {
        fun toAdvisoryLabel(): String {
            val hours = remainingSeconds / AppConstants.TimeConstants.SECONDS_PER_HOUR
            val minutes = (remainingSeconds % AppConstants.TimeConstants.SECONDS_PER_HOUR) / AppConstants.TimeConstants.SECONDS_PER_MINUTE
            val seconds = remainingSeconds % AppConstants.TimeConstants.SECONDS_PER_MINUTE
            val timeLabel =
                when {
                    hours > 0 -> "%dh %dm".format(Locale.US, hours, minutes)
                    minutes > 0 -> "%dm %ds".format(Locale.US, minutes, seconds)
                    else -> "%ds".format(Locale.US, seconds)
                }
            val distKm = distanceMeters / 1000.0
            val distLabel = if (distKm >= 1.0) "%.1f km".format(Locale.US, distKm) else "%.0f m".format(Locale.US, distanceMeters)
            return "$timeLabel · $distLabel teleport"
        }
    }
}
