package com.locationjoystick.core.data

import com.locationjoystick.core.datastore.PreferencesDataSource
import com.locationjoystick.core.datastore.SettingsSnapshot
import com.locationjoystick.core.datastore.SpeedProfilePreferences
import com.locationjoystick.core.datastore.toActiveSpeedProfile
import com.locationjoystick.core.datastore.toAppFeature
import com.locationjoystick.core.model.AppFeature
import com.locationjoystick.core.model.FeatureSurface
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RecentSearch
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.SpeedProfile
import com.locationjoystick.core.model.SpeedUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for all user settings and preferences.
 *
 * Acts as the single source of truth for:
 * - Speed profiles (walk/run/bike speeds)
 * - Active speed profile
 * - Widget features configuration
 * - Onboarding state
 * - Roaming defaults
 * - Last remembered location
 *
 * All data flows from DataStore through this repository.
 * ViewModels and services consume these flows to observe settings changes.
 */
@Singleton
class SettingsRepository
    @Inject
    constructor(
        private val dataSource: PreferencesDataSource,
    ) {
        fun getSpeedProfiles(): Flow<List<SpeedProfile>> =
            dataSource.getSpeedProfiles().map { prefs ->
                listOf(
                    SpeedProfile(id = "walk", name = "Walk", speedMetersPerSecond = prefs.walkSpeedMs),
                    SpeedProfile(id = "run", name = "Run", speedMetersPerSecond = prefs.runSpeedMs),
                    SpeedProfile(id = "bike", name = "Bike", speedMetersPerSecond = prefs.bikeSpeedMs),
                )
            }

        fun getWalkSpeed(): Flow<Double> = dataSource.getSpeedProfiles().map { it.walkSpeedMs }

        fun getRunSpeed(): Flow<Double> = dataSource.getSpeedProfiles().map { it.runSpeedMs }

        fun getBikeSpeed(): Flow<Double> = dataSource.getSpeedProfiles().map { it.bikeSpeedMs }

        fun getActiveSpeedProfile(): Flow<SpeedProfile> =
            dataSource.getSpeedProfiles().map { prefs ->
                prefs.toActiveSpeedProfile()
            }

        fun getFeatureOrder(): Flow<List<AppFeature>> = dataSource.getFeatureOrder()

        suspend fun setFeatureOrder(order: List<AppFeature>) = dataSource.setFeatureOrder(order)

        fun getWidgetFeatures(): Flow<List<AppFeature>> =
            combine(dataSource.getFeatureOrder(), dataSource.getWidgetItems()) { order, keys ->
                val enabled = keys.mapNotNull { it.toAppFeature() }.toSet()
                order.filter { FeatureSurface.WIDGET in it.surfaces && it in enabled }
            }

        fun getMapFeatures(): Flow<List<AppFeature>> =
            combine(dataSource.getFeatureOrder(), dataSource.getMapItems()) { order, keys ->
                val enabled = keys.mapNotNull { it.toAppFeature() }.toSet()
                order.filter { FeatureSurface.MAP in it.surfaces && it in enabled }
            }

        /** All map-eligible features in shared display order, regardless of enabled state. */
        fun getMapFeatureOrder(): Flow<List<AppFeature>> =
            dataSource.getFeatureOrder().map { order -> order.filter { FeatureSurface.MAP in it.surfaces } }

        fun getEnabledMapFeatures(): Flow<Set<AppFeature>> =
            dataSource.getMapItems().map { keys -> keys.mapNotNull { it.toAppFeature() }.toSet() }

        fun getOnboardingComplete(): Flow<Boolean> = dataSource.getOnboardingComplete()

        fun getRoamingDefaults(): Flow<RoamingDefaults> = dataSource.getRoamingDefaults()

        suspend fun updateRoamingDefaults(defaults: RoamingDefaults) = dataSource.updateRoamingDefaults(defaults)

        suspend fun setWalkSpeed(ms: Double) = dataSource.setWalkSpeed(ms)

        suspend fun setRunSpeed(ms: Double) = dataSource.setRunSpeed(ms)

        suspend fun setBikeSpeed(ms: Double) = dataSource.setBikeSpeed(ms)

        suspend fun setActiveProfileId(profileId: String) = dataSource.setActiveProfileId(profileId)

        suspend fun setWidgetFeatures(features: Set<AppFeature>) {
            dataSource.setWidgetItems(features.map { it.name.lowercase() }.toSet())
        }

        suspend fun setMapFeatures(features: Set<AppFeature>) {
            dataSource.setMapItems(features.map { it.name.lowercase() }.toSet())
        }

        suspend fun setOnboardingComplete(complete: Boolean) = dataSource.setOnboardingComplete(complete)

        suspend fun setSpeedUnit(unit: SpeedUnit) {
            dataSource.setSpeedUnit(unit.name)
        }

        fun getSpeedUnit(): Flow<SpeedUnit> =
            dataSource.getSpeedUnit().map { unitName ->
                try {
                    SpeedUnit.valueOf(unitName)
                } catch (e: IllegalArgumentException) {
                    SpeedUnit.KMH
                }
            }

        fun getRememberLastLocation(): Flow<Boolean> = dataSource.getRememberLastLocation()

        suspend fun setRememberLastLocation(enabled: Boolean) = dataSource.setRememberLastLocation(enabled)

        fun getLastLocation(): Flow<LatLng?> = dataSource.getLastLocation()

        suspend fun setLastLocation(location: LatLng) = dataSource.setLastLocation(location)

        fun getJitterIdleRadius(): Flow<Double> = dataSource.getJitterIdleRadius()

        fun getJitterMovingRadius(): Flow<Double> = dataSource.getJitterMovingRadius()

        fun getJitterIntervalSeconds(): Flow<Int> = dataSource.getJitterIntervalSeconds()

        suspend fun setJitterIdleRadius(meters: Double) = dataSource.setJitterIdleRadius(meters)

        suspend fun setJitterMovingRadius(meters: Double) = dataSource.setJitterMovingRadius(meters)

        suspend fun setJitterIntervalSeconds(seconds: Int) = dataSource.setJitterIntervalSeconds(seconds)

        fun getJitterIdleIntervalSeconds(): Flow<Int> = dataSource.getJitterIdleIntervalSeconds()

        suspend fun setJitterIdleIntervalSeconds(seconds: Int) = dataSource.setJitterIdleIntervalSeconds(seconds)

        fun getLastTeleportTime(): Flow<Long> = dataSource.getLastTeleportTime()

        suspend fun setLastTeleportTime(ms: Long) = dataSource.setLastTeleportTime(ms)

        fun getMapFollowsLocation(): Flow<Boolean> = dataSource.getMapFollowsLocation()

        suspend fun setMapFollowsLocation(enabled: Boolean) = dataSource.setMapFollowsLocation(enabled)

        fun getRealismBearingHoldIdle(): Flow<Boolean> = dataSource.getRealismBearingHoldIdle()

        fun getRealismAltitudeEnabled(): Flow<Boolean> = dataSource.getRealismAltitudeEnabled()

        fun getRealismWarmupEnabled(): Flow<Boolean> = dataSource.getRealismWarmupEnabled()

        fun getRealismSatelliteExtrasEnabled(): Flow<Boolean> = dataSource.getRealismSatelliteExtrasEnabled()

        fun getRealismSuspendedMockingEnabled(): Flow<Boolean> = dataSource.getRealismSuspendedMockingEnabled()

        fun getRealismPedometerMockingEnabled(): Flow<Boolean> = dataSource.getRealismPedometerMockingEnabled()

        suspend fun setRealismBearingHoldIdle(enabled: Boolean) = dataSource.setRealismBearingHoldIdle(enabled)

        suspend fun setRealismAltitudeEnabled(enabled: Boolean) = dataSource.setRealismAltitudeEnabled(enabled)

        suspend fun setRealismWarmupEnabled(enabled: Boolean) = dataSource.setRealismWarmupEnabled(enabled)

        suspend fun setRealismSatelliteExtrasEnabled(enabled: Boolean) = dataSource.setRealismSatelliteExtrasEnabled(enabled)

        suspend fun setRealismSuspendedMockingEnabled(enabled: Boolean) = dataSource.setRealismSuspendedMockingEnabled(enabled)

        suspend fun setRealismPedometerMockingEnabled(enabled: Boolean) = dataSource.setRealismPedometerMockingEnabled(enabled)

        fun getRoutesSortNewestFirst(): Flow<Boolean> = dataSource.getRoutesSortNewestFirst()

        suspend fun setRoutesSortNewestFirst(newestFirst: Boolean) = dataSource.setRoutesSortNewestFirst(newestFirst)

        fun getFavoritesSortNewestFirst(): Flow<Boolean> = dataSource.getFavoritesSortNewestFirst()

        suspend fun setFavoritesSortNewestFirst(newestFirst: Boolean) = dataSource.setFavoritesSortNewestFirst(newestFirst)

        fun getRecentSearches(): Flow<List<RecentSearch>> = dataSource.getRecentSearches()

        suspend fun addRecentSearch(
            displayName: String,
            lat: Double,
            lon: Double,
        ) = dataSource.addRecentSearch(displayName, lat, lon)

        fun getJitterSpeedIdleVariationPct(): Flow<Int> = dataSource.getJitterSpeedIdleVariationPct()

        fun getJitterSpeedMovingVariationPct(): Flow<Int> = dataSource.getJitterSpeedMovingVariationPct()

        suspend fun setJitterSpeedIdleVariationPct(pct: Int) = dataSource.setJitterSpeedIdleVariationPct(pct)

        suspend fun setJitterSpeedMovingVariationPct(pct: Int) = dataSource.setJitterSpeedMovingVariationPct(pct)

        fun getElevationTiltJitterDegrees(): Flow<Float> = dataSource.getElevationTiltJitterDegrees()

        suspend fun setElevationTiltJitterDegrees(degrees: Float) = dataSource.setElevationTiltJitterDegrees(degrees)

        fun getElevationNoiseAmplitudeMs2(): Flow<Float> = dataSource.getElevationNoiseAmplitudeMs2()

        suspend fun setElevationNoiseAmplitudeMs2(amplitude: Float) = dataSource.setElevationNoiseAmplitudeMs2(amplitude)

        fun getHotLocationsEnabled(): Flow<Boolean> = dataSource.getHotLocationsEnabled()

        suspend fun setHotLocationsEnabled(enabled: Boolean) = dataSource.setHotLocationsEnabled(enabled)

        fun getFloatingMapQuickWalk(): Flow<Boolean> = dataSource.getFloatingMapQuickWalk()

        fun getTapToWalkOverlayEnabled(): Flow<Boolean> = dataSource.getTapToWalkOverlayEnabled()

        fun getTapToWalkScaleMpx(): Flow<Int> = dataSource.getTapToWalkScaleMpx()

        fun getCompassTrackingEnabled(): Flow<Boolean> = dataSource.getCompassTrackingEnabled()

        suspend fun setCompassTrackingEnabled(enabled: Boolean) = dataSource.setCompassTrackingEnabled(enabled)

        fun getCompassRegionCxPct(): Flow<Float> = dataSource.getCompassRegionCxPct()

        suspend fun setCompassRegionCxPct(value: Float) = dataSource.setCompassRegionCxPct(value)

        fun getCompassRegionCyPct(): Flow<Float> = dataSource.getCompassRegionCyPct()

        suspend fun setCompassRegionCyPct(value: Float) = dataSource.setCompassRegionCyPct(value)

        fun getCompassRegionRadiusPct(): Flow<Float> = dataSource.getCompassRegionRadiusPct()

        suspend fun setCompassRegionRadiusPct(value: Float) = dataSource.setCompassRegionRadiusPct(value)

        fun getSettingsSnapshot(): Flow<SettingsSnapshot> = dataSource.getSettingsSnapshot()

        suspend fun applySnapshot(snapshot: SettingsSnapshot) = dataSource.applySnapshot(snapshot)
    }
