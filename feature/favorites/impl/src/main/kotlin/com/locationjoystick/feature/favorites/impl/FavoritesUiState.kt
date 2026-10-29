package com.locationjoystick.feature.favorites.impl

import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.RealPositionSnapshot
import com.locationjoystick.core.model.SavedItemSortMode

data class FavoritesUiState(
    val favorites: List<FavoriteLocation> = emptyList(),
    val isLoading: Boolean = false,
    val pendingDeleteId: String? = null,
    val sortMode: SavedItemSortMode = SavedItemSortMode.NEWEST_FIRST,
    val hideTeleportFeatures: Boolean = false,
    val homeFavoriteId: String? = null,
    val isRoadRouteFetchInFlight: Boolean = false,
    val lastKnownPosition: RealPositionSnapshot? = null,
)

const val LAST_KNOWN_ID = "last_known_position"
