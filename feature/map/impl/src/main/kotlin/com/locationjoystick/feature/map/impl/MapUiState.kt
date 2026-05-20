package com.locationjoystick.feature.map.impl

import com.locationjoystick.core.data.CooldownState
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.SpeedUnit

data class RoamingDraft(
    val radiusMeters: Double,
    val distanceMeters: Double,
    val speedProfileId: String,
    val followRoads: Boolean,
    val returnToInitialLocation: Boolean,
)

data class MapUiState(
    val currentPosition: LatLng? = null,
    val mockLocationState: MockLocationState = MockLocationState.IDLE,
    val routes: List<Route> = emptyList(),
    val isUserPanning: Boolean = false,
    val showFavoritesSheet: Boolean = false,
    val favorites: List<FavoriteLocation> = emptyList(),
    val favoriteTarget: FavoriteLocation? = null,
    val pendingCameraTarget: LatLng? = null,
    val pendingTapPosition: LatLng? = null,
    val routeTrace: List<LatLng>? = null,
    val walkTarget: LatLng? = null,
    val walkStart: LatLng? = null,
    val isWalkPaused: Boolean = false,
    val isRouteReplay: Boolean = false,
    val showRoamingSheet: Boolean = false,
    val roamingDraft: RoamingDraft? = null,
    val isRoaming: Boolean = false,
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val cooldownState: CooldownState = CooldownState.Ready,
)

val MapUiState.isSpoofing: Boolean
    get() = mockLocationState == MockLocationState.RUNNING
