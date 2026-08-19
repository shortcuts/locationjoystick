package com.locationjoystick.core.location

import android.content.Context
import android.content.Intent
import com.locationjoystick.core.common.constants.AppConstants.ServiceConstants
import com.locationjoystick.core.model.LatLng

/**
 * Compile-safe factory for all intents targeting [MockLocationService].
 *
 * Eliminates 4 different intent-construction patterns and 2 hard-coded class-name
 * strings (`"com.locationjoystick.core.location.MockLocationService"`) that existed
 * across `MapViewModel` and `FloatingWidgetService`.
 *
 * Every method uses `Intent(context, MockLocationService::class.java)` — compile-safe.
 */
object MockLocationIntentBuilder {
    fun updatePosition(
        context: Context,
        lat: Double,
        lon: Double,
        speedMs: Float = 0f,
        bearing: Float = 0f,
    ): Intent =
        Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_UPDATE_POSITION
            putExtra(ServiceConstants.EXTRA_LAT, lat)
            putExtra(ServiceConstants.EXTRA_LON, lon)
            putExtra(ServiceConstants.EXTRA_SPEED_MS, speedMs)
            putExtra(ServiceConstants.EXTRA_BEARING, bearing)
        }

    fun startSpoofing(
        context: Context,
        lat: Double,
        lon: Double,
    ): Intent =
        Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_START
            putExtra(ServiceConstants.EXTRA_LAT, lat)
            putExtra(ServiceConstants.EXTRA_LON, lon)
        }

    fun stopSpoofing(context: Context): Intent =
        Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_STOP
        }

    fun startRouteReplay(
        context: Context,
        routeId: String,
        speedMs: Double,
        isBackward: Boolean = false,
        followRoadsToStart: Boolean = false,
    ): Intent =
        Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_ROUTE_REPLAY_START
            putExtra(MockLocationService.EXTRA_ROUTE_ID, routeId)
            putExtra(MockLocationService.EXTRA_IS_BACKWARD, isBackward)
            putExtra(MockLocationService.EXTRA_SPEED_MS, speedMs)
            putExtra(MockLocationService.EXTRA_FOLLOW_ROADS_TO_START, followRoadsToStart)
        }

    fun cancelRouteReplay(context: Context): Intent =
        Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_ROUTE_REPLAY_CANCEL
        }

    fun appendWaypoint(
        context: Context,
        waypoint: LatLng,
    ): Intent =
        Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_ROUTE_APPEND_WAYPOINT
            putExtra(MockLocationService.EXTRA_WAYPOINT_LAT, waypoint.latitude)
            putExtra(MockLocationService.EXTRA_WAYPOINT_LON, waypoint.longitude)
        }

    fun pauseRouteReplay(context: Context): Intent =
        Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_ROUTE_REPLAY_PAUSE
        }

    fun resumeRouteReplay(
        context: Context,
        speedMs: Double,
    ): Intent =
        Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_ROUTE_REPLAY_RESUME
            putExtra(MockLocationService.EXTRA_SPEED_MS, speedMs)
        }

    fun stopRouteReplay(context: Context): Intent =
        Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_ROUTE_REPLAY_STOP
        }

    fun jumpToNextWaypoint(context: Context): Intent =
        Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_ROUTE_REPLAY_JUMP_NEXT
        }

    fun jumpToPreviousWaypoint(context: Context): Intent =
        Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_ROUTE_REPLAY_JUMP_PREVIOUS
        }

    fun startEphemeralReplay(
        context: Context,
        waypoints: List<LatLng>,
        speedMs: Double,
    ): Intent {
        require(waypoints.size >= 2) { "Ephemeral replay requires at least 2 waypoints" }
        return Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_ROUTE_REPLAY_START
            putExtra(MockLocationService.EXTRA_IS_EPHEMERAL, true)
            putExtra(MockLocationService.EXTRA_SPEED_MS, speedMs)
            putExtra(ServiceConstants.EXTRA_EPHEMERAL_WAYPOINTS, waypoints.encodeToString())
        }
    }

    /** Encodes waypoints as a compact string: "lat,lon;lat,lon;..." */
    private fun List<LatLng>.encodeToString(): String = joinToString(";") { "${it.latitude},${it.longitude}" }
}
