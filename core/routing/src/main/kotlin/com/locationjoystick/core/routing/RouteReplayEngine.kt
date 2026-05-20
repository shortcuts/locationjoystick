package com.locationjoystick.core.routing

import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.LatLng
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RouteReplayEngine"

/**
 * Engine for replaying pre-recorded routes.
 *
 * Interpolates between waypoints at a given speed, supporting:
 * - Forward and backward playback
 * - Looping (wraps from last waypoint back to first)
 * - Pause/resume
 * - Dynamic waypoint appending (for recording)
 *
 * Usage:
 * ```
 * routeReplayEngine.start(
 *     waypoints = listOf(LatLng(...), LatLng(...), ...),
 *     speedMs = 1.5,
 *     isLooping = true,
 *     onPositionUpdate = { pos -> /* called each tick */ },
 *     onComplete = { /* called when route ends */ }
 * )
 * ```
 *
 * The engine maintains state for pause/resume support — call [stop] to fully reset.
 * Only one replay can be active at a time.
 */
@Singleton
class RouteReplayEngine
    @Inject
    constructor(
        private val routeInterpolator: RouteInterpolator,
    ) : AutoCloseable {
        private val exceptionHandler =
            CoroutineExceptionHandler { _, throwable ->
                Log.e(TAG, "Replay coroutine crashed", throwable)
            }

        /** Scope for replay coroutines. Uses SupervisorJob so failures don't propagate. */
        private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)

        /** Current replay job. Null when not replaying. */
        private var activeJob: Job? = null

        /** Position to resume from after pause. Set by [pause]. */
        @Volatile private var resumePosition: LatLng? = null

        /** Waypoint index to resume from after pause. */
        @Volatile private var resumeWaypointIndex: Int = 1

        /** Saved waypoints for resume. Volatile so appendWaypoint writes from the main thread are visible to the coroutine. */
        @Volatile private var savedWaypoints: List<LatLng> = emptyList()

        /** Saved speed for resume. */
        private var savedSpeedMs: Double = 0.0

        /** Whether looping is enabled. */
        @Volatile private var isLooping: Boolean = false

        /**
         * Starts a new route replay from the first waypoint.
         *
         * @param waypoints List of waypoints in order
         * @param speedMs Movement speed in meters per second
         * @param isLooping Whether to loop back to start when reaching end
         * @param onPositionUpdate Callback invoked each tick with new position
         * @param onComplete Callback invoked when replay finishes or stops
         */
        fun start(
            waypoints: List<LatLng>,
            speedMs: Double,
            isLooping: Boolean = false,
            onPositionUpdate: (LatLng) -> Unit,
            onComplete: () -> Unit,
        ) {
            savedWaypoints = waypoints
            savedSpeedMs = speedMs
            this.isLooping = isLooping
            resumePosition = waypoints.firstOrNull()
            resumeWaypointIndex = 1
            launchReplay(onPositionUpdate, onComplete)
            Log.i(TAG, "Replay started: ${waypoints.size} waypoints at ${speedMs}m/s looping=$isLooping")
        }

        /**
         * Resumes a previously paused replay from the saved position.
         * Use [pause] to pause, then call resume to continue.
         *
         * @param onPositionUpdate Callback invoked each tick with new position
         * @param onComplete Callback invoked when replay finishes
         */
        fun resume(
            onPositionUpdate: (LatLng) -> Unit,
            onComplete: () -> Unit,
        ) {
            launchReplay(onPositionUpdate, onComplete)
            Log.i(TAG, "Replay resumed at index $resumeWaypointIndex")
        }

        /**
         * Pauses the current replay. Call [resume] to continue from current position.
         * State is saved internally for resume.
         */
        fun pause() {
            // Cancel but do NOT null activeJob here — launchReplay() calls activeJob?.cancel()
            // before launching the new coroutine, which is safe on an already-cancelled job.
            // Nulling immediately would allow a concurrent resume() to skip the cancel guard.
            activeJob?.cancel()
            Log.i(TAG, "Replay paused at index $resumeWaypointIndex")
        }

        /**
         * Stops the replay and clears all state.
         * Use this to fully reset after pause or to cancel a running replay.
         */
        suspend fun stop() {
            activeJob?.cancelAndJoin()
            activeJob = null
            savedWaypoints = emptyList()
            resumePosition = null
            resumeWaypointIndex = 1
            Log.i(TAG, "Replay stopped")
        }

        /**
         * Appends a waypoint to the current replay (used for recording).
         * @param pos Position to append
         */
        fun appendWaypoint(pos: LatLng) {
            savedWaypoints = savedWaypoints + pos
            Log.i(TAG, "Waypoint appended; total=${savedWaypoints.size}")
        }

        /**
         * Releases the engine scope. Call only when the engine will never be reused
         * (e.g. service onDestroy). Implements [AutoCloseable].
         */
        override fun close() {
            activeJob?.cancel()
            engineScope.cancel()
        }

        private fun launchReplay(
            onPositionUpdate: (LatLng) -> Unit,
            onComplete: () -> Unit,
        ) {
            activeJob?.cancel()
            if (savedWaypoints.size < 2) {
                onComplete()
                return
            }
            var position = resumePosition ?: savedWaypoints.first()
            var index = resumeWaypointIndex

            activeJob =
                engineScope.launch {
                    while (isActive) {
                        val result =
                            routeInterpolator.interpolateAlongRoute(
                                waypoints = savedWaypoints,
                                currentPosition = position,
                                currentWaypointIndex = index,
                                speedMs = savedSpeedMs,
                                deltaTimeMs = AppConstants.LocationConstants.UPDATE_INTERVAL_MS,
                            )
                        position = result.position
                        index = result.nextWaypointIndex
                        resumePosition = position
                        resumeWaypointIndex = index
                        try {
                            onPositionUpdate(position)
                        } catch (e: Exception) {
                            Log.e(TAG, "onPositionUpdate failed", e)
                        }
                        if (result.reachedEnd) {
                            if (isLooping) {
                                position = savedWaypoints.first()
                                index = 1
                                resumePosition = position
                                resumeWaypointIndex = index
                            } else {
                                if (isActive) {
                                    try {
                                        onComplete()
                                    } catch (e: Exception) {
                                        Log.e(TAG, "onComplete failed", e)
                                    }
                                }
                                break
                            }
                        }
                        delay(AppConstants.LocationConstants.UPDATE_INTERVAL_MS)
                    }
                }
        }
    }
