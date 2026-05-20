package com.locationjoystick.core.data

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
    ) : CooldownState()
}
