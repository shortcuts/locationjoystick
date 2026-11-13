package com.locationjoystick.feature.settings.impl

import com.locationjoystick.core.data.BackupRepository
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.HotLocationsRepository
import com.locationjoystick.core.data.HotRoutesRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.ExportData
import kotlinx.coroutines.flow.first

/** Writes an import into the repositories. Built by [SettingsViewModel] so the view model stays under its size budget. */
internal class ExportDataApplier(
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val favoriteRepository: FavoriteRepository,
    private val routeRepository: RouteRepository,
    private val hotLocationsRepository: HotLocationsRepository,
    private val hotRoutesRepository: HotRoutesRepository,
) {
    suspend fun apply(
        data: ExportData,
        replace: Boolean,
    ) {
        backupRepository.apply(data.favoriteLocations, data.routes, clearFavorites = replace, clearRoutes = replace)
        // Preserve fields not present in ExportData by reading current persisted values.
        val currentSnapshot = settingsRepository.getSettingsSnapshot().first()
        val profileById = data.speedProfiles.associateBy { it.id }
        settingsRepository.applySnapshot(
            currentSnapshot.copy(
                settings = data.settings,
                slowWalkSpeedMs = profileById["slow_walk"]?.speedMetersPerSecond ?: currentSnapshot.slowWalkSpeedMs,
                walkSpeedMs = profileById["walk"]?.speedMetersPerSecond ?: currentSnapshot.walkSpeedMs,
                runSpeedMs = profileById["run"]?.speedMetersPerSecond ?: currentSnapshot.runSpeedMs,
                bikeSpeedMs = profileById["bike"]?.speedMetersPerSecond ?: currentSnapshot.bikeSpeedMs,
                driveSpeedMs = profileById["drive"]?.speedMetersPerSecond ?: currentSnapshot.driveSpeedMs,
                jitterIdleRadius = data.jitterIdleRadius,
                jitterMovingRadius = data.jitterMovingRadius,
                jitterMaxStepMeters = data.jitterMaxStepMeters,
                jitterSpeedIdleVariationPct = data.jitterSpeedIdleVariationPct,
                jitterSpeedMovingVariationPct = data.jitterSpeedMovingVariationPct,
                jitterSpeedIdleWobbleProbabilityPct = data.jitterSpeedIdleWobbleProbabilityPct,
                hotLocationsEnabled = data.hotLocationsEnabled,
                selectedHotLocationIds = data.selectedHotLocationIds,
                hotRoutesEnabled = data.hotRoutesEnabled,
                selectedHotRouteIds = data.selectedHotRouteIds,
            ),
        )
        settingsRepository.setTapToWalkOverlayEnabled(data.settings.tapToWalkOverlayEnabled)
        settingsRepository.setTapToWalkMoves(data.settings.tapToWalkMoves)
        settingsRepository.setActiveProfileId(data.settings.activeSpeedProfileId)
        settingsRepository.setReconnectTapEnabled(data.settings.reconnectTapEnabled)
        settingsRepository.setKeepWidgetOnIdle(data.settings.keepWidgetOnIdle)
        settingsRepository.setThemeMode(data.settings.themeMode)
        if (data.hotLocationsEnabled) {
            favoriteRepository.upsertHotLocations(hotLocationsRepository.locations.value, data.selectedHotLocationIds)
        } else {
            favoriteRepository.removeHotLocations()
        }
        if (data.hotRoutesEnabled) {
            routeRepository.upsertHotRoutes(hotRoutesRepository.routes.value, data.selectedHotRouteIds)
        } else {
            routeRepository.removeHotRoutes()
        }
        if (replace) {
            settingsRepository.setRoutesSortNewestFirst(data.routesSortNewestFirst)
            settingsRepository.setFavoritesSortNewestFirst(data.favoritesSortNewestFirst)
            settingsRepository.setRoutesSortMode(data.routesSortMode)
            settingsRepository.setFavoritesSortMode(data.favoritesSortMode)
            // After the sort modes: choosing a mode clears the manual order.
            if (data.favoritesManualOrder.isNotEmpty()) settingsRepository.setFavoritesManualOrder(data.favoritesManualOrder)
            if (data.routesManualOrder.isNotEmpty()) settingsRepository.setRoutesManualOrder(data.routesManualOrder)
        } else {
            val favoritesOrder = settingsRepository.getFavoritesManualOrder().first()
            if (favoritesOrder.isNotEmpty()) {
                val imported = data.favoritesManualOrder + data.favoriteLocations.map { it.id }
                settingsRepository.setFavoritesManualOrder((favoritesOrder + imported).distinct())
            }
            val routesOrder = settingsRepository.getRoutesManualOrder().first()
            if (routesOrder.isNotEmpty()) {
                val imported = data.routesManualOrder + data.routes.map { it.id }
                settingsRepository.setRoutesManualOrder((routesOrder + imported).distinct())
            }
        }
        if (replace || data.homeFavoriteId != null) settingsRepository.setHomeFavoriteId(data.homeFavoriteId)
        clearHomeIfMissing()
    }

    /** Home is a favorite id; clear it when that favorite no longer exists (e.g. hot locations turned off). */
    suspend fun clearHomeIfMissing() {
        val id = settingsRepository.getHomeFavoriteId().first() ?: return
        if (favoriteRepository.getFavorites().first().none { it.id == id }) settingsRepository.setHomeFavoriteId(null)
    }
}
