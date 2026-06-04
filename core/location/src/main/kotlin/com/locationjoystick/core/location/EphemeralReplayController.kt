package com.locationjoystick.core.location

import android.content.Context
import android.content.Intent
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.data.WalkCoordinator
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.routing.OsrmClient
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
    ) {
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
         * @param followRoads If true and transitioning from walk→ephemeral, replace the straight
         *   walkTarget→newPoint leg with an OSRM road-following route.
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
        ): List<LatLng>? =
            if (currentWaypoints.isEmpty() && walkTarget != null) {
                // First "Add next point" — transition from WalkCoordinator to RouteReplayEngine
                val startPos = walkStart ?: locationRepository.currentPosition.value ?: newPoint
                val segment = osrmClient.resolveRoute(OsrmClient.PROFILE_FOOT, walkTarget, newPoint, followRoads)
                val initial = listOf(startPos) + segment
                walkCoordinator.cancel()
                val speedMs = settingsRepository.getActiveSpeedProfile().first().speedMetersPerSecond
                launchIntent(MockLocationIntentBuilder.startEphemeralReplay(context, initial, speedMs))
                initial
            } else if (currentWaypoints.isNotEmpty() ||
                locationRepository.currentMode.value == MockMode.ROUTE_REPLAY
            ) {
                launchIntent(MockLocationIntentBuilder.appendWaypoint(context, newPoint))
                currentWaypoints + newPoint
            } else {
                null // no active walk, no-op
            }
    }
