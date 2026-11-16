package com.locationjoystick.core.location

import android.content.Intent
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.constants.AppConstants.ServiceConstants
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RouteStartConfig

/** One handled `onStartCommand` action with its extras already read. */
internal sealed interface ServiceCommand {
    data class Start(
        val lat: Double,
        val lon: Double,
    ) : ServiceCommand

    data class EnterFollower(
        val host: String,
        val port: Int,
        val groupId: String,
    ) : ServiceCommand

    data object ExitFollower : ServiceCommand

    data object ResumeFollower : ServiceCommand

    data class StartLeader(
        val groupId: String,
    ) : ServiceCommand

    data object ExitLeader : ServiceCommand

    data object OsRestart : ServiceCommand

    data object Stop : ServiceCommand

    data object ParkKeepWidget : ServiceCommand

    data object ClearMotionVector : ServiceCommand

    data class UpdatePosition(
        val lat: Double,
        val lon: Double,
        val speedMs: Float,
        val bearing: Float,
        val isTeleport: Boolean,
    ) : ServiceCommand

    data class StartEphemeralReplay(
        val waypoints: List<LatLng>,
        val speedMs: Double,
        val stopIndices: List<Int>?,
    ) : ServiceCommand

    data class StartRouteReplay(
        val routeId: String,
        val speedMs: Double,
        val config: RouteStartConfig,
        val returnPosition: LatLng?,
    ) : ServiceCommand

    data object ReplayPause : ServiceCommand

    data class ReplayResume(
        val speedMs: Double,
    ) : ServiceCommand

    data object ReplayStop : ServiceCommand

    data object ReplayCancel : ServiceCommand

    data object ReplayJumpNext : ServiceCommand

    data object ReplayJumpPrevious : ServiceCommand

    data object FollowerTeleport : ServiceCommand

    data class AppendWaypoint(
        val position: LatLng,
        val isStop: Boolean,
    ) : ServiceCommand
}

