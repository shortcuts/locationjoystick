package com.locationjoystick.feature.map.impl

import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng

sealed interface MapAction {
    data class TapToTeleport(
        val position: LatLng,
    ) : MapAction

    data class LongPressTapToWalk(
        val position: LatLng,
    ) : MapAction

    data object RecenterCamera : MapAction

    data object UserStartedPanning : MapAction

    data object OpenFavoritesPicker : MapAction

    data object CloseFavoritesPicker : MapAction

    data class SelectFavorite(
        val favorite: FavoriteLocation,
    ) : MapAction

    data object DeselectFavorite : MapAction

    data object CameraTargetConsumed : MapAction

    data class SetLocationTo(
        val position: LatLng,
    ) : MapAction

    data class WalkStraightTo(
        val position: LatLng,
    ) : MapAction

    data class ConfirmTeleport(
        val position: LatLng,
    ) : MapAction

    data object ClearPendingTap : MapAction

    data class SaveCurrentLocation(
        val name: String,
    ) : MapAction

    data object PauseWalk : MapAction

    data object ResumeWalk : MapAction

    data object StopWalk : MapAction

    data class StopRouteAndTeleport(
        val position: LatLng,
    ) : MapAction

    data class StopRouteAndWalkTo(
        val position: LatLng,
    ) : MapAction

    data class FinishRouteAndWalkTo(
        val position: LatLng,
    ) : MapAction

    data object OpenRoamingSheet : MapAction

    data object DismissRoamingSheet : MapAction

    data class UpdateRoamingRadius(
        val meters: Double,
    ) : MapAction

    data class UpdateRoamingDistance(
        val meters: Double,
    ) : MapAction

    data class SelectRoamingSpeedProfile(
        val id: String,
    ) : MapAction

    data class ToggleRoamingFollowRoads(
        val enabled: Boolean,
    ) : MapAction

    data class ToggleRoamingReturnToStart(
        val enabled: Boolean,
    ) : MapAction

    data object StartRoaming : MapAction

    data object StopRoaming : MapAction

    data object PauseRoaming : MapAction

    data object ResumeRoaming : MapAction

    data object GenerateRoamingPreview : MapAction

    data object MinimizeRoamingSheet : MapAction

    data object ExpandRoamingSheet : MapAction

    data object ClearMap : MapAction

    data class AddEphemeralWaypoint(
        val position: LatLng,
    ) : MapAction

    data class WalkViaRoadsTo(
        val position: LatLng,
    ) : MapAction

    data object OpenRoutesSheet : MapAction

    data object CloseRoutesSheet : MapAction

    data class StartRouteReplay(
        val routeId: String,
        val isLooping: Boolean = false,
        val isReverse: Boolean = false,
        val isReturnToLocation: Boolean = false,
        val teleportToStart: Boolean = false,
    ) : MapAction

    data object PauseRouteReplay : MapAction

    data object ResumeRouteReplay : MapAction

    data object StopRouteReplay : MapAction

    data object ToggleRouteControls : MapAction

    data object ToggleRoamingControls : MapAction

    data object ToggleWalkControls : MapAction

    data object ClearPinnedPoint : MapAction
}
