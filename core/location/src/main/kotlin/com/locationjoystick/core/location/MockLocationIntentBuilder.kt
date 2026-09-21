package com.locationjoystick.core.location

import android.content.Context
import android.content.Intent
import com.locationjoystick.core.common.constants.AppConstants.ServiceConstants
import com.locationjoystick.core.common.util.clampTeleportBetweenDelaySeconds
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RouteStartConfig

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

    fun parkSpoofingKeepWidget(context: Context): Intent =
        Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_PARK_KEEP_WIDGET
        }

    fun startRouteReplay(
        context: Context,
        routeId: String,
        speedMs: Double,
        config: RouteStartConfig,
        returnPosition: LatLng? = null,
    ): Intent =
        Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_ROUTE_REPLAY_START
            putExtra(MockLocationService.EXTRA_ROUTE_ID, routeId)
            putExtra(MockLocationService.EXTRA_IS_BACKWARD, config.isReverse)
            putExtra(MockLocationService.EXTRA_SPEED_MS, speedMs)
            putExtra(MockLocationService.EXTRA_IS_LOOPING, config.isLooping)
            putExtra(MockLocationService.EXTRA_FOLLOW_ROADS_TO_START, config.followRoadsToStart)
            putExtra(MockLocationService.EXTRA_TELEPORT_TO_START, config.teleportToStart)
            putExtra(MockLocationService.EXTRA_IS_PLANTING, config.isPlanting)
            putExtra(MockLocationService.EXTRA_TELEPORT_BETWEEN_WAYPOINTS, config.teleportBetweenWaypoints)
            putExtra(MockLocationService.EXTRA_TELEPORT_BETWEEN_DELAY_SECONDS, config.teleportBetweenDelaySeconds)
            if (returnPosition != null) {
                putExtra(MockLocationService.EXTRA_RETURN_LAT, returnPosition.latitude)
                putExtra(MockLocationService.EXTRA_RETURN_LON, returnPosition.longitude)
            }
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

/** Reads the route-start extras written by [MockLocationIntentBuilder.startRouteReplay]; defaults come from [RouteStartConfig]. */
internal fun Intent.toRouteStartConfig(): RouteStartConfig {
    val d = RouteStartConfig()
    val teleportToStart = getBooleanExtra(MockLocationService.EXTRA_TELEPORT_TO_START, d.teleportToStart)
    return RouteStartConfig(
        isLooping = getBooleanExtra(MockLocationService.EXTRA_IS_LOOPING, d.isLooping),
        isReverse = getBooleanExtra(MockLocationService.EXTRA_IS_BACKWARD, d.isReverse),
        followRoadsToStart = getBooleanExtra(MockLocationService.EXTRA_FOLLOW_ROADS_TO_START, d.followRoadsToStart),
        teleportToStart = teleportToStart,
        isPlanting = getBooleanExtra(MockLocationService.EXTRA_IS_PLANTING, d.isPlanting),
        teleportBetweenWaypoints =
            getBooleanExtra(MockLocationService.EXTRA_TELEPORT_BETWEEN_WAYPOINTS, d.teleportBetweenWaypoints) &&
                teleportToStart,
        teleportBetweenDelaySeconds =
            clampTeleportBetweenDelaySeconds(
                getIntExtra(MockLocationService.EXTRA_TELEPORT_BETWEEN_DELAY_SECONDS, d.teleportBetweenDelaySeconds),
            ),
    )
}

internal fun Intent.returnPositionOrNull(): LatLng? {
    val lat = getDoubleExtra(MockLocationService.EXTRA_RETURN_LAT, Double.NaN)
    val lon = getDoubleExtra(MockLocationService.EXTRA_RETURN_LON, Double.NaN)
    return if (!lat.isNaN() && !lon.isNaN()) LatLng(lat, lon) else null
}
