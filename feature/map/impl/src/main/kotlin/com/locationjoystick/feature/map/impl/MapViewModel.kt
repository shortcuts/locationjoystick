package com.locationjoystick.feature.map.impl

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.formatCapturedPointsForClipboard
import com.locationjoystick.core.common.util.parsePastedCoordinates
import com.locationjoystick.core.data.CaptureCoordinatesRepository
import com.locationjoystick.core.data.CooldownState
import com.locationjoystick.core.data.DeepLinkRepository
import com.locationjoystick.core.data.GpxOpenRepository
import com.locationjoystick.core.data.RealLocationRepository
import com.locationjoystick.core.data.RoamingRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.data.TeleportUseCase
import com.locationjoystick.core.location.MapController
import com.locationjoystick.core.location.RouteStartConfig
import com.locationjoystick.core.location.isRouteReplay
import com.locationjoystick.core.location.isSpoofing
import com.locationjoystick.core.location.nonPositionKey
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RecentSearch
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.toConfig
import com.locationjoystick.feature.map.impl.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
        private val gpxOpenRepository: GpxOpenRepository,
        private val teleportUseCase: TeleportUseCase,
        private val settingsRepository: SettingsRepository,
        private val captureCoordinatesRepository: CaptureCoordinatesRepository,
        private val realLocationRepository: RealLocationRepository,
        @ApplicationContext private val context: Context,
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
        val routingErrors: SharedFlow<String> = mapController.routingErrors
        private val _cameraMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val cameraMessages: SharedFlow<String> = _cameraMessages.asSharedFlow()

        init {
            observeSharedState()
            observeCooldownForPendingTap()
            observeDeepLinkCoords()
            observeGpxOpen()
            observeMapFabFeatures()
            observeCaptureHelperOpen()
        }

        private fun observeCaptureHelperOpen() {
            viewModelScope.launch {
                captureCoordinatesRepository.helperOpen.collect { open ->
                    _uiState.update { it.copy(showCaptureCoordinatesSheet = open) }
                }
            }
        }

        private fun observeMapFabFeatures() {
            viewModelScope.launch {
                settingsRepository
                    .getMapFeatureOrder()
                    .distinctUntilChanged()
                    .collect { order ->
                        _uiState.update { it.copy(mapFeatureOrder = order) }
                    }
            }
            viewModelScope.launch {
                settingsRepository
                    .getEnabledMapFeatures()
                    .distinctUntilChanged()
                    .collect { enabled ->
                        _uiState.update { it.copy(enabledMapFeatures = enabled) }
                    }
            }
            viewModelScope.launch {
                combine(
                    settingsRepository.getHideTeleportFeatures(),
                    settingsRepository.getShowRouteJumpButtons(),
                ) { hideTeleport, showJumpButtons -> hideTeleport to showJumpButtons }
                    .distinctUntilChanged()
                    .collect { (hideTeleport, showJumpButtons) ->
                        _uiState.update {
                            it.copy(hideTeleportFeatures = hideTeleport, showRouteJumpButtons = showJumpButtons)
                        }
                    }
            }
        }

        private fun observeSharedState() {
            viewModelScope.launch {
                mapController.sharedState
                    .map { it.currentPosition }
                    .distinctUntilChanged()
                    .collect { pos ->
                        _uiState.update { it.copy(currentPosition = pos) }
                    }
            }
            viewModelScope.launch {
                mapController.sharedState
                    .distinctUntilChangedBy { it.nonPositionKey() }
                    .collect { shared ->
                        _uiState.update { current ->
                            current.copy(
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
                                jitterRadiusMeters = shared.jitterRadiusMeters,
                                debugStatsEnabled = shared.debugStatsEnabled,
                                isRoadRouteFetchInFlight = shared.isRoadRouteFetchInFlight,
                                routeProgress = shared.routeProgress,
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
                    pinCoordinateTarget(coords)
                    deepLinkRepository.consume()
                }
            }
        }

        private fun observeGpxOpen() {
            viewModelScope.launch {
                gpxOpenRepository.pending.collect { pending ->
                    _uiState.update {
                        it.copy(
                            showPasteCoordinatesSheet = true,
                            pasteSheetTitle = PasteSheetTitle.GPX,
                            pasteInitialText = formatCapturedPointsForClipboard(pending.points),
                            pasteInitialRouteName = pending.suggestedName,
                            pasteFormNonce = it.pasteFormNonce + 1,
                        )
                    }
                }
            }
        }

        private fun hidePasteCoordinatesSheet(resetForm: Boolean = false) {
            gpxOpenRepository.consume()
            _uiState.update {
                if (resetForm) {
                    it.copy(
                        showPasteCoordinatesSheet = false,
                        pasteSheetTitle = PasteSheetTitle.DEFAULT,
                        pasteInitialText = "",
                        pasteInitialRouteName = "",
                    )
                } else {
                    it.copy(showPasteCoordinatesSheet = false)
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
                    _uiState.update { it.copy(pendingTapPosition = null, isPendingTapSheetOpen = false) }
                }

                is MapAction.ClearPendingTap -> {
                    _uiState.update { it.copy(isPendingTapSheetOpen = false) }
                }

                MapAction.ClearPinnedPoint -> {
                    _uiState.update { it.copy(pendingTapPosition = null, isPendingTapSheetOpen = false) }
                }

                is MapAction.StopRouteAndTeleport -> {
                    mapController.stopRouteOnly()
                    mapController.teleportTo(action.position)
                    _uiState.update { it.copy(pendingTapPosition = null, isPendingTapSheetOpen = false) }
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
                    _uiState.update { it.copy(pendingTapPosition = null, isPendingTapSheetOpen = false) }
                }

                is MapAction.FinishRouteAndWalkTo -> {
                    mapController.appendWaypointToRoute(action.position)
                    _uiState.update { it.copy(pendingTapPosition = null, isPendingTapSheetOpen = false) }
                }

                is MapAction.AddEphemeralWaypoint -> {
                    mapController.addEphemeralWaypoint(action.position, action.followRoads)
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

                MapAction.ClearMap -> {
                    mapController.stopWalk()
                    _uiState.update {
                        it.copy(
                            pendingTapPosition = null,
                            isPendingTapSheetOpen = false,
                            isWalkControlsExpanded = false,
                            roamingPreviewWaypoints = null,
                            showRoamingSheet = false,
                            roamingDraft = null,
                            isRoamingSheetMinimized = false,
                        )
                    }
                    hidePasteCoordinatesSheet()
                }

                // Camera
                is MapAction.RecenterCamera -> {
                    val state = _uiState.value.mockLocationState
                    if (state != com.locationjoystick.core.model.MockLocationState.IDLE &&
                        state != com.locationjoystick.core.model.MockLocationState.ERROR
                    ) {
                        _uiState.update {
                            it.copy(
                                isUserPanning = false,
                                pendingCameraTarget = it.currentPosition,
                                pendingTapPosition = null,
                                isPendingTapSheetOpen = false,
                            )
                        }
                    } else {
                        viewModelScope.launch {
                            realLocationRepository
                                .getCurrentPosition()
                                .onSuccess { position ->
                                    _uiState.update {
                                        it.copy(
                                            isUserPanning = true,
                                            pendingCameraTarget = position,
                                            pendingTapPosition = null,
                                            isPendingTapSheetOpen = false,
                                        )
                                    }
                                }.onFailure { error ->
                                    val fallback = action.fallbackPosition
                                    if (fallback != null) {
                                        _uiState.update {
                                            it.copy(
                                                isUserPanning = true,
                                                pendingCameraTarget = fallback,
                                                pendingTapPosition = null,
                                                isPendingTapSheetOpen = false,
                                            )
                                        }
                                    } else {
                                        _cameraMessages.emit(
                                            error.message
                                                ?: context.getString(R.string.map_recenter_gps_fallback_error),
                                        )
                                    }
                                }
                        }
                    }
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
                        RouteStartConfig(
                            isLooping = action.isLooping,
                            isReverse = action.isReverse,
                            isReturnToLocation = action.isReturnToLocation,
                            followRoadsToStart = action.followRoadsToStart,
                            isPlanting = action.isPlanting,
                            teleportBetweenWaypoints = action.teleportBetweenWaypoints,
                            teleportBetweenDelaySeconds = action.teleportBetweenDelaySeconds,
                        ),
                    )
                    if (!action.followRoadsToStart) {
                        _uiState.update { it.copy(showRoutesSheet = false) }
                    }
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

                MapAction.JumpToNextWaypoint -> {
                    mapController.jumpToNextWaypoint()
                }

                MapAction.JumpToPreviousWaypoint -> {
                    mapController.jumpToPreviousWaypoint()
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

                is MapAction.SelectPlantingSpeedProfile -> {
                    _uiState.update { s -> s.copy(roamingDraft = s.roamingDraft?.copy(plantingSpeedProfileId = action.id)) }
                }

                is MapAction.ToggleRoamingFollowRoads -> {
                    _uiState.update { s -> s.copy(roamingDraft = s.roamingDraft?.copy(followRoads = action.enabled)) }
                }

                is MapAction.ToggleRoamingReturnToStart -> {
                    _uiState.update { s -> s.copy(roamingDraft = s.roamingDraft?.copy(returnToInitialLocation = action.enabled)) }
                }

                is MapAction.UpdateRoamingKind -> {
                    _uiState.update { s ->
                        val sameKind = s.roamingDraft?.kind == action.kind
                        s.copy(
                            roamingDraft = s.roamingDraft?.copy(kind = action.kind),
                            roamingPreviewWaypoints = if (sameKind) s.roamingPreviewWaypoints else null,
                        )
                    }
                }

                is MapAction.UpdatePlantingStartRadius -> {
                    _uiState.update { s -> s.copy(roamingDraft = s.roamingDraft?.copy(plantingStartRadiusMeters = action.meters)) }
                }

                is MapAction.UpdatePlantingEndRadius -> {
                    _uiState.update { s -> s.copy(roamingDraft = s.roamingDraft?.copy(plantingEndRadiusMeters = action.meters)) }
                }

                is MapAction.TogglePlantingInfiniteLoops -> {
                    _uiState.update { s -> s.copy(roamingDraft = s.roamingDraft?.copy(plantingInfiniteLoops = action.enabled)) }
                }

                is MapAction.UpdatePlantingLoopCount -> {
                    _uiState.update { s -> s.copy(roamingDraft = s.roamingDraft?.copy(plantingLoopCount = action.count)) }
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

                MapAction.OpenPasteCoordinates -> {
                    gpxOpenRepository.consume()
                    _uiState.update {
                        it.copy(
                            showPasteCoordinatesSheet = true,
                            pasteSheetTitle = PasteSheetTitle.DEFAULT,
                            pasteInitialText = "",
                            pasteInitialRouteName = "",
                            pasteFormNonce = it.pasteFormNonce + 1,
                        )
                    }
                }

                MapAction.ClosePasteCoordinates -> {
                    hidePasteCoordinatesSheet(resetForm = true)
                }

                MapAction.OpenCaptureCoordinates -> {
                    hidePasteCoordinatesSheet()
                    viewModelScope.launch { captureCoordinatesRepository.setHelperOpen(true) }
                }

                MapAction.CloseCaptureCoordinates -> {
                    viewModelScope.launch { captureCoordinatesRepository.setHelperOpen(false) }
                }

                is MapAction.PinCoordinateTarget -> {
                    pinCoordinateTarget(action.position)
                }

                is MapAction.SaveFavoriteAt -> {
                    mapController.saveFavorite(action.name, action.position)
                    hidePasteCoordinatesSheet()
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

        fun parsePastedCoordinate(text: String): LatLng? = parsePastedCoordinates(text).firstOrNull()

        fun applyPastedCoordinates(text: String): Boolean {
            val point = parsePastedCoordinate(text) ?: return false
            pinCoordinateTarget(point)
            return true
        }

        fun teleportFromPastedCoordinates(point: LatLng) {
            onAction(MapAction.ConfirmTeleport(point))
            hidePasteCoordinatesSheet()
        }

        fun walkFromPastedCoordinates(
            point: LatLng,
            viaRoads: Boolean,
        ) {
            if (viaRoads) {
                onAction(MapAction.WalkViaRoadsTo(point))
            } else {
                onAction(MapAction.LongPressTapToWalk(point))
            }
            hidePasteCoordinatesSheet()
        }

        fun savePastedFavorite(
            name: String,
            point: LatLng,
        ) {
            mapController.saveFavorite(name, point)
            hidePasteCoordinatesSheet()
        }

        fun savePastedRoute(
            name: String,
            points: List<LatLng>,
        ) {
            if (name.isBlank() || points.size < 2) return
            mapController.savePastedRoute(name, points)
            hidePasteCoordinatesSheet()
        }

        fun startPastedRoute(
            points: List<LatLng>,
            loop: Boolean,
            reverse: Boolean,
            returnToLocation: Boolean,
            followRoads: Boolean,
            planting: Boolean,
            teleportBetweenWaypoints: Boolean = false,
            teleportBetweenDelaySeconds: Int = AppConstants.RouteConstants.TELEPORT_BETWEEN_DEFAULT_DELAY_SECONDS,
        ) {
            if (points.size < 2) return
            mapController.startPastedRouteReplay(
                points = points,
                config =
                    RouteStartConfig(
                        isLooping = loop,
                        isReverse = reverse,
                        isReturnToLocation = returnToLocation,
                        followRoadsToStart = followRoads,
                        isPlanting = planting,
                        teleportBetweenWaypoints = teleportBetweenWaypoints,
                        teleportBetweenDelaySeconds = teleportBetweenDelaySeconds,
                    ),
            )
            hidePasteCoordinatesSheet()
        }

        private fun pinCoordinateTarget(coords: LatLng) {
            gpxOpenRepository.consume()
            _uiState.update {
                it.copy(
                    pendingTapPosition = coords,
                    pendingCameraTarget = coords,
                    isPendingTapSheetOpen = true,
                    showPasteCoordinatesSheet = false,
                    roamingPreviewWaypoints = null,
                )
            }
        }

        private fun handleTapToTeleport(position: LatLng) {
            _uiState.update { it.copy(roamingPreviewWaypoints = null) }
            if (mapController.sharedState.value.isSpoofing) {
                _uiState.update { it.copy(pendingTapPosition = position, isPendingTapSheetOpen = true) }
            } else {
                mapController.teleportTo(position)
            }
        }

        private fun handleSelectFavorite(favorite: FavoriteLocation) {
            if (mapController.sharedState.value.isSpoofing) {
                _uiState.update { it.copy(favoriteTarget = favorite, pendingCameraTarget = favorite.position) }
            } else {
                mapController.teleportTo(favorite.position)
                _uiState.update { it.copy(showFavoritesSheet = false, pendingCameraTarget = favorite.position) }
            }
        }

        private fun generateRoamingPreview() {
            val draft = _uiState.value.roamingDraft ?: return
            val center = mapController.sharedState.value.currentPosition ?: return
            viewModelScope.launch {
                _uiState.update { it.copy(isRoamingPreviewLoading = true) }
                try {
                    val config = draft.toConfig(center)
                    val waypoints = roamingRepository.planRoute(config)
                    _uiState.update { it.copy(roamingPreviewWaypoints = waypoints) }
                } finally {
                    _uiState.update { it.copy(isRoamingPreviewLoading = false) }
                }
            }
        }
    }
