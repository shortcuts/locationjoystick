package com.locationjoystick.feature.favorites.impl

import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.SavedItemSortMode

data class FavoritesUiState(
    val favorites: List<FavoriteLocation> = emptyList(),
    val isLoading: Boolean = false,
    val pendingDeleteId: String? = null,
    val sortMode: SavedItemSortMode = SavedItemSortMode.NEWEST_FIRST,
    val hideTeleportFeatures: Boolean = false,
)
