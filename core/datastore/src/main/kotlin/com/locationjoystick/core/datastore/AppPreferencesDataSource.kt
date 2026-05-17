package com.locationjoystick.core.datastore

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.SpeedProfile
import com.locationjoystick.core.model.WidgetFeature
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

interface PreferencesDataSource {
    fun getSpeedProfiles(): Flow<SpeedProfilePreferences>

    suspend fun setWalkSpeed(ms: Double)

    suspend fun setRunSpeed(ms: Double)

    suspend fun setBikeSpeed(ms: Double)

    suspend fun setActiveProfileId(profileId: String)

    fun getWidgetItems(): Flow<Set<String>>

    suspend fun setWidgetItems(items: Set<String>)

    fun getRoamingDefaults(): Flow<RoamingDefaults>

    suspend fun updateRoamingDefaults(defaults: RoamingDefaults)

    fun getOnboardingComplete(): Flow<Boolean>

    suspend fun setOnboardingComplete(complete: Boolean)

    fun getSpeedUnit(): Flow<String>

    suspend fun setSpeedUnit(unit: String)

    fun getRememberLastLocation(): Flow<Boolean>

    suspend fun setRememberLastLocation(enabled: Boolean)

    fun getLastLocation(): Flow<LatLng?>

    suspend fun setLastLocation(location: LatLng)

    fun getJitterIdleRadius(): Flow<Double>

    fun getJitterMovingRadius(): Flow<Double>

    fun getJitterIntervalSeconds(): Flow<Int>

    suspend fun setJitterIdleRadius(meters: Double)

    suspend fun setJitterMovingRadius(meters: Double)

    suspend fun setJitterIntervalSeconds(seconds: Int)
}

fun SpeedProfilePreferences.toActiveSpeedProfile(): SpeedProfile {
    val speedMs =
        when (activeProfileId) {
            "walk" -> walkSpeedMs
            "run" -> runSpeedMs
            "bike" -> bikeSpeedMs
            else -> walkSpeedMs
        }
    return SpeedProfile(
        id = activeProfileId,
        name = activeProfileId.replaceFirstChar { it.uppercaseChar() },
        speedMetersPerSecond = speedMs,
    )
}

fun WidgetFeature.toKey(): String = name.lowercase()

fun String.toWidgetFeature(): WidgetFeature? = WidgetFeature.entries.firstOrNull { it.name.lowercase() == this }

