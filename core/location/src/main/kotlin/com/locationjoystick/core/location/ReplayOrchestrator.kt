package com.locationjoystick.core.location

import android.util.Log
import com.locationjoystick.core.common.util.BearingTracker
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RoamingRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.WalkToEngine
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.routing.OsrmClient
import com.locationjoystick.core.routing.OsrmFailureReason
import com.locationjoystick.core.routing.RouteReplayEngine
import com.locationjoystick.core.routing.RoutingErrorReporter
import com.locationjoystick.core.routing.osrmFailureMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "ReplayOrchestrator"

/**
 * Owns all route-replay and walk-to orchestration logic extracted from [MockLocationService].
 *
 * `followRoadsToStart` governs both the pre-replay walk from the current position to the
 * route's first waypoint AND every leg between the route's own saved waypoints during
 * replay (see [expandWaypointsForFollowRoads]) — not just the pre-replay walk.
 *
 * Communicates back to the service via lambdas:
 * - [onStateChange]: writes into the service's `_state` MutableStateFlow
 * - [onPositionChange]: updates `currentLat` / `currentLon` @Volatile fields
 * - [onSpeedChange]: updates `currentSpeedMs` @Volatile field
 * - [pushLocationUpdate]: pushes a GPS tick via the test provider
 * - [startUpdateLoop]: restarts the idle update loop after replay ends
 */
