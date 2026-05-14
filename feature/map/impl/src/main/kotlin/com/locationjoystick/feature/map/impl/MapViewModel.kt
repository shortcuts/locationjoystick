package com.locationjoystick.feature.map.impl

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.common.util.advancePosition
import com.locationjoystick.core.common.util.calculateBearing
import com.locationjoystick.core.common.util.haversineDistance
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.location.MockLocationService
import com.locationjoystick.core.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "MapViewModel"
private const val ACTION_UPDATE_POSITION = "com.locationjoystick.core.location.ACTION_UPDATE_POSITION"

@HiltViewModel
class MapViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val locationRepository: LocationRepository,
        private val routeRepository: RouteRepository,
        private val favoriteRepository: FavoriteRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(MapUiState())
        val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

        private var walkToJob: Job? = null

        init {
            observeLocationState()
            observeRoutes()
            observeFavorites()
        }

        private fun observeLocationState() {
            viewModelScope.launch {
                combine(
                    locationRepository.observePosition(),
                    locationRepository.observeState(),
                ) { position, state ->
                    position to state
                }.collect { (position, state) ->
                    _uiState.update { current ->
                        current.copy(
                            currentPosition = position,
                            mockLocationState = state,
                        )
                    }
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
                    _uiState.update { it.copy(pendingTapPosition = null) }
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
            }
        }

        private fun teleportTo(position: LatLng) {
            viewModelScope.launch {
                try {
                    val intent =
                        Intent(ACTION_UPDATE_POSITION).apply {
                            component =
                                ComponentName(
                                    context,
                                    MockLocationService::class.java,
                                )
                            putExtra("lat", position.latitude)
                            putExtra("lon", position.longitude)
                        }
                    context.startService(intent)
                    Log.d(TAG, "Teleport to ${position.latitude}, ${position.longitude}")
                } catch (e: Exception) {
                    Log.e(TAG, "Teleport failed", e)
                }
            }
        }

        private fun walkTo(position: LatLng) {
            walkToJob?.cancel()
            locationRepository.setWalkTarget(position)
            walkToJob =
                viewModelScope.launch {
                    try {
                        val targetLat = position.latitude
                        val targetLon = position.longitude
                        val speedMs = 1.39

                        while (true) {
                            val current = _uiState.value.currentPosition
                            if (current == null) {
                                Log.w(TAG, "No current position; stopping walk")
                                break
                            }

                            val distanceM =
                                haversineDistance(
                                    current.latitude,
                                    current.longitude,
                                    targetLat,
                                    targetLon,
                                )
                            if (distanceM < 1.0) {
                                Log.d(TAG, "Reached target; stopping walk")
                                break
                            }

                            val bearing =
                                calculateBearing(
                                    current.latitude,
                                    current.longitude,
                                    targetLat,
                                    targetLon,
                                )
                            val advanceDistance = minOf(speedMs * 1.0, distanceM)
                            val newPos =
                                advancePosition(
                                    current.latitude,
                                    current.longitude,
                                    bearing,
                                    advanceDistance,
                                )

                            withContext(Dispatchers.Main) {
                                try {
                                    val intent =
                                        Intent(ACTION_UPDATE_POSITION).apply {
                                            component =
                                                ComponentName(
                                                    context,
                                                    MockLocationService::class.java,
                                                )
                                            putExtra("lat", newPos.first)
                                            putExtra("lon", newPos.second)
                                        }
                                    context.startService(intent)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Walk update failed", e)
                                }
                            }

                            delay(1000)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Walk interrupted", e)
                    } finally {
                        locationRepository.setWalkTarget(null)
                    }
                }
        }

        private fun startSpoofing() {
            val startPos = locationRepository.currentPosition.value ?: LatLng(48.8566, 2.3522)
            val intent =
                Intent(MockLocationService.ACTION_START).apply {
                    setClassName(context, "com.locationjoystick.core.location.MockLocationService")
                    putExtra("lat", startPos.latitude)
                    putExtra("lon", startPos.longitude)
                }
            ContextCompat.startForegroundService(context, intent)
        }

        private fun stopSpoofing() {
            viewModelScope.launch {
                locationRepository.stopSpoofing()
            }
        }
    }
