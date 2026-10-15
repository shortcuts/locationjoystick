package com.locationjoystick.core.data

import android.util.Log
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WalkCoordinator"

/**
 * Thin facade over [WalkToEngine] for use inside a ViewModel or Service.
 *
 * Handles:
 * - Cancelling any in-flight walk before starting a new one.
 * - Forwarding position ticks to [LocationRepository].
 * - Clearing [LocationRepository.walkTarget] on arrival / cancellation.
 * - Invoking an optional [onPositionUpdate] callback (e.g. to send a service intent).
 *
 * The caller provides a [CoroutineScope] (typically `viewModelScope` or a
 * service scope) and never manages the [Job] directly.
 *
 * Usage:
 * ```kotlin
 * walkCoordinator.startWalk(target, viewModelScope) { newPos ->
 *     context.startService(MockLocationIntentBuilder.updatePosition(context, newPos.latitude, newPos.longitude))
 * }
 * walkCoordinator.cancel()
 * ```
 */
@Singleton
class WalkCoordinator
    @Inject
    constructor(
        private val locationRepository: LocationRepository,
        private val walkToEngine: WalkToEngine,
    ) {
        private val lock = Any()
        private var activeWalkJob: Job? = null

        // Bumped by every start and cancel. A walk only applies ticks while its own generation
        // is current, and a deferred start (e.g. after an OSRM lookup) only proceeds if nothing
        // started or cancelled a walk meanwhile. Callers run on a multi-threaded scope, so
        // Job.cancel() alone cannot stop a tick already past its last suspension point, nor a
        // road-walk start whose routing result lands just after a teleport (issue #99).
        private var generation = 0L

        /**
         * Token for a later [startWalk]/[startWalkAlongRoute] call via `expectedGeneration`.
         * Any intervening start or [cancel] (e.g. a teleport) invalidates it.
         */
        fun currentGeneration(): Long = synchronized(lock) { generation }

        /** Whether no walk was started or cancelled since [token] was taken. */
        fun isCurrent(token: Long): Boolean = synchronized(lock) { generation == token }

        /**
         * Starts a walk toward [target] on the given [scope].
         *
         * Any previously active walk is cancelled first.
         *
         * @param expectedGeneration When non-null, the walk only starts if [currentGeneration]
         *   still equals it — a stale deferred start is dropped instead of reviving a walk the
         *   user already replaced or cancelled.
         * @param onPositionUpdate Optional extra callback invoked on each position tick.
         *   Use this to forward updates to a background service (e.g. via Intent).
         * @return `true` if the walk started.
         */
        fun startWalk(
            target: LatLng,
            scope: CoroutineScope,
            expectedGeneration: Long? = null,
            speedMsOverride: Double? = null,
            onPositionUpdate: (suspend (LatLng, Float, Float) -> Unit)? = null,
        ): Boolean = startWalkAlongRoute(listOf(target), scope, expectedGeneration, speedMsOverride, onPositionUpdate)

        fun startWalkAlongRoute(
            waypoints: List<LatLng>,
            scope: CoroutineScope,
            expectedGeneration: Long? = null,
            speedMsOverride: Double? = null,
            onPositionUpdate: (suspend (LatLng, Float, Float) -> Unit)? = null,
        ): Boolean {
            require(waypoints.isNotEmpty()) { "Waypoints must not be empty" }
            synchronized(lock) {
                if (expectedGeneration != null && expectedGeneration != generation) {
                    Log.d(TAG, "Dropping stale walk start toward ${waypoints.last()}")
                    return false
                }
                activeWalkJob?.cancel()
                val walkGeneration = ++generation
                val finalTarget = waypoints.last()
                locationRepository.setWalkTarget(finalTarget)
                locationRepository.setMockMode(MockMode.WALK_TO)

                var lastBearing = 0f
                with(walkToEngine) {
                    activeWalkJob =
                        scope.launchWalkAlongRoute(
                            waypoints = waypoints,
                            speedMsOverride = speedMsOverride,
                            onPositionUpdate = { newPos, speedMs, bearing ->
                                // A tick already running when cancel() lands must not write the
                                // old path back over a teleport.
                                if (isCurrent(walkGeneration)) {
                                    lastBearing = bearing
                                    locationRepository.updatePosition(newPos)
                                    onPositionUpdate?.invoke(newPos, speedMs, bearing)
                                }
                            },
                            onArrival = {
                                if (isCurrent(walkGeneration)) {
                                    Log.d(TAG, "Arrived at road-following destination $finalTarget")
                                    // Zero speed while mode is still WALK_TO — MockLocationService.
                                    // updatePositionWithVector() only accepts updates in JOYSTICK/WALK_TO
                                    // mode, so this must happen before setMockMode(TELEPORT) below.
                                    onPositionUpdate?.invoke(finalTarget, 0f, lastBearing)
                                    locationRepository.setMockMode(MockMode.TELEPORT)
                                    locationRepository.emitCompletion("Walk complete")
                                }
                            },
                            onFinished = {
                                synchronized(lock) {
                                    if (generation == walkGeneration) {
                                        locationRepository.setWalkTarget(null)
                                    }
                                }
                            },
                        )
                }
                return true
            }
        }

        /** Cancels any active walk and clears the walk target and route waypoints. */
        fun cancel() {
            synchronized(lock) {
                generation++
                activeWalkJob?.cancel()
                activeWalkJob = null
                locationRepository.setWalkTarget(null)
                locationRepository.setRouteWaypoints(null)
            }
        }
    }
