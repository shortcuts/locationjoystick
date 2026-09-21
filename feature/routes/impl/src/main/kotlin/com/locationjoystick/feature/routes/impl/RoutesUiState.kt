package com.locationjoystick.feature.routes.impl

import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.SavedItemSortMode

data class RoutesUiState(
    val routes: List<Route> = emptyList(),
    val isLoading: Boolean = false,
    val sortMode: SavedItemSortMode = SavedItemSortMode.NEWEST_FIRST,
    val hideTeleportFeatures: Boolean = false,
    val isRoadRouteFetchInFlight: Boolean = false,
)
