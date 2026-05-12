package com.locationjoystick.core.routing

import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.bearingTo
import com.locationjoystick.core.model.distanceTo
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val WAYPOINT_SNAP_THRESHOLD_METERS = 1.0

@Singleton
class RouteInterpolator
    @Inject
    constructor() {
        fun advancePosition(
            from: LatLng,
            bearingDeg: Double,
            distanceMeters: Double,
        ): LatLng {
            val d = distanceMeters / EARTH_RADIUS_METERS
            val brng = Math.toRadians(bearingDeg)
            val lat1 = Math.toRadians(from.latitude)
            val lon1 = Math.toRadians(from.longitude)
            val lat2 = asin(sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(brng))
            val lon2 = lon1 + atan2(sin(brng) * sin(d) * cos(lat1), cos(d) - sin(lat1) * sin(lat2))
            return LatLng(Math.toDegrees(lat2), Math.toDegrees(lon2))
        }

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

            return if (distanceToTarget <= WAYPOINT_SNAP_THRESHOLD_METERS || distanceToAdvance >= distanceToTarget) {
                val nextIndex = currentWaypointIndex + 1
                if (nextIndex >= waypoints.size) {
                    InterpolationResult(target, currentWaypointIndex, reachedEnd = true)
                } else {
                    InterpolationResult(target, nextIndex, reachedEnd = false)
                }
            } else {
                val bearing = currentPosition.bearingTo(target)
                val newPosition = advancePosition(currentPosition, bearing, distanceToAdvance)
                InterpolationResult(newPosition, currentWaypointIndex, reachedEnd = false)
            }
        }
    }

data class InterpolationResult(
    val position: LatLng,
    val nextWaypointIndex: Int,
    val reachedEnd: Boolean,
)
