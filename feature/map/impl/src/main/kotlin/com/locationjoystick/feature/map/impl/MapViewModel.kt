package com.locationjoystick.feature.map.impl

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RoamingRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.data.TeleportUseCase
import com.locationjoystick.core.data.WalkCoordinator
import com.locationjoystick.core.datastore.PreferencesDataSource
import com.locationjoystick.core.location.EphemeralReplayController
import com.locationjoystick.core.location.MockLocationIntentBuilder
import com.locationjoystick.core.location.MockLocationService
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.RecentSearch
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.RouteReplayMode
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.core.model.toConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "MapViewModel"

/**
 * ViewModel for the Map screen.
 *
 * Manages:
 * - Spoofed position display and updates
 * - Route replay controls (start/pause/resume/stop)
 * - Walk-to-target movement
 * - Roaming mode controls
 * - Favorite location teleportation
 *
 * All state is exposed via [uiState] as [MapUiState].
 * UI actions trigger service commands via intents to [MockLocationService].
 *
 * Key flows:
 * - Location state observed from [LocationRepository]
 * - Routes observed from [RouteRepository]
 * - Favorites observed from [FavoriteRepository]
 * - Service commands sent via Intent actions (see [MockLocationService.ACTION_*])
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MapViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val locationRepository: LocationRepository,
        private val routeRepository: RouteRepository,
        private val favoriteRepository: FavoriteRepository,
        private val settingsRepository: SettingsRepository,
        private val roamingRepository: RoamingRepository,
        private val preferencesDataSource: PreferencesDataSource,
        private val walkCoordinator: WalkCoordinator,
        private val teleportUseCase: TeleportUseCase,
        private val ephemeralReplayController: EphemeralReplayController,
        private val osrmClient: com.locationjoystick.core.routing.OsrmClient,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(MapUiState())
        val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

        val recentSearches: StateFlow<List<RecentSearch>> =
            settingsRepository
                .getRecentSearches()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        private var latestRoamingDefaults: RoamingDefaults = RoamingDefaults()

        init {
            observeLocationState()
            observeMockMode()
            observeRoutes()
            observeFavorites()
            observeRouteWaypoints()
            observeRoaming()
            observeRoamingDefaults()
            observeSpeedUnit()
            observeCooldownForPendingTap()
            restoreLastLocationIfNeeded()
        }

        private fun observeLocationState() {
            viewModelScope.launch {
                combine(
                    locationRepository.currentPosition,
                    locationRepository.mockLocationState,
                    locationRepository.isWalkPaused,
                ) { position, state, walkPaused ->
                    Triple(position, state, walkPaused)
                }.collect { (position, state, walkPaused) ->
                    _uiState.update { current ->
                        current.copy(
                            currentPosition = position,
                            mockLocationState = state,
                            isWalkPaused = walkPaused,
                        )
                    }
                }
            }
        }

        private fun observeMockMode() {
            viewModelScope.launch {
                locationRepository.currentMode.collect { mode ->
                    _uiState.update { it.copy(isRouteReplay = mode == MockMode.ROUTE_REPLAY) }
                }
            }
        }

        private fun observeRoutes() {
            viewModelScope.launch {
                combine(
                    routeRepository.getRoutes(),
                    settingsRepository.getRoutesSortNewestFirst(),
                ) { routes, newestFirst ->
                    if (newestFirst) routes.sortedByDescending { it.createdAt } else routes.sortedBy { it.createdAt }
                }.collect { sorted ->
                    _uiState.update { current -> current.copy(routes = sorted) }
                }
            }
        }

        private fun observeFavorites() {
            viewModelScope.launch {
                combine(
                    favoriteRepository.getFavorites(),
                    settingsRepository.getFavoritesSortNewestFirst(),
                ) { favorites, newestFirst ->
                    if (newestFirst) favorites.sortedByDescending { it.createdAt } else favorites.sortedBy { it.createdAt }
                }.collect { sorted ->
                    _uiState.update { current -> current.copy(favorites = sorted) }
                }
            }
        }

        private fun observeRouteWaypoints() {
            viewModelScope.launch {
                locationRepository.routeWaypoints.collect { waypoints ->
                    _uiState.update { current -> current.copy(routeTrace = waypoints) }
                }
            }
        }

        private fun observeRoaming() {
            viewModelScope.launch {
                combine(
                    roamingRepository.isRoaming,
                    roamingRepository.isRoamingPaused,
                ) { roaming, paused -> roaming to paused }
                    .collect { (roaming, paused) ->
                        _uiState.update { it.copy(isRoaming = roaming, isRoamingPaused = paused) }
                    }
            }
        }

        private fun observeRoamingDefaults() {
            viewModelScope.launch {
                preferencesDataSource.getRoamingDefaults().collect { defaults ->
                    latestRoamingDefaults = defaults
                }
            }
        }

        private fun observeSpeedUnit() {
            viewModelScope.launch {
                settingsRepository.getSpeedUnit().collect { unit ->
                    _uiState.update { it.copy(speedUnit = unit) }
                }
            }
        }

        private fun observeCooldownForPendingTap() {
            viewModelScope.launch {
                _uiState
                    .flatMapLatest { state ->
                        val target = state.pendingTapPosition
                        if (target !=
                            null
                        ) {
                            teleportUseCase.cooldownFor(target)
                        } else {
                            flowOf(com.locationjoystick.core.data.CooldownState.Ready)
                        }
                    }.collect { cooldown ->
                        _uiState.update { it.copy(cooldownState = cooldown) }
                    }
            }
        }

        fun onAction(action: MapAction) {
            when (action) {
                is MapAction.TapToTeleport -> {
                    _uiState.update { it.copy(roamingPreviewWaypoints = null) }
                    if (_uiState.value.isSpoofing) {
                        _uiState.update { it.copy(pendingTapPosition = action.position) }
                    } else {
                        teleportTo(action.position)
                    }
                }

                is MapAction.ConfirmTeleport -> {
                    teleportTo(action.position)
                    _uiState.update { it.copy(pendingTapPosition = null) }
                }

                is MapAction.ClearPendingTap -> {
                    _uiState.update { it.copy(pendingTapPosition = null) }
                }

                is MapAction.LongPressTapToWalk -> {
                    _uiState.update { it.copy(roamingPreviewWaypoints = null) }
                    walkTo(action.position)
                }

                is MapAction.WalkViaRoadsTo -> {
                    _uiState.update { it.copy(roamingPreviewWaypoints = null) }
                    walkViaRoads(action.position)
                }

                MapAction.StartSpoofing -> {
                    startSpoofing()
                }

                MapAction.StopSpoofing -> {
                    stopSpoofing()
                    _uiState.update {
                        it.copy(
                            pendingTapPosition = null,
                            routeTrace = null,
                            walkMode = WalkMode.Idle,
                        )
                    }
                }

                MapAction.RecenterCamera -> {
                    _uiState.update { it.copy(isUserPanning = false) }
                }

                MapAction.UserStartedPanning -> {
                    _uiState.update { it.copy(isUserPanning = true) }
                }

                MapAction.OpenFavoritesPicker -> {
                    _uiState.update { it.copy(showFavoritesSheet = true) }
                }

                MapAction.CloseFavoritesPicker -> {
                    _uiState.update { it.copy(showFavoritesSheet = false, favoriteTarget = null) }
                }

                MapAction.DeselectFavorite -> {
                    _uiState.update { it.copy(favoriteTarget = null) }
                }

                MapAction.CameraTargetConsumed -> {
                    _uiState.update { it.copy(pendingCameraTarget = null) }
                }

                is MapAction.SelectFavorite -> {
                    if (_uiState.value.isSpoofing) {
                        _uiState.update {
                            it.copy(
                                favoriteTarget = action.favorite,
                                pendingCameraTarget = action.favorite.position,
                            )
                        }
                    } else {
                        teleportTo(action.favorite.position)
                        _uiState.update { it.copy(showFavoritesSheet = false) }
                    }
                }

                is MapAction.SetLocationTo -> {
                    teleportTo(action.position)
                    _uiState.update {
                        it.copy(showFavoritesSheet = false, favoriteTarget = null)
                    }
                }

                is MapAction.WalkStraightTo -> {
                    walkTo(action.position)
                    _uiState.update {
                        it.copy(showFavoritesSheet = false, favoriteTarget = null)
                    }
                }

                MapAction.PauseWalk -> {
                    locationRepository.setWalkPaused(true)
                }

                MapAction.ResumeWalk -> {
                    locationRepository.setWalkPaused(false)
                }

                MapAction.StopWalk -> {
                    walkCoordinator.cancel()
                    if (_uiState.value.ephemeralWaypoints.isNotEmpty()) {
                        context.startService(MockLocationIntentBuilder.cancelRouteReplay(context))
                    }
                    _uiState.update {
                        it.copy(walkMode = WalkMode.Idle, isWalkPaused = false)
                    }
                }

                is MapAction.StopRouteAndTeleport -> {
                    stopRouteOnly()
                    teleportTo(action.position)
                    _uiState.update { it.copy(pendingTapPosition = null) }
                }

                is MapAction.StopRouteAndWalkTo -> {
                    stopRouteOnly()
                    walkTo(action.position)
                    _uiState.update { it.copy(pendingTapPosition = null) }
                }

                is MapAction.FinishRouteAndWalkTo -> {
                    appendWaypointToRoute(action.position)
                    _uiState.update { it.copy(pendingTapPosition = null) }
                }

                is MapAction.SaveCurrentLocation -> {
                    val position = _uiState.value.currentPosition ?: return
                    viewModelScope.launch {
                        try {
                            favoriteRepository.addFavorite(
                                id =
                                    java.util.UUID
                                        .randomUUID()
                                        .toString(),
                                name = action.name,
                                position = position,
                                createdAt = System.currentTimeMillis(),
                            )
                            Log.d(TAG, "Saved current location as '${action.name}'")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to save current location", e)
                        }
                    }
                }

                MapAction.OpenRoamingSheet -> {
                    _uiState.update {
                        it.copy(
                            showRoamingSheet = true,
                            roamingDraft = latestRoamingDefaults,
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
                    val draft = _uiState.value.roamingDraft ?: return
                    val center = _uiState.value.currentPosition ?: return
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

                MapAction.MinimizeRoamingSheet -> {
                    _uiState.update { it.copy(isRoamingSheetMinimized = true) }
                }

                MapAction.ExpandRoamingSheet -> {
                    _uiState.update { it.copy(isRoamingSheetMinimized = false) }
                }

                MapAction.ClearMap -> {
                    walkCoordinator.cancel()
                    if (_uiState.value.ephemeralWaypoints.isNotEmpty()) {
                        context.startService(MockLocationIntentBuilder.cancelRouteReplay(context))
                    }
                    _uiState.update {
                        it.copy(
                            pendingTapPosition = null,
                            walkMode = WalkMode.Idle,
                            isWalkPaused = false,
                            roamingPreviewWaypoints = null,
                            showRoamingSheet = false,
                            roamingDraft = null,
                            isRoamingSheetMinimized = false,
                        )
                    }
                }

                is MapAction.UpdateRoamingRadius -> {
                    _uiState.update { s ->
                        s.copy(roamingDraft = s.roamingDraft?.copy(radiusMeters = action.meters))
                    }
                }

                is MapAction.UpdateRoamingDistance -> {
                    _uiState.update { s ->
                        s.copy(roamingDraft = s.roamingDraft?.copy(distanceMeters = action.meters))
                    }
                }

                is MapAction.SelectRoamingSpeedProfile -> {
                    _uiState.update { s ->
                        s.copy(roamingDraft = s.roamingDraft?.copy(speedProfileId = action.id))
                    }
                }

                is MapAction.ToggleRoamingFollowRoads -> {
                    _uiState.update { s ->
                        s.copy(roamingDraft = s.roamingDraft?.copy(followRoads = action.enabled))
                    }
                }

                is MapAction.ToggleRoamingReturnToStart -> {
                    _uiState.update { s ->
                        s.copy(roamingDraft = s.roamingDraft?.copy(returnToInitialLocation = action.enabled))
                    }
                }

                MapAction.StartRoaming -> {
                    val draft = _uiState.value.roamingDraft ?: return
                    val position = _uiState.value.currentPosition
                    if (position == null) {
                        Log.w(TAG, "Cannot start roaming: no current position")
                        return
                    }
                    viewModelScope.launch {
                        val speedMs =
                            settingsRepository
                                .getSpeedProfiles()
                                .first()
                                .firstOrNull { it.id == draft.speedProfileId }
                                ?.speedMetersPerSecond
                                ?: settingsRepository.getActiveSpeedProfile().first().speedMetersPerSecond
                        val previewWaypoints = _uiState.value.roamingPreviewWaypoints
                        val config = draft.toConfig(position).copy(previewWaypoints = previewWaypoints)
                        roamingRepository.startRoaming(config, speedMs)
                    }
                    _uiState.update { it.copy(showRoamingSheet = false, roamingDraft = null, isRoamingSheetMinimized = false) }
                }

                MapAction.StopRoaming -> {
                    viewModelScope.launch {
                        roamingRepository.stopRoaming()
                    }
                    _uiState.update { it.copy(isRoamingControlsExpanded = false) }
                }

                MapAction.PauseRoaming -> {
                    roamingRepository.pauseRoaming()
                }

                MapAction.ResumeRoaming -> {
                    roamingRepository.resumeRoaming()
                }

                is MapAction.AddEphemeralWaypoint -> {
                    val state = _uiState.value
                    viewModelScope.launch {
                        val result =
                            ephemeralReplayController.addWaypoint(
                                newPoint = action.position,
                                currentWaypoints = state.ephemeralWaypoints,
                                walkStart = state.walkStart,
                                walkTarget = state.walkTarget,
                                context = context,
                                launchIntent = { context.startService(it) },
                            ) ?: return@launch
                        _uiState.update {
                            it.copy(
                                walkMode = WalkMode.EphemeralReplay(result),
                                pendingTapPosition = null,
                            )
                        }
                    }
                }

                MapAction.OpenRoutesSheet -> {
                    _uiState.update { it.copy(showRoutesSheet = true) }
                }

                MapAction.CloseRoutesSheet -> {
                    _uiState.update { it.copy(showRoutesSheet = false) }
                }

                is MapAction.StartRouteReplay -> {
                    startRouteReplayWithMode(action.routeId, action.mode)
                    _uiState.update { it.copy(showRoutesSheet = false) }
                }

                MapAction.PauseRouteReplay -> {
                    context.startService(MockLocationIntentBuilder.pauseRouteReplay(context))
                }

                MapAction.ResumeRouteReplay -> {
                    viewModelScope.launch {
                        val speedMs = settingsRepository.getActiveSpeedProfile().first().speedMetersPerSecond
                        context.startService(MockLocationIntentBuilder.resumeRouteReplay(context, speedMs))
                    }
                }

                MapAction.StopRouteReplay -> {
                    context.startService(MockLocationIntentBuilder.stopRouteReplay(context))
                    _uiState.update { it.copy(isRouteControlsExpanded = false) }
                }

                MapAction.ToggleRouteControls -> {
                    _uiState.update { it.copy(isRouteControlsExpanded = !it.isRouteControlsExpanded) }
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
            viewModelScope.launch { settingsRepository.addRecentSearch(displayName, lat, lon) }
        }

        private fun restoreLastLocationIfNeeded() {
            viewModelScope.launch {
                if (locationRepository.currentPosition.value == null) {
                    val remember = settingsRepository.getRememberLastLocation().first()
                    if (remember) {
                        val last = settingsRepository.getLastLocation().first()
                        if (last != null) {
                            locationRepository.setPositionInternal(last)
                        }
                    }
                }
            }
        }

        private fun teleportTo(position: LatLng) {
            viewModelScope.launch {
                teleportUseCase.execute(position)
            }
        }

        private fun walkTo(position: LatLng) {
            _uiState.update {
                it.copy(
                    walkMode = WalkMode.Walking(target = position, start = it.currentPosition),
                    routeTrace = null,
                )
            }
            walkCoordinator.startWalk(position, viewModelScope) { newPos, speedMs, bearing ->
                context.startService(
                    MockLocationIntentBuilder.updatePosition(context, newPos.latitude, newPos.longitude, speedMs, bearing),
                )
            }
        }

        private fun walkViaRoads(position: LatLng) {
            viewModelScope.launch {
                val current = locationRepository.currentPosition.value
                if (current == null) {
                    walkTo(position)
                    return@launch
                }
                val waypoints =
                    osrmClient
                        .getRoute(com.locationjoystick.core.routing.OsrmClient.PROFILE_FOOT, listOf(current, position))
                        .getOrNull()
                if (waypoints.isNullOrEmpty()) {
                    Log.w(TAG, "OSRM road-following failed; falling back to straight walk")
                    walkTo(position)
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        walkMode = WalkMode.Walking(target = position, start = it.currentPosition),
                        routeTrace = null,
                    )
                }
                walkCoordinator.startWalkAlongRoute(waypoints, viewModelScope) { newPos, speedMs, bearing ->
                    context.startService(
                        MockLocationIntentBuilder.updatePosition(context, newPos.latitude, newPos.longitude, speedMs, bearing),
                    )
                }
            }
        }

        private fun startRouteReplayWithMode(
            routeId: String,
            mode: RouteReplayMode,
        ) {
            viewModelScope.launch {
                val speedMs = settingsRepository.getActiveSpeedProfile().first().speedMetersPerSecond
                val isBackward = mode == RouteReplayMode.LOOP_REVERSE
                val isLooping = mode == RouteReplayMode.LOOP || mode == RouteReplayMode.LOOP_REVERSE
                val returnPosition =
                    if (mode == RouteReplayMode.RETURN_TO_LOCATION) {
                        locationRepository.currentPosition.value
                    } else {
                        null
                    }
                val intent =
                    MockLocationIntentBuilder
                        .startRouteReplay(context, routeId, speedMs, isBackward)
                        .apply {
                            putExtra(MockLocationService.EXTRA_IS_LOOPING, isLooping)
                            if (returnPosition != null) {
                                putExtra(MockLocationService.EXTRA_RETURN_LAT, returnPosition.latitude)
                                putExtra(MockLocationService.EXTRA_RETURN_LON, returnPosition.longitude)
                            }
                        }
                context.startService(intent)
            }
        }

        private fun stopRouteOnly() {
            context.startService(MockLocationIntentBuilder.cancelRouteReplay(context))
        }

        private fun appendWaypointToRoute(position: LatLng) {
            context.startService(MockLocationIntentBuilder.appendWaypoint(context, position))
        }

        private fun startSpoofing() {
            viewModelScope.launch {
                val startPos =
                    locationRepository.currentPosition.value
                        ?: settingsRepository.getLastLocation().first()
                        ?: LatLng(AppConstants.MapConstants.DEFAULT_LAT, AppConstants.MapConstants.DEFAULT_LON)
                ContextCompat.startForegroundService(
                    context,
                    MockLocationIntentBuilder.startSpoofing(context, startPos.latitude, startPos.longitude),
                )
            }
        }

        private fun stopSpoofing() {
            ContextCompat.startForegroundService(context, MockLocationIntentBuilder.stopSpoofing(context))
        }
    }
