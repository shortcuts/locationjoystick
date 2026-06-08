package com.locationjoystick.feature.map.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.data.CooldownState
import com.locationjoystick.core.data.DeepLinkRepository
import com.locationjoystick.core.data.RoamingRepository
import com.locationjoystick.core.data.TeleportUseCase
import com.locationjoystick.core.location.MapController
import com.locationjoystick.core.location.isRouteReplay
import com.locationjoystick.core.location.isSpoofing
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RecentSearch
import com.locationjoystick.core.model.RoamingDefaults
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "MapViewModel"

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MapViewModel
    @Inject
    constructor(
        private val mapController: MapController,
        private val roamingRepository: RoamingRepository,
        private val deepLinkRepository: DeepLinkRepository,
        private val teleportUseCase: TeleportUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(MapUiState())
        val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

        // Shared state flows through as separate StateFlows to preserve MapScreen API surface
        val recentSearches: StateFlow<List<RecentSearch>> =
            mapController.sharedState
                .map { it.recentSearches }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        val roamingDefaults: StateFlow<RoamingDefaults> =
            mapController.sharedState
                .map { it.roamingDefaults }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoamingDefaults())

        val completionMessages: SharedFlow<String> = mapController.completionMessages

        init {
            observeSharedState()
            observeCooldownForPendingTap()
            observeDeepLinkCoords()
        }

        private fun observeSharedState() {
            viewModelScope.launch {
                mapController.sharedState.collect { shared ->
                    _uiState.update { current ->
                        current.copy(
                            currentPosition = shared.currentPosition,
                            mockLocationState = shared.mockLocationState,
                            isWalkPaused = shared.isWalkPaused,
                            isRouteReplay = shared.isRouteReplay,
                            routes = shared.routes,
                            favorites = shared.favorites,
                            favoriteCooldownStates = shared.favoriteCooldownStates,
                            routeTrace = shared.routeTrace,
                            walkMode = shared.walkMode,
                            isRoaming = shared.isRoaming,
                            isRoamingPaused = shared.isRoamingPaused,
                            speedUnit = shared.speedUnit,
                        )
                    }
                }
            }
        }

        private fun observeCooldownForPendingTap() {
            viewModelScope.launch {
                _uiState
                    .distinctUntilChangedBy { it.pendingTapPosition }
                    .flatMapLatest { state ->
                        val target = state.pendingTapPosition
                        if (target != null) teleportUseCase.cooldownFor(target) else flowOf(CooldownState.Ready)
                    }.collect { cooldown ->
                        _uiState.update { it.copy(cooldownState = cooldown) }
                    }
            }
        }

        private fun observeDeepLinkCoords() {
            viewModelScope.launch {
                deepLinkRepository.pendingCoords.collect { coords ->
                    _uiState.update { it.copy(pendingTapPosition = coords, pendingCameraTarget = coords) }
                }
            }
        }

        fun onAction(action: MapAction) {
            when (action) {
                // Teleport
                is MapAction.TapToTeleport -> {
                    handleTapToTeleport(action.position)
                }

                is MapAction.ConfirmTeleport -> {
                    mapController.teleportTo(action.position)
                    _uiState.update { it.copy(pendingTapPosition = null) }
                }

                is MapAction.ClearPendingTap -> {
                    _uiState.update { it.copy(pendingTapPosition = null) }
                }

                is MapAction.StopRouteAndTeleport -> {
                    mapController.stopRouteOnly()
                    mapController.teleportTo(action.position)
                    _uiState.update { it.copy(pendingTapPosition = null) }
                }

                is MapAction.SetLocationTo -> {
                    mapController.teleportTo(action.position)
                    _uiState.update { it.copy(showFavoritesSheet = false, favoriteTarget = null) }
                }

                // Walk
                is MapAction.LongPressTapToWalk -> {
                    _uiState.update { it.copy(roamingPreviewWaypoints = null) }
                    mapController.walkTo(action.position)
                }

                is MapAction.WalkViaRoadsTo -> {
                    _uiState.update { it.copy(roamingPreviewWaypoints = null) }
                    mapController.walkViaRoads(action.position)
                }

                is MapAction.WalkStraightTo -> {
                    mapController.walkTo(action.position)
                    _uiState.update { it.copy(showFavoritesSheet = false, favoriteTarget = null) }
                }

                is MapAction.StopRouteAndWalkTo -> {
                    mapController.stopRouteOnly()
                    mapController.walkTo(action.position)
                    _uiState.update { it.copy(pendingTapPosition = null) }
                }

                is MapAction.FinishRouteAndWalkTo -> {
                    mapController.appendWaypointToRoute(action.position)
                    _uiState.update { it.copy(pendingTapPosition = null) }
                }

                is MapAction.AddEphemeralWaypoint -> {
                    mapController.addEphemeralWaypoint(action.position)
                }

                MapAction.PauseWalk -> {
                    mapController.pauseWalk()
                }

                MapAction.ResumeWalk -> {
                    mapController.resumeWalk()
                }

                MapAction.StopWalk -> {
                    mapController.stopWalk()
                    _uiState.update { it.copy(isWalkControlsExpanded = false) }
                }

                MapAction.ToggleWalkControls -> {
                    _uiState.update { it.copy(isWalkControlsExpanded = !it.isWalkControlsExpanded) }
                }

                // Spoofing
                MapAction.StartSpoofing -> {
                    mapController.startSpoofing()
                }

                MapAction.StopSpoofing -> {
                    mapController.stopSpoofing()
                    _uiState.update { it.copy(pendingTapPosition = null, isWalkControlsExpanded = false) }
                }

                MapAction.ClearMap -> {
                    mapController.stopWalk()
                    _uiState.update {
                        it.copy(
                            pendingTapPosition = null,
                            isWalkControlsExpanded = false,
                            roamingPreviewWaypoints = null,
                            showRoamingSheet = false,
                            roamingDraft = null,
                            isRoamingSheetMinimized = false,
                        )
                    }
                }

                // Camera
                MapAction.RecenterCamera -> {
                    _uiState.update { it.copy(isUserPanning = false, pendingCameraTarget = it.currentPosition) }
                }

                MapAction.UserStartedPanning -> {
                    _uiState.update { it.copy(isUserPanning = true) }
                }

                MapAction.CameraTargetConsumed -> {
                    _uiState.update { it.copy(pendingCameraTarget = null) }
                }

                // Favorites
                MapAction.OpenFavoritesPicker -> {
                    _uiState.update { it.copy(showFavoritesSheet = true) }
                }

                MapAction.CloseFavoritesPicker -> {
                    _uiState.update { it.copy(showFavoritesSheet = false, favoriteTarget = null) }
                }

                MapAction.DeselectFavorite -> {
                    _uiState.update { it.copy(favoriteTarget = null) }
                }

                is MapAction.SelectFavorite -> {
                    handleSelectFavorite(action.favorite)
                }

                is MapAction.SaveCurrentLocation -> {
                    mapController.saveCurrentLocation(action.name)
                }

                // Routes
                MapAction.OpenRoutesSheet -> {
                    _uiState.update { it.copy(showRoutesSheet = true) }
                }

                MapAction.CloseRoutesSheet -> {
                    _uiState.update { it.copy(showRoutesSheet = false) }
                }

                is MapAction.StartRouteReplay -> {
                    mapController.startRouteReplay(
                        action.routeId,
                        action.isLooping,
                        action.isReverse,
                        action.isReturnToLocation,
                        action.teleportToStart,
                    )
                    _uiState.update { it.copy(showRoutesSheet = false) }
                }

                MapAction.PauseRouteReplay -> {
                    mapController.pauseRouteReplay()
                }

                MapAction.ResumeRouteReplay -> {
                    mapController.resumeRouteReplay()
                }

                MapAction.StopRouteReplay -> {
                    mapController.stopRouteReplay()
                    _uiState.update { it.copy(isRouteControlsExpanded = false) }
                }

                MapAction.ToggleRouteControls -> {
                    _uiState.update { it.copy(isRouteControlsExpanded = !it.isRouteControlsExpanded) }
                }

                // Roaming
                MapAction.OpenRoamingSheet -> {
                    _uiState.update {
                        it.copy(
                            showRoamingSheet = true,
                            roamingDraft = mapController.sharedState.value.roamingDefaults,
                            roamingPreviewWaypoints = null,
                            isRoamingSheetMinimized = false,
                        )
                    }
                }

                MapAction.DismissRoamingSheet -> {
                    _uiState.update {
                        it.copy(
                            showRoamingSheet = false,
                            roamingDraft = null,
                            roamingPreviewWaypoints = null,
                            isRoamingSheetMinimized = false,
                        )
                    }
                }

                MapAction.GenerateRoamingPreview -> {
                    generateRoamingPreview()
                }

                MapAction.MinimizeRoamingSheet -> {
                    _uiState.update { it.copy(isRoamingSheetMinimized = true) }
                }

                MapAction.ExpandRoamingSheet -> {
                    _uiState.update { it.copy(isRoamingSheetMinimized = false) }
                }

                is MapAction.UpdateRoamingRadius -> {
                    _uiState.update { s -> s.copy(roamingDraft = s.roamingDraft?.copy(radiusMeters = action.meters)) }
                }

                is MapAction.UpdateRoamingDistance -> {
                    _uiState.update { s -> s.copy(roamingDraft = s.roamingDraft?.copy(distanceMeters = action.meters)) }
                }

                is MapAction.SelectRoamingSpeedProfile -> {
                    _uiState.update { s -> s.copy(roamingDraft = s.roamingDraft?.copy(speedProfileId = action.id)) }
                }

                is MapAction.ToggleRoamingFollowRoads -> {
                    _uiState.update { s -> s.copy(roamingDraft = s.roamingDraft?.copy(followRoads = action.enabled)) }
                }

                is MapAction.ToggleRoamingReturnToStart -> {
                    _uiState.update { s -> s.copy(roamingDraft = s.roamingDraft?.copy(returnToInitialLocation = action.enabled)) }
                }

                MapAction.StartRoaming -> {
                    val draft = _uiState.value.roamingDraft ?: return
                    val position = mapController.sharedState.value.currentPosition ?: return
                    mapController.startRoaming(draft, position, _uiState.value.roamingPreviewWaypoints)
                    _uiState.update { it.copy(showRoamingSheet = false, roamingDraft = null, isRoamingSheetMinimized = false) }
                }

                MapAction.StopRoaming -> {
                    mapController.stopRoaming()
                    _uiState.update { it.copy(isRoamingControlsExpanded = false) }
                }

                MapAction.PauseRoaming -> {
                    mapController.pauseRoaming()
                }

                MapAction.ResumeRoaming -> {
                    mapController.resumeRoaming()
                }

                MapAction.ToggleRoamingControls -> {
                    _uiState.update { it.copy(isRoamingControlsExpanded = !it.isRoamingControlsExpanded) }
                }
            }
        }

        fun addRecentSearch(
            displayName: String,
            lat: Double,
            lon: Double,
        ) {
            mapController.addRecentSearch(displayName, lat, lon)
        }

        private fun handleTapToTeleport(position: LatLng) {
            _uiState.update { it.copy(roamingPreviewWaypoints = null) }
            if (mapController.sharedState.value.isSpoofing) {
                _uiState.update { it.copy(pendingTapPosition = position) }
            } else {
                mapController.teleportTo(position)
            }
        }

        private fun handleSelectFavorite(favorite: FavoriteLocation) {
            if (mapController.sharedState.value.isSpoofing) {
                _uiState.update { it.copy(favoriteTarget = favorite, pendingCameraTarget = favorite.position) }
            } else {
                mapController.teleportTo(favorite.position)
                _uiState.update { it.copy(showFavoritesSheet = false) }
            }
        }

        private fun generateRoamingPreview() {
            val draft = _uiState.value.roamingDraft ?: return
            val center = mapController.sharedState.value.currentPosition ?: return
            viewModelScope.launch {
                val waypoints =
                    roamingRepository.generatePreviewRoute(
                        center = center,
                        radiusMeters = draft.radiusMeters,
                        followRoads = draft.followRoads,
                        speedProfileId = draft.speedProfileId,
                    )
                _uiState.update { it.copy(roamingPreviewWaypoints = waypoints) }
            }
        }
    }
