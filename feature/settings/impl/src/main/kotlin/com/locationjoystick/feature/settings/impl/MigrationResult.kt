package com.locationjoystick.feature.settings.impl

import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.Route

data class MigrationResult(
    val favorites: List<FavoriteLocation> = emptyList(),
    val routes: List<Route> = emptyList(),
    val walkSpeed: Double? = null,
    val runSpeed: Double? = null,
    val bikeSpeed: Double? = null,
)
