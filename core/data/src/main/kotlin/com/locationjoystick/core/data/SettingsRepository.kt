package com.locationjoystick.core.data

import com.locationjoystick.core.datastore.PreferencesDataSource
import com.locationjoystick.core.datastore.SpeedProfilePreferences
import com.locationjoystick.core.datastore.toActiveSpeedProfile
import com.locationjoystick.core.datastore.toKey
import com.locationjoystick.core.datastore.toWidgetFeature
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.SpeedProfile
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.core.model.WidgetFeature
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Display order for widget features in the floating widget.
 * Items are sorted by this order when retrieved.
 */
private val widgetDisplayOrder =
    listOf(
        WidgetFeature.MAP_FLOATING,
        WidgetFeature.JOYSTICK_TOGGLE,
        WidgetFeature.JOYSTICK_LOCK,
        WidgetFeature.ROUTES_FLOATING,
        WidgetFeature.FAVORITES_FLOATING,
        WidgetFeature.SPEED_CYCLE,
    )

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

        fun getWidgetFeatures(): Flow<List<WidgetFeature>> =
            dataSource.getWidgetItems().map { keys ->
                keys
                    .mapNotNull { key -> key.toWidgetFeature() }
                    .sortedBy { widgetDisplayOrder.indexOf(it) }
            }

        fun getOnboardingComplete(): Flow<Boolean> = dataSource.getOnboardingComplete()

        fun getRoamingDefaults(): Flow<RoamingDefaults> = dataSource.getRoamingDefaults()

        suspend fun updateRoamingDefaults(defaults: RoamingDefaults) = dataSource.updateRoamingDefaults(defaults)

        suspend fun setWalkSpeed(ms: Double) = dataSource.setWalkSpeed(ms)

        suspend fun setRunSpeed(ms: Double) = dataSource.setRunSpeed(ms)

        suspend fun setBikeSpeed(ms: Double) = dataSource.setBikeSpeed(ms)

        suspend fun setActiveProfileId(profileId: String) = dataSource.setActiveProfileId(profileId)

        suspend fun setWidgetFeatures(features: List<WidgetFeature>) {
            val keys = features.map { it.toKey() }.toSet()
            dataSource.setWidgetItems(keys)
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

        fun getLastTeleportTime(): Flow<Long> = dataSource.getLastTeleportTime()

        suspend fun setLastTeleportTime(ms: Long) = dataSource.setLastTeleportTime(ms)

        fun getMapFollowsLocation(): Flow<Boolean> = dataSource.getMapFollowsLocation()

        suspend fun setMapFollowsLocation(enabled: Boolean) = dataSource.setMapFollowsLocation(enabled)

        fun getRealismBearingHoldIdle(): Flow<Boolean> = dataSource.getRealismBearingHoldIdle()

        fun getRealismAltitudeEnabled(): Flow<Boolean> = dataSource.getRealismAltitudeEnabled()

        fun getRealismWarmupEnabled(): Flow<Boolean> = dataSource.getRealismWarmupEnabled()

        fun getRealismSatelliteExtrasEnabled(): Flow<Boolean> = dataSource.getRealismSatelliteExtrasEnabled()

        fun getRealismSuspendedMockingEnabled(): Flow<Boolean> = dataSource.getRealismSuspendedMockingEnabled()

        suspend fun setRealismBearingHoldIdle(enabled: Boolean) = dataSource.setRealismBearingHoldIdle(enabled)

        suspend fun setRealismAltitudeEnabled(enabled: Boolean) = dataSource.setRealismAltitudeEnabled(enabled)

        suspend fun setRealismWarmupEnabled(enabled: Boolean) = dataSource.setRealismWarmupEnabled(enabled)

        suspend fun setRealismSatelliteExtrasEnabled(enabled: Boolean) = dataSource.setRealismSatelliteExtrasEnabled(enabled)

        suspend fun setRealismSuspendedMockingEnabled(enabled: Boolean) = dataSource.setRealismSuspendedMockingEnabled(enabled)
    }
