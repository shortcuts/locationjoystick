package com.locationjoystick.core.location

import android.content.Context
import android.content.Intent
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.data.WalkCoordinator
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.routing.OsrmClient
import com.locationjoystick.core.routing.OsrmFailureReason
import com.locationjoystick.core.routing.RoutingErrorReporter
import com.locationjoystick.core.routing.osrmFailureMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the walk→ephemeral-replay transition decision.
 *
 * Both [MapViewModel][com.locationjoystick.feature.map.impl.MapViewModel] and
 * [FloatingWidgetService][com.locationjoystick.feature.widget.impl.FloatingWidgetService]
 * inject this and call [addWaypoint] instead of duplicating the state machine inline.
 *
 * The controller is context-free: callers provide [launchIntent] so the controller
 * never needs to hold a [Context] reference itself.
 */
@Singleton
class EphemeralReplayController
    @Inject
    constructor(
        private val locationRepository: LocationRepository,
        private val settingsRepository: SettingsRepository,
        private val walkCoordinator: WalkCoordinator,
        private val osrmClient: OsrmClient,
        private val routingErrorReporter: RoutingErrorReporter,
    ) {
        private val _pendingWaypoints = MutableStateFlow<List<LatLng>>(emptyList())

        /** Current ephemeral waypoint list, shared across all surfaces. Both [MapViewModel] and
         * [FloatingWidgetService] observe this instead of tracking the list locally. */
        val pendingWaypoints: StateFlow<List<LatLng>> = _pendingWaypoints.asStateFlow()

        /** Clears [pendingWaypoints] when the ephemeral replay commits to a real route or is cancelled. */
        fun clearPendingWaypoints() {
            _pendingWaypoints.value = emptyList()
        }

        /**
         * Called when the user taps "Add next point".
         *
         * - If currently walking: cancels the walk, builds the 3-point initial list
         *   (walkStart → walkTarget → newPoint), fires startEphemeralReplay.
         * - If already in ROUTE_REPLAY: appends the new point.
         * - Otherwise: no-op (returns null).
         *
         * @param newPoint The tapped map position.
         * @param currentWaypoints The caller's current ephemeral waypoint list (may be empty).
         * @param walkStart The start of the current walk leg, if any.
         * @param walkTarget The active walk target, if any.
         * @param followRoads If true, use OSRM road-following for new segments: the
         *   walkTarget→newPoint leg on the initial transition, and each appended segment
         *   on subsequent taps.
         * @param context Android [Context] used to build service Intents.
         * @param launchIntent Callback to send the built [Intent] to the service.
         * @return The resulting full waypoint list (caller should update its UI state), or null
         *   if the action was a no-op.
         */
        suspend fun addWaypoint(
            newPoint: LatLng,
            currentWaypoints: List<LatLng>,
            walkStart: LatLng?,
            walkTarget: LatLng?,
            followRoads: Boolean = false,
            context: Context,
            launchIntent: (Intent) -> Unit,
        ): List<LatLng>? {
            val result =
                if (currentWaypoints.isEmpty() && walkTarget != null) {
                    // First "Add next point" — transition from WalkCoordinator to RouteReplayEngine
                    val startPos = walkStart ?: locationRepository.currentPosition.value ?: newPoint
                    // When road-following, both legs (startPos→walkTarget and walkTarget→newPoint)
                    // must be OSRM-resolved. The original walk was via roads; preserving that for
                    // the first leg means the whole chain stays road-following.
                    val initial =
                        if (followRoads) {
                            locationRepository.setRoadRouteFetchInFlight(true)
                            try {
                                val (toTarget, toNewPoint) =
                                    coroutineScope {
                                        val a =
                                            async {
                                                osrmClient.resolveRoute(
                                                    OsrmClient.PROFILE_FOOT,
                                                    startPos,
                                                    walkTarget,
                                                    followRoads = true,
                                                    onFallback = ::reportFallback,
                                                )
                                            }
                                        val b =
                                            async {
                                                osrmClient.resolveRoute(
                                                    OsrmClient.PROFILE_FOOT,
                                                    walkTarget,
                                                    newPoint,
                                                    followRoads = true,
                                                    onFallback = ::reportFallback,
                                                )
                                            }
                                        a.await() to b.await()
                                    }
                                toTarget + toNewPoint.drop(1) // walkTarget is last of toTarget, first of toNewPoint
                            } finally {
                                locationRepository.setRoadRouteFetchInFlight(false)
                            }
                        } else {
                            listOf(startPos) + osrmClient.resolveRoute(OsrmClient.PROFILE_FOOT, walkTarget, newPoint, followRoads = false)
                        }
                    walkCoordinator.cancel()
                    val speedMs = settingsRepository.getActiveSpeedProfile().first().speedMetersPerSecond
                    launchIntent(MockLocationIntentBuilder.startEphemeralReplay(context, initial, speedMs))
                    initial
                } else if (currentWaypoints.isNotEmpty() ||
                    locationRepository.currentMode.value == MockMode.ROUTE_REPLAY
                ) {
                    val appendSegment =
                        if (followRoads && currentWaypoints.isNotEmpty()) {
                            locationRepository.setRoadRouteFetchInFlight(true)
                            try {
                                val from = currentWaypoints.last()
                                osrmClient
                                    .resolveRoute(
                                        OsrmClient.PROFILE_FOOT,
                                        from,
                                        newPoint,
                                        followRoads = true,
                                        onFallback = ::reportFallback,
                                    )
                                    .drop(1) // first point is `from`, already in the route
                            } finally {
                                locationRepository.setRoadRouteFetchInFlight(false)
                            }
                        } else {
                            listOf(newPoint)
                        }
                    appendSegment.forEach { launchIntent(MockLocationIntentBuilder.appendWaypoint(context, it)) }
                    currentWaypoints + appendSegment
                } else {
                    null // no active walk, no-op
                }
            if (result != null) _pendingWaypoints.value = result
            return result
        }

        private fun reportFallback(reason: OsrmFailureReason) {
            routingErrorReporter.report("${osrmFailureMessage(reason)} — using straight line for part of the route")
        }
    }
