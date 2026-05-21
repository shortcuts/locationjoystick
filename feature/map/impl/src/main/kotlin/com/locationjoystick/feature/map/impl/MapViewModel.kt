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
import com.locationjoystick.core.location.MockLocationIntentBuilder
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.RoamingConfig
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.SpeedUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(MapUiState())
        val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

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
                routeRepository.getRoutes().collect { routes ->
                    _uiState.update { current -> current.copy(routes = routes) }
                }
            }
        }

        private fun observeFavorites() {
            viewModelScope.launch {
                favoriteRepository.getFavorites().collect { favorites ->
                    _uiState.update { current -> current.copy(favorites = favorites) }
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
                roamingRepository.isRoaming.collect { roaming ->
                    _uiState.update { it.copy(isRoaming = roaming) }
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
                    walkTo(action.position)
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
                            walkTarget = null,
                            walkStart = null,
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
                    _uiState.update {
                        it.copy(walkTarget = null, walkStart = null, isWalkPaused = false)
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
                    val draft =
                        RoamingDraft(
                            radiusMeters = latestRoamingDefaults.radiusMeters,
                            distanceMeters = latestRoamingDefaults.distanceMeters,
                            speedProfileId = latestRoamingDefaults.speedProfileId,
                            followRoads = latestRoamingDefaults.followRoads,
                            returnToInitialLocation = latestRoamingDefaults.returnToInitialLocation,
                        )
                    _uiState.update { it.copy(showRoamingSheet = true, roamingDraft = draft) }
                }

                MapAction.DismissRoamingSheet -> {
                    _uiState.update { it.copy(showRoamingSheet = false, roamingDraft = null) }
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
                        val config =
                            RoamingConfig(
                                centerPosition = position,
                                radiusMeters = draft.radiusMeters,
                                distanceMeters = draft.distanceMeters,
                                speedProfileId = draft.speedProfileId,
                                useRoadSnapping = draft.followRoads,
                                returnToInitialLocation = draft.returnToInitialLocation,
                            )
                        roamingRepository.startRoaming(config, speedMs)
                    }
                    _uiState.update { it.copy(showRoamingSheet = false, roamingDraft = null) }
                }

                MapAction.StopRoaming -> {
                    viewModelScope.launch {
                        roamingRepository.stopRoaming()
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
                    walkTarget = position,
                    walkStart = it.currentPosition,
                    routeTrace = null,
                )
            }
            walkCoordinator.startWalk(position, viewModelScope) { newPos, speedMs, bearing ->
                context.startService(
                    MockLocationIntentBuilder.updatePosition(context, newPos.latitude, newPos.longitude, speedMs, bearing),
                )
            }
        }

        private fun stopRouteOnly() {
            context.startService(MockLocationIntentBuilder.cancelRouteReplay(context))
        }

        private fun appendWaypointToRoute(position: LatLng) {
            context.startService(MockLocationIntentBuilder.appendWaypoint(context, position))
        }

        private fun startSpoofing() {
            val startPos =
                locationRepository.currentPosition.value
                    ?: LatLng(AppConstants.MapConstants.DEFAULT_LAT, AppConstants.MapConstants.DEFAULT_LON)
            ContextCompat.startForegroundService(
                context,
                MockLocationIntentBuilder.startSpoofing(context, startPos.latitude, startPos.longitude),
            )
        }

        private fun stopSpoofing() {
            ContextCompat.startForegroundService(context, MockLocationIntentBuilder.stopSpoofing(context))
        }
    }