internal class ReplayOrchestrator(
    private val locationRepository: LocationRepository,
    private val routeRepository: RouteRepository,
    private val roamingRepository: RoamingRepository,
    private val routeReplayEngine: RouteReplayEngine,
    private val walkToEngine: WalkToEngine,
    private val osrmClient: OsrmClient,
    private val routingErrorReporter: RoutingErrorReporter,
    private val scope: CoroutineScope,
    private val onStateChange: (MockLocationState) -> Unit,
    private val onPositionChange: (lat: Double, lon: Double) -> Unit,
    private val onSpeedChange: (speedMs: Float) -> Unit,
    private val pushLocationUpdate: () -> Unit,
    private val startUpdateLoop: () -> Unit,
) {
    /** Tracks the active scope.launch job so new starts can cancel it before launching. */
    private var activeReplayJob: Job? = null

    private val bearingTracker = BearingTracker()

    fun handleStart(
        routeId: String,
        isBackward: Boolean,
        speedMs: Double,
        isLoopingOverride: Boolean? = null,
        returnPosition: LatLng? = null,
        followRoadsToStart: Boolean = false,
    ) {
        val previous = activeReplayJob
        activeReplayJob =
            scope.launch {
                previous?.cancelAndJoin()
                routeReplayEngine.stop()
                val route = routeRepository.getRouteWithWaypoints(routeId).first() ?: return@launch
                if (route.waypoints.size < 2) return@launch
                val latLngs = (if (isBackward) route.waypoints.reversed() else route.waypoints).map { it.position }
                val isLooping = isLoopingOverride ?: route.isLooping
                val (replayWaypoints, boundaryIndices) =
                    if (followRoadsToStart) expandWaypointsForFollowRoads(latLngs) else latLngs to null

                startReplayWithWaypoints(
                    waypoints = replayWaypoints,
                    speedMs = speedMs,
                    isLooping = isLooping,
                    followRoadsToStart = followRoadsToStart,
                    boundaryIndices = boundaryIndices,
                    persistMetadata = {
                        locationRepository.setActiveRouteId(routeId)
                        locationRepository.setIsReplayBackward(isBackward)
                        locationRepository.setRouteWaypoints(replayWaypoints)
                    },
                    onComplete = {
                        locationRepository.setRouteWaypoints(null)
                        locationRepository.setActiveRouteId(null)
                        if (returnPosition != null) {
                            walkToPosition(returnPosition, speedMs)
                        }
                        onSpeedChange(0f)
                        locationRepository.setMockMode(MockMode.TELEPORT)
                        locationRepository.emitCompletion("Route complete")
                    },
                )
            }
    }

    fun handleEphemeralStart(
        waypoints: List<LatLng>,
        speedMs: Double,
    ) {
        val previous = activeReplayJob
        activeReplayJob =
            scope.launch {
                previous?.cancelAndJoin()
                routeReplayEngine.stop()
                startReplayWithWaypoints(
                    waypoints = waypoints,
                    speedMs = speedMs,
                    isLooping = false,
                    persistMetadata = null,
                )
            }
    }

    /**
     * Propagates a live speed-profile change into the active replay. Caller must only invoke
     * this while a replay is actually running (mode == ROUTE_REPLAY), since it also overwrites
     * the service's reported `currentSpeedMs`.
     */
    fun updateSpeed(speedMs: Double) {
        routeReplayEngine.updateSpeed(speedMs)
        onSpeedChange(speedMs.toFloat())
    }

    fun handlePause() {
        routeReplayEngine.pause()
        onStateChange(MockLocationState.PAUSED)
        locationRepository.pauseSpoofing()
        Log.i(TAG, "Replay paused")
    }

    fun handleResume(speedMs: Double) {
        onStateChange(MockLocationState.RUNNING)
        locationRepository.startSpoofing()
        routeReplayEngine.resume(
            onPositionUpdate = ::tickPosition,
            onComplete = {
                // Matches startReplayWithWaypoints' default onComplete (used when a replay finishes
                // without ever being paused) — a natural completion must not force IDLE/stopSpoofing.
                // Forcing IDLE here previously killed a group-sync leader's broadcast on completion.
                locationRepository.setRouteWaypoints(null)
                onSpeedChange(0f)
                locationRepository.setMockMode(MockMode.TELEPORT)
                locationRepository.emitCompletion("Route complete")
            },
        )
        Log.i(TAG, "Replay resumed at ${speedMs}m/s")
    }

    fun handleJumpToNextWaypoint() = handleJumpToWaypoint(forward = true)

    fun handleJumpToPreviousWaypoint() = handleJumpToWaypoint(forward = false)

    private fun handleJumpToWaypoint(forward: Boolean) {
        if (locationRepository.currentMode.value != MockMode.ROUTE_REPLAY) return
        val onReplayComplete: () -> Unit = {
            locationRepository.setRouteWaypoints(null)
            onSpeedChange(0f)
            locationRepository.setMockMode(MockMode.TELEPORT)
            locationRepository.emitCompletion("Route complete")
        }
        val target =
            if (forward) {
                routeReplayEngine.jumpToNextWaypoint(::tickPosition, onReplayComplete)
            } else {
                routeReplayEngine.jumpToPreviousWaypoint(::tickPosition, onReplayComplete)
            }
        target?.let(::tickPosition)
        Log.i(TAG, "Jumped to ${if (forward) "next" else "previous"} waypoint")
    }

    /**
     * Pushes one position tick: updates the service's reported lat/lon, the shared
     * repository state, and the test-provider location.
     */
    private fun tickPosition(pos: LatLng) {
        onPositionChange(pos.latitude, pos.longitude)
        bearingTracker.advance(pos)?.let { locationRepository.setBearingInternal(it) }
        try {
            locationRepository.setPositionInternal(pos)
            pushLocationUpdate()
        } catch (e: Exception) {
            Log.e(TAG, "Position update failed", e)
        }
    }

    suspend fun handleStop() {
        activeReplayJob?.cancelAndJoin()
        activeReplayJob = null
        locationRepository.setRouteWaypoints(null)
        routeReplayEngine.stop()
        resetModeIfStillReplaying()
        locationRepository.setActiveRouteId(null)
        if (locationRepository.mockLocationState.value == MockLocationState.RUNNING) {
            startUpdateLoop()
        }
        Log.i(TAG, "Replay stopped; service remains active in TELEPORT mode")
    }

    suspend fun handleCancel() {
        activeReplayJob?.cancelAndJoin()
        activeReplayJob = null
        locationRepository.setRouteWaypoints(null)
        routeReplayEngine.stop()
        resetModeIfStillReplaying()
        locationRepository.setActiveRouteId(null)
        if (locationRepository.mockLocationState.value == MockLocationState.RUNNING) {
            startUpdateLoop()
        }
        Log.i(TAG, "Replay cancelled; service remains active in TELEPORT mode")
    }

    /**
     * Only resets mode to TELEPORT if we are still in ROUTE_REPLAY, to avoid clobbering a mode
     * set by another subsystem while `cancelAndJoin` was suspended (e.g. a new walk-to started
     * immediately after a cancel was requested — see MapController.walkTo/walkViaRoads).
     */
    private fun resetModeIfStillReplaying() {
        if (locationRepository.currentMode.value == MockMode.ROUTE_REPLAY) {
            onSpeedChange(0f)
            locationRepository.setMockMode(MockMode.TELEPORT)
        }
    }

    /**
     * Shared engine for both named-route and ephemeral replay.
     *
     * @param waypoints Ordered list of positions to replay (≥2).
     * @param speedMs Playback speed in m/s.
     * @param isLooping Whether to loop at the end.
     * @param persistMetadata If non-null, invoked before replay starts to persist route metadata.
     * @param onComplete Invoked on the service scope when the replay engine signals completion.
     */
    private suspend fun startReplayWithWaypoints(
        waypoints: List<LatLng>,
        speedMs: Double,
        isLooping: Boolean,
        followRoadsToStart: Boolean = false,
        boundaryIndices: List<Int>? = null,
        persistMetadata: (suspend () -> Unit)? = null,
        onComplete: suspend () -> Unit = {
            locationRepository.setRouteWaypoints(null)
            onSpeedChange(0f)
            locationRepository.setMockMode(MockMode.TELEPORT)
            locationRepository.emitCompletion("Route complete")
        },
    ) {
        if (locationRepository.currentMode.value == MockMode.ROAMING) roamingRepository.stopRoaming()
        if (waypoints.size < 2) return

        onSpeedChange(speedMs.toFloat())

        // Set mode BEFORE state so the state observer sees ROUTE_REPLAY and
        // correctly skips starting the background update loop.
        locationRepository.setMockMode(MockMode.ROUTE_REPLAY)
        persistMetadata?.invoke()
        // Trigger RUNNING after mode is set.
        onStateChange(MockLocationState.RUNNING)
        locationRepository.startSpoofing()

        walkToPosition(waypoints.first(), speedMs, followRoadsToStart)

        routeReplayEngine.start(
            waypoints = waypoints,
            speedMs = speedMs,
            isLooping = isLooping,
            onPositionUpdate = ::tickPosition,
            onComplete = { scope.launch { onComplete() } },
            boundaryIndices = boundaryIndices,
        )

        // If pause was requested during walk-to-start (before engine launched),
        // ensure the engine is paused now that it has been initialized.
        if (locationRepository.mockLocationState.value == MockLocationState.PAUSED) routeReplayEngine.pause()
    }

    /**
     * Pre-plans a road-following path between the route's own saved waypoints, mirroring
     * [com.locationjoystick.core.routing.RoamingEngine.planRoadFollowingRoute]. Resolves each
     * leg independently via OSRM (foot profile); a leg that fails falls back to a straight
     * line and is counted, with one aggregated summary reported afterward if any legs fell
     * back.
     *
     * @return the expanded waypoint list plus the indices within it that are the route's
     *   real, named stops ("boundary indices"), so jump-to-waypoint keeps targeting real stops.
     */
    private suspend fun expandWaypointsForFollowRoads(waypoints: List<LatLng>): Pair<List<LatLng>, List<Int>> {
        val expanded = mutableListOf(waypoints.first())
        val boundaryIndices = mutableListOf(0)
        var fallbackCount = 0
        for (i in 0 until waypoints.size - 1) {
            val leg =
                osrmClient.resolveRoute(
                    OsrmClient.PROFILE_FOOT,
                    waypoints[i],
                    waypoints[i + 1],
                    followRoads = true,
                    onFallback = { fallbackCount++ },
                )
            expanded.addAll(leg.drop(1))
            boundaryIndices.add(expanded.size - 1)
        }
        if (fallbackCount > 0) {
            routingErrorReporter.report(
                "Road-following partially unavailable — $fallbackCount of " +
                    "${waypoints.size - 1} legs used straight-line paths",
            )
        }
        return expanded to boundaryIndices
    }

    private suspend fun walkToPosition(
        target: LatLng,
        speedMs: Double,
        followRoads: Boolean = false,
    ) {
        val startPos = locationRepository.currentPosition.value ?: return
        if (!followRoads) {
            walkToEngine.walkToOnce(startPos, target, speedMs, ::tickPosition)
            return
        }
        val legs =
            osrmClient.resolveRoute(
                OsrmClient.PROFILE_FOOT,
                startPos,
                target,
                followRoads = true,
                onFallback = ::reportWalkToStartFallback,
            )
        for (i in 0 until legs.size - 1) {
            walkToEngine.walkToOnce(legs[i], legs[i + 1], speedMs, ::tickPosition)
        }
    }

    private fun reportWalkToStartFallback(reason: OsrmFailureReason) {
        routingErrorReporter.report("${osrmFailureMessage(reason)} — using straight walk to the route start")
    }
}
