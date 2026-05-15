package com.locationjoystick.core.routing

import android.util.Log
import com.locationjoystick.core.model.LatLng
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RouteReplayEngine"
private const val TICK_INTERVAL_MS = 1000L

@Singleton
class RouteReplayEngine
    @Inject
    constructor(
        private val routeInterpolator: RouteInterpolator,
    ) {
        private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Replay coroutine crashed", throwable)
        }
        private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)
        private var activeJob: Job? = null

        @Volatile private var resumePosition: LatLng? = null

        @Volatile private var resumeWaypointIndex: Int = 1
        private var savedWaypoints: List<LatLng> = emptyList()
        private var savedSpeedMs: Double = 0.0

        @Volatile private var isLooping: Boolean = false

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

        fun resume(
            onPositionUpdate: (LatLng) -> Unit,
            onComplete: () -> Unit,
        ) {
            launchReplay(onPositionUpdate, onComplete)
            Log.i(TAG, "Replay resumed at index $resumeWaypointIndex")
        }

        fun pause() {
            activeJob?.cancel()
            activeJob = null
            Log.i(TAG, "Replay paused at index $resumeWaypointIndex")
        }

        suspend fun stop() {
            activeJob?.cancelAndJoin()
            activeJob = null
            savedWaypoints = emptyList()
            resumePosition = null
            resumeWaypointIndex = 1
            Log.i(TAG, "Replay stopped")
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
                                deltaTimeMs = TICK_INTERVAL_MS,
                            )
                        position = result.position
                        index = result.nextWaypointIndex
                        resumePosition = position
                        resumeWaypointIndex = index
                        try { onPositionUpdate(position) } catch (e: Exception) { Log.e(TAG, "onPositionUpdate failed", e) }
                        if (result.reachedEnd) {
                            if (isLooping) {
                                position = savedWaypoints.first()
                                index = 1
                                resumePosition = position
                                resumeWaypointIndex = index
                            } else {
                                try { onComplete() } catch (e: Exception) { Log.e(TAG, "onComplete failed", e) }
                                break
                            }
                        }
                        delay(TICK_INTERVAL_MS)
                    }
                }
        }
    }
