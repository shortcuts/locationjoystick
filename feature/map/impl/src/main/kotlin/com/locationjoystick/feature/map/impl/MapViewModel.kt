package com.locationjoystick.feature.map.impl

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.common.util.formatCapturedPointsForClipboard
import com.locationjoystick.core.common.util.parsePastedCoordinates
import com.locationjoystick.core.data.CaptureCoordinatesRepository
import com.locationjoystick.core.data.CooldownState
import com.locationjoystick.core.data.DeepLinkRepository
import com.locationjoystick.core.data.GpxOpenRepository
import com.locationjoystick.core.data.LaunchAfterLinkUseCase
import com.locationjoystick.core.data.RoamingRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.data.TeleportUseCase
import com.locationjoystick.core.data.advisoryPair
import com.locationjoystick.core.location.MapController
import com.locationjoystick.core.location.isRouteReplay
import com.locationjoystick.core.location.nonPositionKey
import com.locationjoystick.core.model.CompletionKind
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RecentSearch
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.RouteStartConfig
import com.locationjoystick.core.model.toConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
        private val launchAfterLinkUseCase: LaunchAfterLinkUseCase,
    ) : ViewModel() {
        /** Pin that came from an intercepted/deep link; confirming it opens the user's chosen app. */
        private var linkPinnedTarget: LatLng? = null

        private val _uiState = MutableStateFlow(MapUiState())
        val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

        private fun moveCameraTo(position: LatLng) {
            _uiState.update {
                it.copy(
                    isUserPanning = true,
                    pendingCameraTarget = position,
                    pendingTapPosition = null,
                )
            }
            sheet(SheetEvent.ClosePendingTap)
        }

        private fun sheet(event: SheetEvent) {
            _uiState.update { s ->
                s.activeSheet.next(event)?.let { s.copy(activeSheet = it) }
                    ?: s.also { Log.w(TAG, "ignored sheet event $event from ${s.activeSheet}") }
            }
        }

        // Shared state flows through as separate StateFlows to preserve MapScreen API surface
        val recentSearches: StateFlow<List<RecentSearch>> =
            mapController.sharedState
                .map { it.recentSearches }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        val roamingDefaults: StateFlow<RoamingDefaults> =
            mapController.sharedState
                .map { it.roamingDefaults }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoamingDefaults())

        val completionMessages: SharedFlow<CompletionKind> = mapController.completionMessages
        val routingErrors: SharedFlow<String> = mapController.routingErrors

        init {
            mapController.restoreLastLocationIfNeeded()
            observeSharedState()
            observeCooldownForPendingTap()
            observeCooldownEnd()
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
                settingsRepository.getRouteStartConfig().distinctUntilChanged().collect { last ->
                    _uiState.update { it.copy(lastRouteStartConfig = last) }
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
            viewModelScope.launch {
                settingsRepository
                    .getMapTileSource()
                    .distinctUntilChanged()
                    .collect { source -> _uiState.update { it.copy(mapTileSource = source) } }
            }
        }

        private fun observeSharedState() {
            // Separate from sharedState: speed ticks would re-emit its distinct-key flow.
            viewModelScope.launch {
                mapController.currentSpeedMps.collect { s -> _uiState.update { it.copy(currentSpeedMps = s) } }
            }
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
                                movement = shared.movement,
                                isRouteReplay = shared.isRouteReplay,
                                routes = shared.routes,
                                favorites = shared.favorites,
                                favoriteCooldownStates = shared.favoriteCooldownStates,
                                homeFavoriteId = shared.homeFavoriteId,
                                routeTrace = shared.routeTrace,
                                walkMode = shared.walkMode,
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

        private fun observeCooldownEnd() {
            viewModelScope.launch {
                settingsRepository.getLastCooldownEndTime().collect { end -> _uiState.update { it.copy(cooldownEndMs = end) } }
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
                    pinCoordinateTarget(coords, fromLink = true)
                    deepLinkRepository.consume()
                }
            }
        }

        private fun observeGpxOpen() {
            viewModelScope.launch {
                gpxOpenRepository.pending.collect { pending ->
                    showPasteSheetWith(PasteSheetTitle.GPX, pending.points, pending.suggestedName)
                }
            }
        }

        private fun showPasteSheetWith(
            title: PasteSheetTitle,
            points: List<LatLng>,
            routeName: String,
        ) {
            sheet(SheetEvent.OpenPaste(title, formatCapturedPointsForClipboard(points), routeName))
            _uiState.update { it.copy(pasteFormNonce = it.pasteFormNonce + 1) }
        }

        private fun hidePasteCoordinatesSheet() {
            gpxOpenRepository.consume()
            sheet(SheetEvent.ClosePaste)
        }

        fun onAction(action: MapAction) {
            when (action) {
                // Teleport
                is MapAction.TapToTeleport -> {
                    handleTapToTeleport(action.position)
                }

                is MapAction.ConfirmTeleport -> {
                    mapController.teleportTo(action.position)
                    launchAfterLinkIfPinned(action.position)
                    _uiState.update { it.copy(pendingTapPosition = null) }
                    sheet(SheetEvent.ClosePendingTap)
                }

                is MapAction.ClearPendingTap -> {
                    sheet(SheetEvent.ClosePendingTap)
                }

                MapAction.ClearPinnedPoint -> {
                    linkPinnedTarget = null
                    _uiState.update { it.copy(pendingTapPosition = null) }
                    sheet(SheetEvent.ClosePendingTap)
                }

                is MapAction.StopRouteAndTeleport -> {
                    mapController.stopRouteOnly()
                    mapController.teleportTo(action.position)
                    launchAfterLinkIfPinned(action.position)
                    _uiState.update { it.copy(pendingTapPosition = null) }
                    sheet(SheetEvent.ClosePendingTap)
                }

                is MapAction.SetLocationTo -> {
                    mapController.teleportTo(action.position)
                    sheet(SheetEvent.CloseFavorites)
                }

                // Walk
                is MapAction.LongPressTapToWalk -> {
                    _uiState.update { it.copy(roamingPreviewWaypoints = null) }
                    mapController.walkTo(action.position)
                    launchAfterLinkIfPinned(action.position)
                }

                is MapAction.WalkViaRoadsTo -> {
                    _uiState.update { it.copy(roamingPreviewWaypoints = null) }
                    mapController.walkViaRoads(action.position)
                    launchAfterLinkIfPinned(action.position)
                }

                is MapAction.WalkStraightTo -> {
                    mapController.walkTo(action.position)
                    sheet(SheetEvent.CloseFavorites)
                }

                is MapAction.StopRouteAndWalkTo -> {
                    mapController.stopRouteOnly()
                    mapController.walkTo(action.position)
                    launchAfterLinkIfPinned(action.position)
                    _uiState.update { it.copy(pendingTapPosition = null) }
                    sheet(SheetEvent.ClosePendingTap)
                }

                is MapAction.FinishRouteAndWalkTo -> {
                    mapController.appendWaypointToRoute(action.position)
                    launchAfterLinkIfPinned(action.position)
                    _uiState.update { it.copy(pendingTapPosition = null) }
                    sheet(SheetEvent.ClosePendingTap)
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
                            isWalkControlsExpanded = false,
                            roamingPreviewWaypoints = null,
                        )
                    }
                    sheet(SheetEvent.ClosePendingTap)
                    sheet(SheetEvent.CloseRoaming)
                    hidePasteCoordinatesSheet()
                }

                // Camera
                is MapAction.RecenterCamera -> {
                    val state = _uiState.value.mockLocationState
                    val target = _uiState.value.currentPosition ?: action.fallbackPosition
                    when {
                        state != com.locationjoystick.core.model.MockLocationState.IDLE &&
                            state != com.locationjoystick.core.model.MockLocationState.ERROR ->
                            {
                                _uiState.update {
                                    it.copy(
                                        isUserPanning = false,
                                        pendingCameraTarget = target,
                                        pendingTapPosition = null,
                                    )
                                }
                                sheet(SheetEvent.ClosePendingTap)
                            }

                        // Idle: jump to the marker synchronously. A GPS query here made the button lag and stack.
                        target != null -> moveCameraTo(target)
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
                    sheet(SheetEvent.OpenFavorites)
                }

                MapAction.CloseFavoritesPicker -> {
                    sheet(SheetEvent.CloseFavorites)
                }

                MapAction.DeselectFavorite -> {
                    sheet(SheetEvent.DeselectFavorite)
                }

                is MapAction.SelectFavorite -> {
                    handleSelectFavorite(action.favorite)
                }

                is MapAction.ToggleHomeFavorite -> {
                    mapController.toggleHomeFavorite(action.favorite.id)
                }

                is MapAction.SaveCurrentLocation -> {
                    mapController.saveCurrentLocation(action.name)
                }

                // Routes
                MapAction.OpenRoutesSheet -> {
                    sheet(SheetEvent.OpenRoutes)
                }

                MapAction.CloseRoutesSheet -> {
                    sheet(SheetEvent.CloseRoutes)
                }

                is MapAction.StartRouteReplay -> {
                    viewModelScope.launch { settingsRepository.setRouteStartConfig(action.config) }
                    mapController.startRouteReplay(action.routeId, action.config)
                    if (!action.config.followRoadsToStart) {
                        sheet(SheetEvent.CloseRoutes)
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
                    sheet(SheetEvent.OpenRoaming(mapController.sharedState.value.roamingDefaults))
                    _uiState.update { it.copy(roamingPreviewWaypoints = null) }
                }

                MapAction.DismissRoamingSheet -> {
                    sheet(SheetEvent.CloseRoaming)
                    _uiState.update { it.copy(roamingPreviewWaypoints = null) }
                }

                MapAction.GenerateRoamingPreview -> {
                    generateRoamingPreview()
                }

                MapAction.MinimizeRoamingSheet -> {
                    sheet(SheetEvent.MinimizeRoaming)
                }

                MapAction.ExpandRoamingSheet -> {
                    sheet(SheetEvent.ExpandRoaming)
                }

                is MapAction.UpdateRoamingRadius -> {
                    sheet(SheetEvent.UpdateRoamingDraft { it.copy(radiusMeters = action.meters) })
                }

                is MapAction.UpdateRoamingDistance -> {
                    sheet(SheetEvent.UpdateRoamingDraft { it.copy(distanceMeters = action.meters) })
                }

                is MapAction.SelectRoamingSpeedProfile -> {
                    sheet(SheetEvent.UpdateRoamingDraft { it.copy(speedProfileId = action.id) })
                }

                is MapAction.SelectPlantingSpeedProfile -> {
                    sheet(SheetEvent.UpdateRoamingDraft { it.copy(plantingSpeedProfileId = action.id) })
                }

                is MapAction.ToggleRoamingFollowRoads -> {
                    sheet(SheetEvent.UpdateRoamingDraft { it.copy(followRoads = action.enabled) })
                }

                is MapAction.ToggleRoamingReturnToStart -> {
                    sheet(SheetEvent.UpdateRoamingDraft { it.copy(returnToInitialLocation = action.enabled) })
                }

                is MapAction.UpdateRoamingKind -> {
                    val sameKind = _uiState.value.roamingDraft?.kind == action.kind
                    sheet(SheetEvent.UpdateRoamingDraft { it.copy(kind = action.kind) })
                    if (!sameKind) _uiState.update { it.copy(roamingPreviewWaypoints = null) }
                }

                is MapAction.UpdatePlantingStartRadius -> {
                    sheet(SheetEvent.UpdateRoamingDraft { it.copy(plantingStartRadiusMeters = action.meters) })
                    _uiState.update { it.copy(roamingPreviewWaypoints = null) }
                }

                is MapAction.UpdatePlantingEndRadius -> {
                    sheet(SheetEvent.UpdateRoamingDraft { it.copy(plantingEndRadiusMeters = action.meters) })
                    _uiState.update { it.copy(roamingPreviewWaypoints = null) }
                }

                is MapAction.TogglePlantingInfiniteLoops -> {
                    sheet(SheetEvent.UpdateRoamingDraft { it.copy(plantingInfiniteLoops = action.enabled) })
                }

                is MapAction.TogglePlantingExpandOnly -> {
                    sheet(SheetEvent.UpdateRoamingDraft { it.copy(plantingExpandOnly = action.enabled) })
                    _uiState.update { it.copy(roamingPreviewWaypoints = null) }
                }

                is MapAction.UpdatePlantingLoopCount -> {
                    sheet(SheetEvent.UpdateRoamingDraft { it.copy(plantingLoopCount = action.count) })
                }

                is MapAction.UpdatePlantingSpacing -> {
                    sheet(SheetEvent.UpdateRoamingDraft { it.copy(plantingSpacingMeters = action.meters) })
                    _uiState.update { it.copy(roamingPreviewWaypoints = null) }
                }

                MapAction.StartRoaming -> {
                    val draft = _uiState.value.roamingDraft ?: return
                    val position = mapController.sharedState.value.currentPosition ?: return
                    mapController.startRoaming(draft, position, _uiState.value.roamingPreviewWaypoints)
                    sheet(SheetEvent.CloseRoaming)
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
                    sheet(SheetEvent.OpenPaste(PasteSheetTitle.DEFAULT, "", ""))
                    _uiState.update { it.copy(pasteFormNonce = it.pasteFormNonce + 1) }
                }

                MapAction.ToggleDrawLine -> {
                    _uiState.update { it.copy(isDrawLineActive = !it.isDrawLineActive) }
                }

                is MapAction.DrawLineFinished -> {
                    _uiState.update { it.copy(isDrawLineActive = false) }
                    showPasteSheetWith(PasteSheetTitle.DEFAULT, action.points, "")
                }

                MapAction.ClosePasteCoordinates -> {
                    hidePasteCoordinatesSheet()
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

        fun cooldownFor(position: LatLng): Flow<Pair<Long, Double>?> = teleportUseCase.cooldownFor(position).map { it.advisoryPair() }

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
            config: RouteStartConfig,
        ) {
            if (points.size < 2) return
            mapController.startPastedRouteReplay(points = points, config = config)
            hidePasteCoordinatesSheet()
        }

        private fun pinCoordinateTarget(
            coords: LatLng,
            fromLink: Boolean = false,
        ) {
            gpxOpenRepository.consume()
            linkPinnedTarget = coords.takeIf { fromLink }
            _uiState.update {
                it.copy(
                    pendingTapPosition = coords,
                    pendingCameraTarget = coords,
                    roamingPreviewWaypoints = null,
                )
            }
            sheet(SheetEvent.OpenPendingTap)
        }

        private fun launchAfterLinkIfPinned(position: LatLng) {
            if (position != linkPinnedTarget) return
            linkPinnedTarget = null
            viewModelScope.launch { launchAfterLinkUseCase.launch() }
        }

        private fun handleTapToTeleport(position: LatLng) {
            _uiState.update { it.copy(roamingPreviewWaypoints = null) }
            if (mapController.isSpoofingLocally()) {
                _uiState.update { it.copy(pendingTapPosition = position) }
                sheet(SheetEvent.OpenPendingTap)
            } else {
                mapController.teleportTo(position)
            }
        }

        private fun handleSelectFavorite(favorite: FavoriteLocation) {
            if (mapController.isSpoofingLocally()) {
                sheet(SheetEvent.SelectFavorite(favorite))
                _uiState.update { it.copy(pendingCameraTarget = favorite.position) }
            } else {
                mapController.teleportTo(favorite.position)
                sheet(SheetEvent.CloseFavorites)
                _uiState.update { it.copy(pendingCameraTarget = favorite.position) }
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