@Singleton
class AppPreferencesDataSource
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : PreferencesDataSource {
        private object Keys {
            val WALK_SPEED_MS = doublePreferencesKey("walk_speed_ms")
            val RUN_SPEED_MS = doublePreferencesKey("run_speed_ms")
            val BIKE_SPEED_MS = doublePreferencesKey("bike_speed_ms")
            val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
            val WIDGET_ITEMS = stringSetPreferencesKey("widget_items")
            val ROAMING_RADIUS_METERS = doublePreferencesKey("roaming_radius_meters")
            val ROAMING_DISTANCE_METERS = doublePreferencesKey("roaming_distance_meters")
            val ROAMING_ROAD_FOLLOWING = booleanPreferencesKey("roaming_road_following")
            val ROAMING_TRANSPORT_MODE = stringPreferencesKey("roaming_transport_mode")
            val ROAMING_RETURN_TO_START = booleanPreferencesKey("roaming_return_to_start")
            val ROAMING_SPEED_PROFILE_ID = stringPreferencesKey("roaming_speed_profile_id")
            val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
            val SPEED_UNIT = stringPreferencesKey("speed_unit")
            val REMEMBER_LAST_LOCATION = booleanPreferencesKey("remember_last_location")
            val LAST_LATITUDE = doublePreferencesKey("last_latitude")
            val LAST_LONGITUDE = doublePreferencesKey("last_longitude")
            val JITTER_IDLE_RADIUS_METERS = doublePreferencesKey("jitter_idle_radius_meters")
            val JITTER_MOVING_RADIUS_METERS = doublePreferencesKey("jitter_moving_radius_meters")
            val JITTER_INTERVAL_SECONDS = intPreferencesKey("jitter_interval_seconds")
        }

        override fun getSpeedProfiles(): Flow<SpeedProfilePreferences> =
            dataStore.data
                .catch { e ->
                    if (e is IOException) {
                        Log.e(TAG, "Error reading speed profile preferences", e)
                        emit(emptyPreferences())
                    } else {
                        throw e
                    }
                }.map { prefs ->
                    SpeedProfilePreferences(
                        walkSpeedMs = prefs[Keys.WALK_SPEED_MS] ?: DEFAULT_WALK_SPEED_MS,
                        runSpeedMs = prefs[Keys.RUN_SPEED_MS] ?: DEFAULT_RUN_SPEED_MS,
                        bikeSpeedMs = prefs[Keys.BIKE_SPEED_MS] ?: DEFAULT_BIKE_SPEED_MS,
                        activeProfileId = prefs[Keys.ACTIVE_PROFILE_ID] ?: DEFAULT_ACTIVE_PROFILE_ID,
                    )
                }

        override suspend fun setWalkSpeed(ms: Double) {
            dataStore.edit { prefs -> prefs[Keys.WALK_SPEED_MS] = ms.coerceIn(MIN_SPEED_MS, MAX_SPEED_MS) }
        }

        override suspend fun setRunSpeed(ms: Double) {
            dataStore.edit { prefs -> prefs[Keys.RUN_SPEED_MS] = ms.coerceIn(MIN_SPEED_MS, MAX_SPEED_MS) }
        }

        override suspend fun setBikeSpeed(ms: Double) {
            dataStore.edit { prefs -> prefs[Keys.BIKE_SPEED_MS] = ms.coerceIn(MIN_SPEED_MS, MAX_SPEED_MS) }
        }

        override suspend fun setActiveProfileId(profileId: String) {
            dataStore.edit { prefs -> prefs[Keys.ACTIVE_PROFILE_ID] = profileId }
        }

        override fun getWidgetItems(): Flow<Set<String>> =
            dataStore.data
                .catch { e ->
                    if (e is IOException) {
                        Log.e(TAG, "Error reading widget preferences", e)
                        emit(emptyPreferences())
                    } else {
                        throw e
                    }
                }.map { prefs -> prefs[Keys.WIDGET_ITEMS] ?: DEFAULT_WIDGET_ITEMS }

        override suspend fun setWidgetItems(items: Set<String>) {
            dataStore.edit { prefs -> prefs[Keys.WIDGET_ITEMS] = items }
        }

        override fun getRoamingDefaults(): Flow<RoamingDefaults> =
            dataStore.data
                .catch { e ->
                    if (e is IOException) {
                        Log.e(TAG, "Error reading roaming preferences", e)
                        emit(emptyPreferences())
                    } else {
                        throw e
                    }
                }.map { prefs ->
                    RoamingDefaults(
                        radiusMeters = prefs[Keys.ROAMING_RADIUS_METERS] ?: DEFAULT_ROAMING_RADIUS_METERS,
                        distanceMeters = prefs[Keys.ROAMING_DISTANCE_METERS] ?: DEFAULT_ROAMING_DISTANCE_METERS,
                        speedProfileId = prefs[Keys.ROAMING_SPEED_PROFILE_ID] ?: DEFAULT_ROAMING_SPEED_PROFILE_ID,
                        followRoads = prefs[Keys.ROAMING_ROAD_FOLLOWING] ?: DEFAULT_ROAMING_FOLLOW_ROADS,
                        returnToInitialLocation = prefs[Keys.ROAMING_RETURN_TO_START] ?: DEFAULT_ROAMING_RETURN_TO_START,
                    )
                }

        override suspend fun updateRoamingDefaults(defaults: RoamingDefaults) {
            dataStore.edit { prefs ->
                prefs[Keys.ROAMING_RADIUS_METERS] = defaults.radiusMeters
                prefs[Keys.ROAMING_DISTANCE_METERS] = defaults.distanceMeters
                prefs[Keys.ROAMING_SPEED_PROFILE_ID] = defaults.speedProfileId
                prefs[Keys.ROAMING_ROAD_FOLLOWING] = defaults.followRoads
                prefs[Keys.ROAMING_RETURN_TO_START] = defaults.returnToInitialLocation
            }
        }

        override fun getOnboardingComplete(): Flow<Boolean> =
            dataStore.data
                .catch { e ->
                    if (e is IOException) {
                        Log.e(TAG, "Error reading onboarding preferences", e)
                        emit(emptyPreferences())
                    } else {
                        throw e
                    }
                }.map { prefs -> prefs[Keys.ONBOARDING_COMPLETE] ?: false }

        override suspend fun setOnboardingComplete(complete: Boolean) {
            dataStore.edit { prefs -> prefs[Keys.ONBOARDING_COMPLETE] = complete }
        }

        override fun getSpeedUnit(): Flow<String> =
            dataStore.data
                .catch { e ->
                    if (e is IOException) {
                        Log.e(TAG, "Error reading speed unit preference", e)
                        emit(emptyPreferences())
                    } else {
                        throw e
                    }
                }.map { prefs -> prefs[Keys.SPEED_UNIT] ?: AppConstants.ProfileConstants.DEFAULT_SPEED_UNIT }

        override suspend fun setSpeedUnit(unit: String) {
            dataStore.edit { prefs -> prefs[Keys.SPEED_UNIT] = unit }
        }

        override fun getRememberLastLocation(): Flow<Boolean> =
            dataStore.data
                .catch { e ->
                    if (e is IOException) {
                        Log.e(TAG, "Error reading remember last location preference", e)
                        emit(emptyPreferences())
                    } else {
                        throw e
                    }
                }.map { prefs -> prefs[Keys.REMEMBER_LAST_LOCATION] ?: AppConstants.DataStoreConstants.DEFAULT_REMEMBER_LAST_LOCATION }

        override suspend fun setRememberLastLocation(enabled: Boolean) {
            dataStore.edit { prefs -> prefs[Keys.REMEMBER_LAST_LOCATION] = enabled }
        }

        override fun getLastLocation(): Flow<LatLng?> =
            dataStore.data
                .catch { e ->
                    if (e is IOException) {
                        Log.e(TAG, "Error reading last location preference", e)
                        emit(emptyPreferences())
                    } else {
                        throw e
                    }
                }.map { prefs ->
                    val lat = prefs[Keys.LAST_LATITUDE]
                    val lon = prefs[Keys.LAST_LONGITUDE]
                    if (lat != null && lon != null) LatLng(lat, lon) else null
                }

        override suspend fun setLastLocation(location: LatLng) {
            dataStore.edit { prefs ->
                prefs[Keys.LAST_LATITUDE] = location.latitude
                prefs[Keys.LAST_LONGITUDE] = location.longitude
            }
        }

        override fun getJitterIdleRadius(): Flow<Double> =
            dataStore.data
                .catch { e ->
                    if (e is IOException) {
                        Log.e(TAG, "Error reading jitter idle radius preference", e)
                        emit(emptyPreferences())
                    } else {
                        throw e
                    }
                }.map { prefs -> prefs[Keys.JITTER_IDLE_RADIUS_METERS] ?: DEFAULT_JITTER_IDLE_RADIUS_METERS }

        override fun getJitterMovingRadius(): Flow<Double> =
            dataStore.data
                .catch { e ->
                    if (e is IOException) {
                        Log.e(TAG, "Error reading jitter moving radius preference", e)
                        emit(emptyPreferences())
                    } else {
                        throw e
                    }
                }.map { prefs -> prefs[Keys.JITTER_MOVING_RADIUS_METERS] ?: DEFAULT_JITTER_MOVING_RADIUS_METERS }

        override suspend fun setJitterIdleRadius(meters: Double) {
            dataStore.edit { prefs -> prefs[Keys.JITTER_IDLE_RADIUS_METERS] = meters.coerceIn(0.0, MAX_JITTER_RADIUS_METERS) }
        }

        override suspend fun setJitterMovingRadius(meters: Double) {
            dataStore.edit { prefs -> prefs[Keys.JITTER_MOVING_RADIUS_METERS] = meters.coerceIn(0.0, MAX_JITTER_RADIUS_METERS) }
        }

        override fun getJitterIntervalSeconds(): Flow<Int> =
            dataStore.data
                .catch { e ->
                    if (e is IOException) {
                        Log.e(TAG, "Error reading jitter interval preference", e)
                        emit(emptyPreferences())
                    } else {
                        throw e
                    }
                }.map { prefs -> prefs[Keys.JITTER_INTERVAL_SECONDS] ?: DEFAULT_JITTER_INTERVAL_SECONDS }

        override suspend fun setJitterIntervalSeconds(seconds: Int) {
            dataStore.edit { prefs ->
                prefs[Keys.JITTER_INTERVAL_SECONDS] = seconds.coerceIn(MIN_JITTER_INTERVAL_SECONDS, MAX_JITTER_INTERVAL_SECONDS)
            }
        }

        companion object {
            const val DATASTORE_FILE_NAME = AppConstants.DataStoreConstants.FILE_NAME

            const val DEFAULT_WALK_SPEED_MS = AppConstants.ProfileConstants.WALK_SPEED_MPS
            const val DEFAULT_RUN_SPEED_MS = AppConstants.ProfileConstants.RUN_SPEED_MPS
            const val DEFAULT_BIKE_SPEED_MS = AppConstants.ProfileConstants.BIKE_SPEED_MPS
            const val DEFAULT_ACTIVE_PROFILE_ID = AppConstants.ProfileConstants.DEFAULT_ACTIVE_PROFILE_ID

            const val MIN_SPEED_MS = AppConstants.ProfileConstants.MIN_SPEED_MS
            const val MAX_SPEED_MS = AppConstants.ProfileConstants.MAX_SPEED_MS

            val DEFAULT_WIDGET_ITEMS: Set<String> =
                setOf(
                    WidgetFeature.MAP_FLOATING.name.lowercase(),
                    WidgetFeature.JOYSTICK_TOGGLE.name.lowercase(),
                    WidgetFeature.JOYSTICK_LOCK.name.lowercase(),
                    WidgetFeature.ROUTES_FLOATING.name.lowercase(),
                    WidgetFeature.FAVORITES_FLOATING.name.lowercase(),
                    WidgetFeature.SPEED_CYCLE.name.lowercase(),
                )

            const val DEFAULT_ROAMING_RADIUS_METERS = AppConstants.RoamingConstants.DEFAULT_RADIUS_METERS
            const val DEFAULT_ROAMING_DISTANCE_METERS = AppConstants.RoamingConstants.DEFAULT_DISTANCE_METERS
            const val DEFAULT_ROAMING_SPEED_PROFILE_ID = AppConstants.ProfileConstants.DEFAULT_ACTIVE_PROFILE_ID
            const val DEFAULT_ROAMING_FOLLOW_ROADS = AppConstants.RoamingConstants.DEFAULT_FOLLOW_ROADS
            const val DEFAULT_ROAMING_RETURN_TO_START = AppConstants.RoamingConstants.DEFAULT_RETURN_TO_START

            const val DEFAULT_JITTER_IDLE_RADIUS_METERS = AppConstants.JitterConstants.DEFAULT_IDLE_RADIUS_METERS
            const val DEFAULT_JITTER_MOVING_RADIUS_METERS = AppConstants.JitterConstants.DEFAULT_MOVING_RADIUS_METERS
            const val MAX_JITTER_RADIUS_METERS = AppConstants.JitterConstants.MAX_RADIUS_METERS
            const val DEFAULT_JITTER_INTERVAL_SECONDS = AppConstants.JitterConstants.DEFAULT_INTERVAL_SECONDS
            const val MIN_JITTER_INTERVAL_SECONDS = AppConstants.JitterConstants.MIN_INTERVAL_SECONDS
            const val MAX_JITTER_INTERVAL_SECONDS = AppConstants.JitterConstants.MAX_INTERVAL_SECONDS
        }
    }

private const val TAG = "AppPreferencesDataSource"

data class SpeedProfilePreferences(
    val walkSpeedMs: Double,
    val runSpeedMs: Double,
    val bikeSpeedMs: Double,
    val activeProfileId: String,
)
