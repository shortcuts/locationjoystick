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
    val jitterIntervalSeconds: Int = 3,
    val jitterIdleIntervalSeconds: Int = 30,
)
