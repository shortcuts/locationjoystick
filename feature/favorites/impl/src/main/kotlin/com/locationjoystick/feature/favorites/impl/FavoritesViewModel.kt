package com.locationjoystick.feature.favorites.impl

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.location.MockLocationService
import com.locationjoystick.core.model.FavoriteLocation
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val favoriteRepository: FavoriteRepository,
        private val locationRepository: LocationRepository,
    ) : ViewModel() {
        private val pendingDeleteIdFlow = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

        val uiState: StateFlow<FavoritesUiState> =
            kotlinx.coroutines
                .flow
                .combine(
                    favoriteRepository.getFavorites(),
                    pendingDeleteIdFlow,
                ) { favorites, pendingDeleteId ->
                    FavoritesUiState(
                        favorites = favorites,
                        isLoading = false,
                        pendingDeleteId = pendingDeleteId,
                    )
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = FavoritesUiState(isLoading = true),
                )

        val currentPosition: com.locationjoystick.core.model.LatLng?
            get() = locationRepository.currentPosition.value

        fun teleportTo(favorite: FavoriteLocation) {
            val intent =
                Intent(context, MockLocationService::class.java).apply {
                    action = MockLocationService.ACTION_UPDATE_POSITION
                    putExtra("lat", favorite.position.latitude)
                    putExtra("lon", favorite.position.longitude)
                }
            context.startService(intent)
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
