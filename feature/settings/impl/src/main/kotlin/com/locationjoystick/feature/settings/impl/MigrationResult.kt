package com.locationjoystick.feature.settings.impl

import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.Route

data class MigrationResult(
    val favorites: List<FavoriteLocation> = emptyList(),
    val routes: List<Route> = emptyList(),
    val walkSpeed: Double? = null,
    val runSpeed: Double? = null,
    val bikeSpeed: Double? = null,
    /** Named routes that could not be imported because their waypoint tables were too large. */
    val skippedOversizedRouteCount: Int = 0,
) {
    fun toImportMessage(): String {
        val base = "Imported ${favorites.size} favorites, ${routes.size} routes from GPS Joystick"
        if (skippedOversizedRouteCount <= 0) return base
        val noun = if (skippedOversizedRouteCount == 1) "route that was" else "routes that were"
        return "$base. Skipped $skippedOversizedRouteCount $noun too large to import"
    }
}
