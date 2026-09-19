package com.locationjoystick.feature.map.impl

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RoamingKind

sealed interface MapAction {
    data class TapToTeleport(
        val position: LatLng,
    ) : MapAction

    data class LongPressTapToWalk(
        val position: LatLng,
    ) : MapAction

    data class RecenterCamera(
        val fallbackPosition: LatLng? = null,
    ) : MapAction

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

    data class SelectPlantingSpeedProfile(
        val id: String,
    ) : MapAction

    data class ToggleRoamingFollowRoads(
        val enabled: Boolean,
    ) : MapAction

    data class ToggleRoamingReturnToStart(
        val enabled: Boolean,
    ) : MapAction

    data class UpdateRoamingKind(
        val kind: RoamingKind,
    ) : MapAction

    data class UpdatePlantingStartRadius(
        val meters: Double,
    ) : MapAction

    data class UpdatePlantingEndRadius(
        val meters: Double,
    ) : MapAction

    data class TogglePlantingInfiniteLoops(
        val enabled: Boolean,
    ) : MapAction

    data class UpdatePlantingLoopCount(
        val count: Int,
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
        val followRoads: Boolean = false,
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
        val followRoadsToStart: Boolean = false,
        val isPlanting: Boolean = false,
        val teleportBetweenWaypoints: Boolean = false,
        val teleportBetweenDelaySeconds: Int = AppConstants.RouteConstants.TELEPORT_BETWEEN_DEFAULT_DELAY_SECONDS,
    ) : MapAction

    data object PauseRouteReplay : MapAction

    data object ResumeRouteReplay : MapAction

    data object StopRouteReplay : MapAction

    data object ToggleRouteControls : MapAction

    data object JumpToNextWaypoint : MapAction

    data object JumpToPreviousWaypoint : MapAction

    data object ToggleRoamingControls : MapAction

    data object ToggleWalkControls : MapAction

    data object ClearPinnedPoint : MapAction

    data object OpenPasteCoordinates : MapAction

    data object ClosePasteCoordinates : MapAction

    data object OpenCaptureCoordinates : MapAction

    data object CloseCaptureCoordinates : MapAction

    data class PinCoordinateTarget(
        val position: LatLng,
    ) : MapAction

    data class SaveFavoriteAt(
        val name: String,
        val position: LatLng,
    ) : MapAction
}
