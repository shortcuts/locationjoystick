package com.locationjoystick.feature.map.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val routeRepository: RouteRepository,
    private val favoriteRepository: FavoriteRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

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
            is MapAction.TapToTeleport -> teleportTo(action.position)
            is MapAction.LongPressTapToWalk -> walkTo(action.position)
            MapAction.StartSpoofing -> startSpoofing()
            MapAction.StopSpoofing -> stopSpoofing()
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
        }
    }

    private fun teleportTo(position: LatLng) {
        viewModelScope.launch {
            locationRepository.updatePosition(position)
        }
    }

    private fun walkTo(position: LatLng) {
        viewModelScope.launch {
            locationRepository.updatePosition(position)
        }
    }

    private fun startSpoofing() {
        viewModelScope.launch {
            locationRepository.startSpoofing()
        }
    }

    private fun stopSpoofing() {
        viewModelScope.launch {
            locationRepository.stopSpoofing()
        }
    }
}
