package com.locationjoystick.feature.favorites.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.common.util.parsePastedCoordinates
import com.locationjoystick.core.common.util.tickerFlow
import com.locationjoystick.core.data.CooldownEngine
import com.locationjoystick.core.data.CooldownState
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.data.TeleportUseCase
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.RecentSearch
import com.locationjoystick.core.model.SavedItemSortMode
import com.locationjoystick.core.model.sortedBySavedItemMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
                settingsRepository.getFavoritesSortMode(),
                settingsRepository.getHideTeleportFeatures(),
            ) { favorites, pendingDeleteId, sortMode, hideTeleportFeatures ->
                FavoritesUiState(
                    favorites = favorites.sortedBySavedItemMode(sortMode) { it.name },
                    isLoading = false,
                    pendingDeleteId = pendingDeleteId,
                    sortMode = sortMode,
                    hideTeleportFeatures = hideTeleportFeatures,
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

        fun setSortMode(mode: SavedItemSortMode) {
            viewModelScope.launch {
                settingsRepository.setFavoritesSortMode(mode)
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
            if (uiState.value.hideTeleportFeatures) return
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

        /**
         * Parses pasted decimal-degree text the same way route paste does, then saves the first
         * valid pair as a favorite.
         *
         * @return true if a coordinate was parsed and save was started
         */
        fun addFavoriteFromPaste(
            name: String,
            pasteText: String,
            swapLatLon: Boolean = false,
        ): Boolean {
            val point = parsePastedCoordinates(pasteText, swapLatLon).firstOrNull() ?: return false
            addFavorite(name, point.latitude, point.longitude)
            return true
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
