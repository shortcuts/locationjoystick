package com.locationjoystick.feature.routes.impl

import com.locationjoystick.core.model.Route

data class RoutesUiState(
    val routes: List<Route> = emptyList(),
    val isLoading: Boolean = false,
)
