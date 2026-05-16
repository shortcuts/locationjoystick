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

    data object StartSpoofing : MapAction

    data object StopSpoofing : MapAction

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
}
