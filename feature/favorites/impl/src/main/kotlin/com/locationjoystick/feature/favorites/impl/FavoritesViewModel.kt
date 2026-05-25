package com.locationjoystick.feature.favorites.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.data.CooldownEngine
import com.locationjoystick.core.data.CooldownState
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.data.TeleportUseCase
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.RecentSearch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel
    @Inject
    constructor(
        private val favoriteRepository: FavoriteRepository,
        private val locationRepository: LocationRepository,
        private val settingsRepository: SettingsRepository,
        private val teleportUseCase: TeleportUseCase,
    ) : ViewModel() {
        private val pendingDeleteIdFlow = MutableStateFlow<String?>(null)

        val uiState: StateFlow<FavoritesUiState> =
            combine(
                favoriteRepository.getFavorites(),
                pendingDeleteIdFlow,
                settingsRepository.getFavoritesSortNewestFirst(),
            ) { favorites, pendingDeleteId, sortNewestFirst ->
                val sorted =
                    if (sortNewestFirst) {
                        favorites.sortedByDescending { it.createdAt }
                    } else {
                        favorites.sortedBy { it.createdAt }
                    }
                FavoritesUiState(
                    favorites = sorted,
                    isLoading = false,
                    pendingDeleteId = pendingDeleteId,
                    sortNewestFirst = sortNewestFirst,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = FavoritesUiState(isLoading = true),
            )

        val recentSearches: StateFlow<List<RecentSearch>> =
            settingsRepository
                .getRecentSearches()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        val currentPosition: com.locationjoystick.core.model.LatLng?
            get() = locationRepository.currentPosition.value

        /** Cooldown states keyed by favorite ID, refreshed every 30 seconds. */
        val cooldownStates: StateFlow<Map<String, CooldownState>> =
            combine(
                settingsRepository.getLastTeleportTime(),
                settingsRepository.getLastLocation(),
                tickerFlow(30_000L),
            ) { teleportTime, lastLoc, _ ->
                uiState.value.favorites.associate { fav ->
                    fav.id to CooldownEngine.computeState(teleportTime, lastLoc, fav.position)
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap(),
            )

        fun toggleSort() {
            viewModelScope.launch {
                settingsRepository.setFavoritesSortNewestFirst(!uiState.value.sortNewestFirst)
            }
        }

        fun addRecentSearch(
            displayName: String,
            lat: Double,
            lon: Double,
        ) {
            viewModelScope.launch { settingsRepository.addRecentSearch(displayName, lat, lon) }
        }

        fun teleportTo(favorite: FavoriteLocation) {
            viewModelScope.launch {
                teleportUseCase.execute(favorite.position)
            }
        }

        fun deleteFavorite(favoriteId: String) {
            viewModelScope.launch {
                favoriteRepository.deleteFavorite(favoriteId)
            }
        }

        fun renameFavorite(
            favoriteId: String,
            newName: String,
        ) {
            viewModelScope.launch {
                val favorite = uiState.value.favorites.find { it.id == favoriteId } ?: return@launch
                favoriteRepository.updateFavorite(favorite.copy(name = newName))
            }
        }

        fun addFavorite(
            name: String,
            lat: Double,
            lon: Double,
        ) {
            viewModelScope.launch {
                val uuid =
                    java.util.UUID
                        .randomUUID()
                        .toString()
                favoriteRepository.addFavorite(
                    id = uuid,
                    name = name,
                    position =
                        com.locationjoystick.core.model
                            .LatLng(lat, lon),
                    createdAt = System.currentTimeMillis(),
                )
            }
        }

        fun updateFavorite(
            id: String,
            newName: String,
            newLat: Double,
            newLon: Double,
        ) {
            viewModelScope.launch {
                val favorite = uiState.value.favorites.find { it.id == id } ?: return@launch
                favoriteRepository.updateFavorite(
                    favorite.copy(
                        name = newName,
                        position =
                            com.locationjoystick.core.model
                                .LatLng(newLat, newLon),
                    ),
                )
            }
        }

        fun setPendingDeleteId(id: String?) {
            pendingDeleteIdFlow.value = id
        }

        fun confirmDelete() {
            val idToDelete = pendingDeleteIdFlow.value ?: return
            pendingDeleteIdFlow.value = null
            deleteFavorite(idToDelete)
        }
    }

/** Emits [Unit] immediately and then every [intervalMs] milliseconds. Cancelled with its scope. */
private fun tickerFlow(intervalMs: Long) =
    flow {
        while (true) {
            emit(Unit)
            delay(intervalMs)
        }
    }
