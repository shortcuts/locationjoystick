package com.locationjoystick.core.model

data class ExportData(
    val schemaVersion: Int = 2,
    val exportedAt: Long = 0L,
    val settings: AppSettings = AppSettings(),
    val speedProfiles: List<SpeedProfile> = SpeedProfile.defaultProfiles(),
    val routes: List<Route> = emptyList(),
    val favoriteLocations: List<FavoriteLocation> = emptyList(),
    val jitterIdleRadius: Double = 0.8,
    val jitterMovingRadius: Double = 1.0,
    val jitterIntervalSeconds: Int = 10,
    val jitterIdleIntervalSeconds: Int = 30,
    val jitterSpeedIdleVariationPct: Int = 5,
    val jitterSpeedMovingVariationPct: Int = 8,
    val hotLocationsEnabled: Boolean = false,
    val selectedHotLocationIds: Set<String> = emptySet(),
    val hotRoutesEnabled: Boolean = false,
    val selectedHotRouteIds: Set<String> = emptySet(),
    val routesSortNewestFirst: Boolean = true,
    val favoritesSortNewestFirst: Boolean = true,
)
