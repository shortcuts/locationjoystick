package com.locationjoystick.core.data

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.LatLng
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure, stateless engine that computes teleport cooldown advisories.
 *
 * All methods are stateless — callers supply the timestamps and positions.
 * Uses the cooldown table from [AppConstants.CooldownConstants].
 */
object CooldownEngine {
    private val thresholds = AppConstants.CooldownConstants.DISTANCE_THRESHOLDS_METERS
    private val cooldowns = AppConstants.CooldownConstants.COOLDOWN_SECONDS

    /**
     * Returns the advised cooldown in seconds for a given straight-line distance (Haversine).
     *
     * Lookup is a simple descending scan of [thresholds]: the last threshold whose value is ≤
     * [distanceMeters] wins.
     */
    fun cooldownSecondsForDistance(distanceMeters: Double): Long {
        var result = cooldowns[0]
        for (i in thresholds.indices) {
            if (distanceMeters >= thresholds[i]) {
                result = cooldowns[i]
            } else {
                break
            }
        }
        return result
    }

    /**
     * Returns the remaining cooldown in seconds given when the last teleport happened and the total
     * advised cooldown duration.
     *
     * @param lastTeleportMs Epoch ms of the last teleport (0 means never).
     * @param cooldownSeconds Total advised cooldown for the distance tier.
     * @return Remaining seconds ≥ 0.
     */
    fun remainingSeconds(
        lastTeleportMs: Long,
        cooldownSeconds: Long,
    ): Long {
        if (lastTeleportMs == 0L || cooldownSeconds == 0L) return 0L
        val elapsedSeconds = (System.currentTimeMillis() - lastTeleportMs) / 1000L
        return maxOf(0L, cooldownSeconds - elapsedSeconds)
    }

    /**
     * Computes the full [CooldownState] for a pending teleport.
     *
     * @param lastTeleportMs Epoch ms of the last teleport.
     * @param lastPosition Last known spoofed position, or null if unknown.
     * @param target Target position for the pending teleport.
     */
    fun computeState(
        lastTeleportMs: Long,
        lastPosition: LatLng?,
        target: LatLng,
    ): CooldownState {
        if (lastPosition == null || lastTeleportMs == 0L) return CooldownState.Ready
        val distMeters = haversineMeters(lastPosition, target)
        val total = cooldownSecondsForDistance(distMeters)
        if (total == 0L) return CooldownState.Ready
        val remaining = remainingSeconds(lastTeleportMs, total)
        return if (remaining > 0L) {
            CooldownState.Cooling(
                remainingSeconds = remaining,
                totalSeconds = total,
                distanceMeters = distMeters,
            )
        } else {
            CooldownState.Ready
        }
    }

    /** Haversine distance in meters between two [LatLng] points. */
    private fun haversineMeters(
        a: LatLng,
        b: LatLng,
    ): Double {
        val r = AppConstants.LocationConstants.EARTH_RADIUS_METERS
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val sinDLat = sin(dLat / 2)
        val sinDLon = sin(dLon / 2)
        val h = sinDLat * sinDLat + cos(lat1) * cos(lat2) * sinDLon * sinDLon
        return 2 * r * asin(sqrt(h))
    }
}
