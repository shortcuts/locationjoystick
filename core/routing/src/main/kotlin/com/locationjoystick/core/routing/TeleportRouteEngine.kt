package com.locationjoystick.core.routing

import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.Waypoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TeleportRouteEngine"

/**
 * Engine for replaying a "teleport route": instantly jumps between waypoints, waiting each
 * waypoint's configured duration before jumping to the next.
 *
 * Ticks at the same 1 Hz cadence as [RouteReplayEngine] so the mock fix never goes stale while
 * waiting (mirrors paused route replay — see docs/features/routes.md, "Replay"). Only one replay
 * active at a time.
 */
@Singleton
class TeleportRouteEngine
    @Inject
    constructor() :
    AutoCloseable,
        RouteReplayer {
        private val jobController = ReplayJobController(TAG)

        @Volatile private var resumeIndex: Int = 0

        @Volatile private var resumeRemainingWaitMs: Long = 0L
        private val savedWaypointsRef = AtomicReference<List<Waypoint>>(emptyList())

        @Volatile private var isLooping: Boolean = false

        @Volatile private var randomizeOrder: Boolean = false

        /**
         * @param waypoints Positions in order, each carrying its own wait duration via
         *   [Waypoint.waitSeconds].
         * @param randomizeOrder Shuffles [waypoints] before the first jump, and again every time
         *   the loop restarts (see [launchReplay]) — never mid-loop.
         */
        fun start(
            waypoints: List<Waypoint>,
            isLooping: Boolean = false,
            randomizeOrder: Boolean = false,
            onPositionUpdate: (LatLng) -> Unit,
            onComplete: () -> Unit,
        ) {
            savedWaypointsRef.set(if (randomizeOrder) waypoints.shuffled() else waypoints)
            this.isLooping = isLooping
            this.randomizeOrder = randomizeOrder
            resumeIndex = 0
            resumeRemainingWaitMs = waitMsFor(0)
            launchReplay(onPositionUpdate, onComplete)
            Log.i(TAG, "Teleport replay started: ${waypoints.size} waypoints looping=$isLooping randomized=$randomizeOrder")
        }

        // Nothing walks in a teleport route, so resumeFrom is unused.
        override fun resume(
            onPositionUpdate: (LatLng) -> Unit,
            onComplete: () -> Unit,
            resumeFrom: LatLng?,
        ) {
            launchReplay(onPositionUpdate, onComplete)
            Log.i(TAG, "Teleport replay resumed at index $resumeIndex")
        }

        override fun pause() {
            jobController.cancel()
            Log.i(TAG, "Teleport replay paused at index $resumeIndex")
        }

        // Nothing moves at a "speed" in a teleport route; report 0 so speed-cycle UI doesn't
        // show a stale value.
        override fun updateSpeed(speedMs: Double): Float = 0f

        override suspend fun stop() {
            jobController.cancelAndJoinActive()
            savedWaypointsRef.set(emptyList())
            resumeIndex = 0
            resumeRemainingWaitMs = 0L
            Log.i(TAG, "Teleport replay stopped")
        }

        /** Jumps to [target] (clamped), resetting its wait timer to full — same call whichever direction. */
        private fun jumpToWaypoint(
            target: Int,
            onPositionUpdate: (LatLng) -> Unit,
            onComplete: () -> Unit,
        ): LatLng? {
            val waypoints = savedWaypointsRef.get()
            if (waypoints.isEmpty()) return null
            val clamped = target.coerceIn(0, waypoints.size - 1)
            val wasRunning = jobController.isRunning
            jobController.cancel()
            resumeIndex = clamped
            resumeRemainingWaitMs = waitMsFor(clamped)
            if (wasRunning) launchReplay(onPositionUpdate, onComplete)
            Log.i(TAG, "Jumped to waypoint $clamped, wait timer reset")
            return waypoints[clamped].position
        }

        override fun jumpToNextWaypoint(
            onPositionUpdate: (LatLng) -> Unit,
            onComplete: () -> Unit,
        ): LatLng? = jumpToWaypoint(resumeIndex + 1, onPositionUpdate, onComplete)

        override fun jumpToPreviousWaypoint(
            onPositionUpdate: (LatLng) -> Unit,
            onComplete: () -> Unit,
        ): LatLng? = jumpToWaypoint(resumeIndex - 1, onPositionUpdate, onComplete)

        /**
         * Cancels any active replay job. Call from service onDestroy to stop movement without
         * destroying the scope — the engine is a @Singleton and must remain usable after the
         * service is recreated.
         */
        fun cancelActiveReplay() {
            jobController.cancelActiveReplay()
        }

        override fun close() {
            jobController.close()
        }

        private fun waitMsFor(index: Int): Long =
            savedWaypointsRef
                .get()
                .getOrNull(index)
                ?.waitSeconds
                ?.coerceAtLeast(0)
                ?.times(1000L) ?: 0L

        private fun launchReplay(
            onPositionUpdate: (LatLng) -> Unit,
            onComplete: () -> Unit,
        ) {
            var snapshot = savedWaypointsRef.get()
            if (snapshot.size < 2) {
                jobController.cancel()
                onComplete()
                return
            }
            var index = resumeIndex
            var remainingWaitMs = resumeRemainingWaitMs

            jobController.launch {
                onPositionUpdate(snapshot[index].position)
                while (isActive) {
                    delay(AppConstants.LocationConstants.UPDATE_INTERVAL_MS)
                    remainingWaitMs -= AppConstants.LocationConstants.UPDATE_INTERVAL_MS
                    resumeRemainingWaitMs = remainingWaitMs
                    if (remainingWaitMs > 0) {
                        // Still waiting: re-push the frozen position so the fix never goes stale.
                        try {
                            onPositionUpdate(snapshot[index].position)
                        } catch (e: Exception) {
                            Log.e(TAG, "onPositionUpdate failed", e)
                        }
                        continue
                    }
                    val atEnd = index >= snapshot.size - 1
                    if (atEnd) {
                        if (isLooping) {
                            index = 0
                            if (randomizeOrder) {
                                snapshot = snapshot.shuffled()
                                savedWaypointsRef.set(snapshot)
                            }
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
                    } else {
                        index += 1
                    }
                    remainingWaitMs = waitMsFor(index)
                    resumeIndex = index
                    resumeRemainingWaitMs = remainingWaitMs
                    try {
                        onPositionUpdate(snapshot[index].position)
                    } catch (e: Exception) {
                        Log.e(TAG, "onPositionUpdate failed", e)
                    }
                }
            }
        }
    }
