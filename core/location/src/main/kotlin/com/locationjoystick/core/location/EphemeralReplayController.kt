package com.locationjoystick.core.location

import android.content.Context
import android.content.Intent
import com.locationjoystick.core.common.util.remainingRoute
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

        // Serializes taps: a second tap must read the list the first one wrote, not the list before it.
        private val addLock = Mutex()

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
         * - If currently walking: cancels the walk, builds the initial list
         *   (current position → rest of the walk → newPoint), fires startEphemeralReplay.
         *   A walk via roads keeps its remaining stored road shape instead of re-fetching it.
         * - If already in ROUTE_REPLAY: appends the new point.
         * - Otherwise: no-op (returns null).
         *
         * @param newPoint The tapped map position.
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
            walkTarget: LatLng?,
            followRoads: Boolean = false,
            context: Context,
            launchIntent: (Intent) -> Unit,
        ): List<LatLng>? =
            addLock.withLock {
                val currentWaypoints = _pendingWaypoints.value
                val result =
                    if (currentWaypoints.isEmpty() && walkTarget != null) {
                        // First "Add next point" — transition from WalkCoordinator to RouteReplayEngine
                        // Walk-via-roads stores its polyline and index; read them before cancel() clears them.
                        val storedRoute = locationRepository.routeWaypoints.value?.takeIf { it.isNotEmpty() }
                        val fromSegment = (walkCoordinator.walkWaypointIndex() - 1).coerceAtLeast(0)
                        // Cancel first: the walk keeps moving during the lookups below, which would leave startPos behind it.
                        walkCoordinator.cancel()
                        val startPos = locationRepository.currentPosition.value ?: newPoint
                        val firstLeg = storedRoute?.let { remainingRoute(it, startPos, fromSegment) }
                        val legEnd = firstLeg?.last() ?: walkTarget
                        // When road-following, any first leg not already stored (startPos→walkTarget)
                        // and legEnd→newPoint must be OSRM-resolved so the whole chain stays road-following.
                        var targetIndex = 0
                        val initial =
                            if (followRoads) {
                                locationRepository.beginRoadRouteFetch(newPoint)
                                try {
                                    val (toTarget, toNewPoint) =
                                        coroutineScope {
                                            val a =
                                                async {
                                                    firstLeg
                                                        ?: osrmClient.resolveRoute(
                                                            OsrmClient.PROFILE_FOOT,
                                                            startPos,
                                                            walkTarget,
                                                            followRoads = true,
                                                            onFallback = { reportFallback(context, it) },
                                                        )
                                                }
                                            val b =
                                                async {
                                                    osrmClient.resolveRoute(
                                                        OsrmClient.PROFILE_FOOT,
                                                        legEnd,
                                                        newPoint,
                                                        followRoads = true,
                                                        onFallback = { reportFallback(context, it) },
                                                    )
                                                }
                                            a.await() to b.await()
                                        }
                                    targetIndex = toTarget.size - 1
                                    toTarget + toNewPoint.drop(1) // legEnd is last of toTarget, first of toNewPoint
                                } finally {
                                    locationRepository.endRoadRouteFetch()
                                }
                            } else {
                                val prefix = firstLeg?.dropLast(1) ?: listOf(startPos)
                                targetIndex = prefix.size
                                prefix +
                                    osrmClient.resolveRoute(OsrmClient.PROFILE_FOOT, legEnd, newPoint, followRoads = false)
                            }
                        val speedMs = settingsRepository.getActiveSpeedProfile().first().speedMetersPerSecond
                        launchIntent(
                            MockLocationIntentBuilder.startEphemeralReplay(
                                context,
                                initial,
                                speedMs,
                                listOf(
                                    targetIndex,
                                    initial.size - 1,
                                ),
                            ),
                        )
                        initial
                    } else if (currentWaypoints.isNotEmpty() ||
                        locationRepository.currentMode.value == MockMode.ROUTE_REPLAY
                    ) {
                        val appendSegment =
                            if (followRoads && currentWaypoints.isNotEmpty()) {
                                locationRepository.beginRoadRouteFetch(newPoint)
                                try {
                                    val from = currentWaypoints.last()
                                    osrmClient
                                        .resolveRoute(
                                            OsrmClient.PROFILE_FOOT,
                                            from,
                                            newPoint,
                                            followRoads = true,
                                            onFallback = { reportFallback(context, it) },
                                        ).drop(1) // first point is `from`, already in the route
                                } finally {
                                    locationRepository.endRoadRouteFetch()
                                }
                            } else {
                                listOf(newPoint)
                            }
                        appendSegment.forEachIndexed { i, point ->
                            launchIntent(MockLocationIntentBuilder.appendWaypoint(context, point, isStop = i == appendSegment.lastIndex))
                        }
                        currentWaypoints + appendSegment
                    } else {
                        null // no active walk, no-op
                    }
                if (result != null) _pendingWaypoints.value = result
                result
            }

        private fun reportFallback(
            context: Context,
            reason: OsrmFailureReason,
        ) {
            routingErrorReporter.report(
                context.getString(R.string.ephemeral_replay_fallback_message, osrmFailureMessage(context, reason)),
            )
        }
    }
