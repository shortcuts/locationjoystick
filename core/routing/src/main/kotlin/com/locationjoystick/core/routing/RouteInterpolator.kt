package com.locationjoystick.core.routing

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.calculateBearing
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.distanceTo
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.toDegrees
import kotlin.math.toRadians

/**
 * Handles position interpolation along routes for replay and roaming.
 *
 * Used by [RouteReplayEngine] and [RoamingEngine] to advance positions
 * along a series of waypoints at a given speed.
 *
 * Key functionality:
 * - Advances a position along a great-circle bearing by a given distance
 * - Handles waypoint arrival detection and automatic advancement to next waypoint
 * - Snaps to waypoints within a threshold distance ([AppConstants.RouteConstants.WAYPOINT_SNAP_THRESHOLD_METERS])
 */
@Singleton
class RouteInterpolator
    @Inject
    constructor() {
        /**
         * Advances a position along a bearing by a given distance.
         *
         * @param from Starting position
         * @param bearingDeg Bearing in degrees (0 = north, 90 = east)
         * @param distanceMeters Distance to travel in meters
         * @return New position after advancing
         */
        fun advancePosition(
            from: LatLng,
            bearingDeg: Double,
            distanceMeters: Double,
        ): LatLng {
            val d = distanceMeters / AppConstants.LocationConstants.EARTH_RADIUS_METERS
            val brng = bearingDeg.toRadians()
            val lat1 = from.latitude.toRadians()
            val lon1 = from.longitude.toRadians()
            val lat2 = asin(sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(brng))
            val lon2 = lon1 + atan2(sin(brng) * sin(d) * cos(lat1), cos(d) - sin(lat1) * sin(lat2))
            return LatLng(lat2.toDegrees(), lon2.toDegrees())
        }

        /**
         * Interpolates movement along a route for one time step.
         *
         * @param waypoints Ordered list of waypoints to follow
         * @param currentPosition Current position
         * @param currentWaypointIndex Index of the target waypoint in the list
         * @param speedMs Movement speed in meters per second
         * @param deltaTimeMs Time step in milliseconds
         * @return [InterpolationResult] with new position, next waypoint index, and end-of-route flag
         */
        fun interpolateAlongRoute(
            waypoints: List<LatLng>,
            currentPosition: LatLng,
            currentWaypointIndex: Int,
            speedMs: Double,
            deltaTimeMs: Long,
        ): InterpolationResult {
            if (waypoints.size < 2 || currentWaypointIndex >= waypoints.size) {
                return InterpolationResult(currentPosition, currentWaypointIndex, reachedEnd = true)
            }

            val target = waypoints[currentWaypointIndex]
            val distanceToTarget = currentPosition.distanceTo(target)
            val distanceToAdvance = speedMs * (deltaTimeMs / 1000.0)

            return if (distanceToTarget <= AppConstants.RouteConstants.WAYPOINT_SNAP_THRESHOLD_METERS ||
                distanceToAdvance >= distanceToTarget
            ) {
                val nextIndex = currentWaypointIndex + 1
                if (nextIndex >= waypoints.size) {
                    InterpolationResult(target, currentWaypointIndex, reachedEnd = true)
                } else {
                    InterpolationResult(target, nextIndex, reachedEnd = false)
                }
            } else {
                val bearing = calculateBearing(currentPosition.latitude, currentPosition.longitude, target.latitude, target.longitude)
                val newPosition = advancePosition(currentPosition, bearing, distanceToAdvance)
                InterpolationResult(newPosition, currentWaypointIndex, reachedEnd = false)
            }
        }
    }

/**
 * Result of one interpolation step along a route.
 *
 * @property position New position after interpolation
 * @property nextWaypointIndex Index of the next waypoint to target
 * @property reachedEnd True if the last waypoint has been reached
 */
data class InterpolationResult(
    val position: LatLng,
    val nextWaypointIndex: Int,
    val reachedEnd: Boolean,
)
