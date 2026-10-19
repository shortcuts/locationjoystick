package com.locationjoystick.core.routing

import com.locationjoystick.core.model.LatLng

/**
 * Common shape of [RouteReplayEngine] and [TeleportRouteEngine], so callers driving an active
 * replay (pause/resume/stop/jump) don't need to know which engine is running it.
 */
interface RouteReplayer {
    fun pause()

    /**
     * Updates the speed used by the active (or next-resumed) replay and returns the value
     * the caller should report as the current speed — a teleport route always reports 0f
     * since nothing moves at a "speed" there.
     */
    fun updateSpeed(speedMs: Double): Float

    /** [resumeFrom] is where the user left the position while paused; the replay walks from it back to the next waypoint. */
    fun resume(
        onPositionUpdate: (LatLng) -> Unit,
        onComplete: () -> Unit,
        resumeFrom: LatLng? = null,
    )

    suspend fun stop()

    fun jumpToNextWaypoint(
        onPositionUpdate: (LatLng) -> Unit,
        onComplete: () -> Unit,
    ): LatLng?

    fun jumpToPreviousWaypoint(
        onPositionUpdate: (LatLng) -> Unit,
        onComplete: () -> Unit,
    ): LatLng?
}
