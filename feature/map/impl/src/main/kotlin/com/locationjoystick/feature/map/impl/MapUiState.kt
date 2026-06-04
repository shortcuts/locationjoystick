package com.locationjoystick.feature.map.impl

import com.locationjoystick.core.data.CooldownState
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.SpeedUnit

/** Represents the current walk/ephemeral-replay state shown in the map UI. */
sealed interface WalkMode {
    data object Idle : WalkMode

    /** Walking toward a single target via [WalkCoordinator]. */
    data class Walking(
        val target: LatLng,
        val start: LatLng?,
        val isViaRoads: Boolean = false,
    ) : WalkMode

    /** Replaying a chain of taps (ephemeral route) via [RouteReplayEngine]. */
    data class EphemeralReplay(
        val waypoints: List<LatLng>,
        val followRoads: Boolean = false,
    ) : WalkMode
}

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
    val walkMode: WalkMode = WalkMode.Idle,
    val isWalkPaused: Boolean = false,
    val isWalkControlsExpanded: Boolean = false,
    val isRouteReplay: Boolean = false,
    val showRoutesSheet: Boolean = false,
    val isRouteControlsExpanded: Boolean = false,
    val showRoamingSheet: Boolean = false,
    val roamingDraft: RoamingDefaults? = null,
    val isRoaming: Boolean = false,
    val isRoamingPaused: Boolean = false,
    val isRoamingControlsExpanded: Boolean = false,
    val roamingPreviewWaypoints: List<LatLng>? = null,
    val isRoamingSheetMinimized: Boolean = false,
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val cooldownState: CooldownState = CooldownState.Ready,
    val favoriteCooldownStates: Map<String, CooldownState> = emptyMap(),
)

// Convenience accessors
val MapUiState.walkTarget: LatLng? get() = (walkMode as? WalkMode.Walking)?.target
val MapUiState.walkStart: LatLng? get() = (walkMode as? WalkMode.Walking)?.start
val MapUiState.ephemeralWaypoints: List<LatLng>
    get() = (walkMode as? WalkMode.EphemeralReplay)?.waypoints ?: emptyList()
val MapUiState.isWalkActive: Boolean get() = walkMode !is WalkMode.Idle

val MapUiState.isSpoofing: Boolean
    get() = mockLocationState == MockLocationState.RUNNING

val MapUiState.isRoutePaused: Boolean
    get() = isRouteReplay && mockLocationState == MockLocationState.PAUSED
