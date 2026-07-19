package com.locationjoystick.core.location

import android.util.Log
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RoamingRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.WalkToEngine
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.routing.RouteReplayEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "ReplayOrchestrator"

/**
 * Owns all route-replay and walk-to orchestration logic extracted from [MockLocationService].
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
    private val scope: CoroutineScope,
    private val onStateChange: (MockLocationState) -> Unit,
    private val onPositionChange: (lat: Double, lon: Double) -> Unit,
    private val onSpeedChange: (speedMs: Float) -> Unit,
    private val pushLocationUpdate: () -> Unit,
    private val startUpdateLoop: () -> Unit,
) {
    /** Tracks the active scope.launch job so new starts can cancel it before launching. */
    private var activeReplayJob: Job? = null

    fun handleStart(
        routeId: String,
        isBackward: Boolean,
        speedMs: Double,
        isLoopingOverride: Boolean? = null,
        returnPosition: LatLng? = null,
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

                startReplayWithWaypoints(
                    waypoints = latLngs,
                    speedMs = speedMs,
                    isLooping = isLooping,
                    persistMetadata = {
                        locationRepository.setActiveRouteId(routeId)
                        locationRepository.setIsReplayBackward(isBackward)
                        locationRepository.setRouteWaypoints(latLngs)
                    },
                    onComplete = {
                        locationRepository.setRouteWaypoints(null)
                        locationRepository.setActiveRouteId(null)
                        if (returnPosition != null) {
                            walkToPosition(returnPosition, speedMs)
                        }
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
            onPositionUpdate = { pos ->
                onPositionChange(pos.latitude, pos.longitude)
                try {
                    locationRepository.setPositionInternal(pos)
                    pushLocationUpdate()
                } catch (e: Exception) {
                    Log.e(TAG, "Position update failed during route resume", e)
                }
            },
            onComplete = {
                // Matches startReplayWithWaypoints' default onComplete (used when a replay finishes
                // without ever being paused) — a natural completion must not force IDLE/stopSpoofing.
                // Forcing IDLE here previously killed a group-sync leader's broadcast on completion.
                locationRepository.setRouteWaypoints(null)
                locationRepository.setMockMode(MockMode.TELEPORT)
                locationRepository.emitCompletion("Route complete")
            },
        )
        Log.i(TAG, "Replay resumed at ${speedMs}m/s")
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
        persistMetadata: (suspend () -> Unit)? = null,
        onComplete: suspend () -> Unit = {
            locationRepository.setRouteWaypoints(null)
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

        walkToPosition(waypoints.first(), speedMs)

        routeReplayEngine.start(
            waypoints = waypoints,
            speedMs = speedMs,
            isLooping = isLooping,
            onPositionUpdate = { pos ->
                onPositionChange(pos.latitude, pos.longitude)
                try {
                    locationRepository.setPositionInternal(pos)
                    pushLocationUpdate()
                } catch (e: Exception) {
                    Log.e(TAG, "Position update failed during replay", e)
                }
            },
            onComplete = { scope.launch { onComplete() } },
        )

        // If pause was requested during walk-to-start (before engine launched),
        // ensure the engine is paused now that it has been initialized.
        if (locationRepository.mockLocationState.value == MockLocationState.PAUSED) routeReplayEngine.pause()
    }

    private suspend fun walkToPosition(
        target: LatLng,
        speedMs: Double,
    ) {
        val startPos = locationRepository.currentPosition.value ?: return
        walkToEngine.walkToOnce(startPos, target, speedMs) { pos ->
            onPositionChange(pos.latitude, pos.longitude)
            locationRepository.setPositionInternal(pos)
            pushLocationUpdate()
        }
    }
}
