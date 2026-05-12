package com.locationjoystick.feature.favorites.impl

import com.locationjoystick.core.model.FavoriteLocation

data class FavoritesUiState(
    val favorites: List<FavoriteLocation> = emptyList(),
    val isLoading: Boolean = false,
    val deletedFavorite: FavoriteLocation? = null,
)
