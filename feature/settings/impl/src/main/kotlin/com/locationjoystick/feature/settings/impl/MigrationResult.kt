package com.locationjoystick.feature.settings.impl

import android.content.Context
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.Route
import com.locationjoystick.feature.settings.impl.R

data class MigrationResult(
    val favorites: List<FavoriteLocation> = emptyList(),
    val routes: List<Route> = emptyList(),
    val walkSpeed: Double? = null,
    val runSpeed: Double? = null,
    val bikeSpeed: Double? = null,
    /** Named routes that could not be imported because their waypoint tables were too large. */
    val skippedOversizedRouteCount: Int = 0,
) {
    fun toImportMessage(context: Context): String {
        val base = context.getString(R.string.settings_migration_imported_gps_joystick, favorites.size, routes.size)
        if (skippedOversizedRouteCount <= 0) return base
        val skipped =
            context.resources.getQuantityString(
                R.plurals.settings_migration_skipped_oversized,
                skippedOversizedRouteCount,
                skippedOversizedRouteCount,
            )
        return "$base. $skipped"
    }
}