/** Null means the action is unknown or a required extra is missing; the service ignores the intent. */
internal fun Intent?.toServiceCommand(currentPosition: LatLng): ServiceCommand? {
    val intent = this ?: return ServiceCommand.OsRestart
    return when (intent.action) {
        null -> {
            ServiceCommand.OsRestart
        }

        MockLocationService.ACTION_START -> {
            ServiceCommand.Start(
                intent.getDoubleExtra(ServiceConstants.EXTRA_LAT, AppConstants.MapConstants.DEFAULT_LAT),
                intent.getDoubleExtra(ServiceConstants.EXTRA_LON, AppConstants.MapConstants.DEFAULT_LON),
            )
        }

        MockLocationService.ACTION_ENTER_FOLLOWER -> {
            ServiceCommand.EnterFollower(
                host = intent.getStringExtra(ServiceConstants.EXTRA_FOLLOWER_HOST) ?: return null,
                port = intent.getIntExtra(ServiceConstants.EXTRA_FOLLOWER_PORT, 0),
                groupId = intent.getStringExtra(ServiceConstants.EXTRA_FOLLOWER_GROUP_ID) ?: return null,
            )
        }

        MockLocationService.ACTION_EXIT_FOLLOWER -> {
            ServiceCommand.ExitFollower
        }

        ServiceConstants.ACTION_RESUME_FOLLOWER -> {
            ServiceCommand.ResumeFollower
        }

        ServiceConstants.ACTION_START_LEADER -> {
            ServiceCommand.StartLeader(intent.getStringExtra(ServiceConstants.EXTRA_LEADER_GROUP_ID) ?: return null)
        }

        ServiceConstants.ACTION_EXIT_LEADER -> {
            ServiceCommand.ExitLeader
        }

        MockLocationService.ACTION_STOP -> {
            ServiceCommand.Stop
        }

        MockLocationService.ACTION_PARK_KEEP_WIDGET -> {
            ServiceCommand.ParkKeepWidget
        }

        MockLocationService.ACTION_CLEAR_MOTION_VECTOR -> {
            ServiceCommand.ClearMotionVector
        }

        MockLocationService.ACTION_UPDATE_POSITION -> {
            ServiceCommand.UpdatePosition(
                lat = intent.getDoubleExtra(ServiceConstants.EXTRA_LAT, currentPosition.latitude),
                lon = intent.getDoubleExtra(ServiceConstants.EXTRA_LON, currentPosition.longitude),
                speedMs = intent.getFloatExtra(ServiceConstants.EXTRA_SPEED_MS, 0f),
                bearing = intent.getFloatExtra(ServiceConstants.EXTRA_BEARING, 0f),
                isTeleport = intent.getBooleanExtra(ServiceConstants.EXTRA_IS_TELEPORT, false),
            )
        }

        MockLocationService.ACTION_ROUTE_REPLAY_START -> {
            val speedMs =
                intent.getDoubleExtra(MockLocationService.EXTRA_SPEED_MS, AppConstants.LocationConstants.DEFAULT_REPLAY_SPEED_MS)
            if (intent.getBooleanExtra(MockLocationService.EXTRA_IS_EPHEMERAL, false)) {
                val waypoints =
                    intent
                        .getStringExtra(ServiceConstants.EXTRA_EPHEMERAL_WAYPOINTS)
                        ?.split(";")
                        ?.mapNotNull { seg ->
                            val parts = seg.split(",")
                            val lat = parts.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
                            val lon = parts.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
                            LatLng(lat, lon)
                        }?.takeIf { it.size >= 2 }
                        ?: return null
                ServiceCommand.StartEphemeralReplay(
                    waypoints,
                    speedMs,
                    intent.getIntArrayExtra(MockLocationService.EXTRA_EPHEMERAL_STOP_INDICES)?.toList(),
                )
            } else {
                val routeId = intent.getStringExtra(MockLocationService.EXTRA_ROUTE_ID) ?: return null
                ServiceCommand.StartRouteReplay(routeId, speedMs, intent.toRouteStartConfig(), intent.returnPositionOrNull())
            }
        }

        MockLocationService.ACTION_ROUTE_REPLAY_PAUSE -> {
            ServiceCommand.ReplayPause
        }

        MockLocationService.ACTION_ROUTE_REPLAY_RESUME -> {
            ServiceCommand.ReplayResume(
                intent.getDoubleExtra(MockLocationService.EXTRA_SPEED_MS, AppConstants.LocationConstants.DEFAULT_REPLAY_SPEED_MS),
            )
        }

        MockLocationService.ACTION_ROUTE_REPLAY_STOP -> {
            ServiceCommand.ReplayStop
        }

        MockLocationService.ACTION_ROUTE_REPLAY_CANCEL -> {
            ServiceCommand.ReplayCancel
        }

        MockLocationService.ACTION_ROUTE_REPLAY_JUMP_NEXT -> {
            ServiceCommand.ReplayJumpNext
        }

        MockLocationService.ACTION_ROUTE_REPLAY_JUMP_PREVIOUS -> {
            ServiceCommand.ReplayJumpPrevious
        }

        MockLocationService.ACTION_FOLLOWER_TELEPORT -> {
            ServiceCommand.FollowerTeleport
        }

        MockLocationService.ACTION_ROUTE_APPEND_WAYPOINT -> {
            ServiceCommand.AppendWaypoint(
                LatLng(
                    intent.getDoubleExtra(MockLocationService.EXTRA_WAYPOINT_LAT, 0.0),
                    intent.getDoubleExtra(MockLocationService.EXTRA_WAYPOINT_LON, 0.0),
                ),
                intent.getBooleanExtra(MockLocationService.EXTRA_WAYPOINT_IS_STOP, true),
            )
        }

        else -> {
            null
        }
    }
}
