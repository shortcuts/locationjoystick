package com.locationjoystick.core.datastore

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RecentSearch
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.SpeedProfile
import com.locationjoystick.core.model.WidgetFeature
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface for accessing app preferences stored in DataStore.
 *
 * This abstraction allows for easy testing and potential future migration
 * from DataStore to another storage mechanism.
 *
 * Keys are defined in [AppConstants.DataStoreConstants].
 */
interface PreferencesDataSource {
    /** Gets the current speed profiles (walk/run/bike speeds and active profile ID). */
    fun getSpeedProfiles(): Flow<SpeedProfilePreferences>

    /** Sets the walk speed in meters per second. */
    suspend fun setWalkSpeed(ms: Double)

    /** Sets the run speed in meters per second. */
    suspend fun setRunSpeed(ms: Double)

    /** Sets the bike speed in meters per second. */
    suspend fun setBikeSpeed(ms: Double)

    /** Sets the active speed profile ID (walk/run/bike). */
    suspend fun setActiveProfileId(profileId: String)

    /** Gets the set of enabled widget feature keys. */
    fun getWidgetItems(): Flow<Set<String>>

    /** Sets the enabled widget feature keys. */
    suspend fun setWidgetItems(items: Set<String>)

    /** Gets the default roaming configuration. */
    fun getRoamingDefaults(): Flow<RoamingDefaults>

    /** Updates the roaming defaults. */
    suspend fun updateRoamingDefaults(defaults: RoamingDefaults)

    /** Gets whether onboarding has been completed. */
    fun getOnboardingComplete(): Flow<Boolean>

    /** Sets whether onboarding has been completed. */
    suspend fun setOnboardingComplete(complete: Boolean)

    /** Gets the speed unit preference (KMH/MPH). */
    fun getSpeedUnit(): Flow<String>

    /** Sets the speed unit preference. */
    suspend fun setSpeedUnit(unit: String)

    /** Gets whether to remember the last spoofed location. */
    fun getRememberLastLocation(): Flow<Boolean>

    /** Sets whether to remember the last spoofed location. */
    suspend fun setRememberLastLocation(enabled: Boolean)

    /** Gets the last spoofed location (for restore on app restart). */
    fun getLastLocation(): Flow<LatLng?>

    /** Sets the last spoofed location. */
    suspend fun setLastLocation(location: LatLng)

    /** Gets the GPS jitter radius when idle (meters). */
    fun getJitterIdleRadius(): Flow<Double>

    /** Gets the GPS jitter radius when moving (meters). */
    fun getJitterMovingRadius(): Flow<Double>

    /** Gets the GPS jitter update interval (seconds). */
    fun getJitterIntervalSeconds(): Flow<Int>

    /** Sets the GPS jitter radius when idle. */
    suspend fun setJitterIdleRadius(meters: Double)

    /** Sets the GPS jitter radius when moving. */
    suspend fun setJitterMovingRadius(meters: Double)

    /** Sets the GPS jitter update interval. */
    suspend fun setJitterIntervalSeconds(seconds: Int)

    /** Gets the GPS jitter update interval when idle (seconds). */
    fun getJitterIdleIntervalSeconds(): Flow<Int>

    /** Sets the GPS jitter update interval when idle. */
    suspend fun setJitterIdleIntervalSeconds(seconds: Int)

    /** Gets the timestamp (epoch ms) of the last teleport action. */
    fun getLastTeleportTime(): Flow<Long>

    /** Sets the timestamp (epoch ms) of the last teleport action. */
    suspend fun setLastTeleportTime(ms: Long)

    /** Gets whether the map camera should follow the spoofed location marker. */
    fun getMapFollowsLocation(): Flow<Boolean>

    /** Sets whether the map camera should follow the spoofed location marker. */
    suspend fun setMapFollowsLocation(enabled: Boolean)

    fun getRealismBearingHoldIdle(): Flow<Boolean>

    fun getRealismAltitudeEnabled(): Flow<Boolean>

    fun getRealismWarmupEnabled(): Flow<Boolean>

    fun getRealismSatelliteExtrasEnabled(): Flow<Boolean>

    fun getRealismSuspendedMockingEnabled(): Flow<Boolean>

    suspend fun setRealismBearingHoldIdle(enabled: Boolean)

    suspend fun setRealismAltitudeEnabled(enabled: Boolean)

    suspend fun setRealismWarmupEnabled(enabled: Boolean)

    suspend fun setRealismSatelliteExtrasEnabled(enabled: Boolean)

    suspend fun setRealismSuspendedMockingEnabled(enabled: Boolean)

    /** Gets the list of recently searched locations, newest first. */
    fun getRecentSearches(): Flow<List<RecentSearch>>

    /** Prepends a search to the recent list, deduplicates by displayName, and caps at max count. */
    suspend fun addRecentSearch(
        displayName: String,
        lat: Double,
        lon: Double,
    )

    fun getRoutesSortNewestFirst(): Flow<Boolean>

    suspend fun setRoutesSortNewestFirst(newestFirst: Boolean)

    fun getFavoritesSortNewestFirst(): Flow<Boolean>

    suspend fun setFavoritesSortNewestFirst(newestFirst: Boolean)

    /** Gets the GPS jitter idle speed variation percentage (0 = off). */
    fun getJitterSpeedIdleVariationPct(): Flow<Int>

    /** Gets the GPS jitter moving speed variation percentage (0 = off). */
    fun getJitterSpeedMovingVariationPct(): Flow<Int>

    /** Sets the GPS jitter idle speed variation percentage. */
    suspend fun setJitterSpeedIdleVariationPct(pct: Int)

    /** Sets the GPS jitter moving speed variation percentage. */
    suspend fun setJitterSpeedMovingVariationPct(pct: Int)
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
            val JITTER_IDLE_INTERVAL_SECONDS = intPreferencesKey("jitter_idle_interval_seconds")
            val LAST_TELEPORT_TIME_MS = longPreferencesKey("last_teleport_time_ms")
            val MAP_FOLLOWS_LOCATION = booleanPreferencesKey("map_follows_location")
            val REALISM_BEARING_HOLD_IDLE = booleanPreferencesKey("realism_bearing_hold_idle")
            val REALISM_ALTITUDE_ENABLED = booleanPreferencesKey("realism_altitude_enabled")
            val REALISM_WARMUP_ENABLED = booleanPreferencesKey("realism_warmup_enabled")
            val REALISM_SATELLITE_EXTRAS_ENABLED = booleanPreferencesKey("realism_satellite_extras_enabled")
            val REALISM_SUSPENDED_MOCKING_ENABLED = booleanPreferencesKey("realism_suspended_mocking_enabled")
            val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
            val ROUTES_SORT_NEWEST_FIRST = booleanPreferencesKey("routes_sort_newest_first")
            val FAVORITES_SORT_NEWEST_FIRST = booleanPreferencesKey("favorites_sort_newest_first")
            val JITTER_SPEED_IDLE_VARIATION_PCT = intPreferencesKey("jitter_speed_idle_variation_pct")
            val JITTER_SPEED_MOVING_VARIATION_PCT = intPreferencesKey("jitter_speed_moving_variation_pct")
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

        override fun getJitterIdleIntervalSeconds(): Flow<Int> =
            dataStore.data
                .catch { e ->
                    if (e is IOException) {
                        Log.e(TAG, "Error reading jitter idle interval preference", e)
                        emit(emptyPreferences())
                    } else {
                        throw e
                    }
                }.map { prefs -> prefs[Keys.JITTER_IDLE_INTERVAL_SECONDS] ?: DEFAULT_JITTER_IDLE_INTERVAL_SECONDS }

        override suspend fun setJitterIdleIntervalSeconds(seconds: Int) {
            dataStore.edit { prefs ->
                prefs[Keys.JITTER_IDLE_INTERVAL_SECONDS] = seconds.coerceIn(MIN_JITTER_INTERVAL_SECONDS, MAX_JITTER_INTERVAL_SECONDS)
            }
        }

        override fun getLastTeleportTime(): Flow<Long> =
            dataStore.data
                .catch { e ->
                    if (e is IOException) {
                        Log.e(TAG, "Error reading last teleport time preference", e)
                        emit(emptyPreferences())
                    } else {
                        throw e
                    }
                }.map { prefs -> prefs[Keys.LAST_TELEPORT_TIME_MS] ?: AppConstants.DataStoreConstants.DEFAULT_LAST_TELEPORT_TIME_MS }

        override suspend fun setLastTeleportTime(ms: Long) {
            dataStore.edit { prefs -> prefs[Keys.LAST_TELEPORT_TIME_MS] = ms }
        }

        override fun getMapFollowsLocation(): Flow<Boolean> =
            dataStore.data
                .catch { e ->
                    if (e is IOException) {
                        Log.e(TAG, "Error reading map follows location preference", e)
                        emit(emptyPreferences())
                    } else {
                        throw e
                    }
                }.map { prefs -> prefs[Keys.MAP_FOLLOWS_LOCATION] ?: true }

        override suspend fun setMapFollowsLocation(enabled: Boolean) {
            dataStore.edit { prefs -> prefs[Keys.MAP_FOLLOWS_LOCATION] = enabled }
        }

        override fun getRealismBearingHoldIdle(): Flow<Boolean> =
            dataStore.data
                .catch { e ->
                    if (e is IOException) {
                        Log.e(TAG, "Error reading realism bearing hold preference", e)
                        emit(emptyPreferences())
                    } else {
                        throw e
                    }
                }.map { prefs -> prefs[Keys.REALISM_BEARING_HOLD_IDLE] ?: true }

        override fun getRealismAltitudeEnabled(): Flow<Boolean> =
            dataStore.data
                .catch { e ->
                    if (e is IOException) {
                        Log.e(TAG, "Error reading realism altitude preference", e)
                        emit(emptyPreferences())
                    } else {
                        throw e
                    }
                }.map { prefs -> prefs[Keys.REALISM_ALTITUDE_ENABLED] ?: true }

        override fun getRealismWarmupEnabled(): Flow<Boolean> =
            dataStore.data
                .catch { e ->
                    if (e is IOException) {
                        Log.e(TAG, "Error reading realism warmup preference", e)
                        emit(emptyPreferences())
                    } else {
                        throw e
                    }
                }.map { prefs -> prefs[Keys.REALISM_WARMUP_ENABLED] ?: false }

        override fun getRealismSatelliteExtrasEnabled(): Flow<Boolean> =
            dataStore.data
                .catch { e ->
                    if (e is IOException) {
                        Log.e(TAG, "Error reading realism satellite preference", e)
                        emit(emptyPreferences())
                    } else {
                        throw e
                    }
                }.map { prefs -> prefs[Keys.REALISM_SATELLITE_EXTRAS_ENABLED] ?: true }

        override fun getRealismSuspendedMockingEnabled(): Flow<Boolean> =
            dataStore.data
                .catch { e ->
                    if (e is IOException) {
                        Log.e(TAG, "Error reading realism suspended mocking preference", e)
                        emit(emptyPreferences())
                    } else {
                        throw e
                    }
                }.map { prefs -> prefs[Keys.REALISM_SUSPENDED_MOCKING_ENABLED] ?: false }

        override suspend fun setRealismBearingHoldIdle(enabled: Boolean) {
            dataStore.edit { prefs -> prefs[Keys.REALISM_BEARING_HOLD_IDLE] = enabled }
        }

        override suspend fun setRealismAltitudeEnabled(enabled: Boolean) {
            dataStore.edit { prefs -> prefs[Keys.REALISM_ALTITUDE_ENABLED] = enabled }
        }

        override suspend fun setRealismWarmupEnabled(enabled: Boolean) {
            dataStore.edit { prefs -> prefs[Keys.REALISM_WARMUP_ENABLED] = enabled }
        }

        override suspend fun setRealismSatelliteExtrasEnabled(enabled: Boolean) {
            dataStore.edit { prefs -> prefs[Keys.REALISM_SATELLITE_EXTRAS_ENABLED] = enabled }
        }

        override suspend fun setRealismSuspendedMockingEnabled(enabled: Boolean) {
            dataStore.edit { prefs -> prefs[Keys.REALISM_SUSPENDED_MOCKING_ENABLED] = enabled }
        }

        override fun getRecentSearches(): Flow<List<RecentSearch>> =
            dataStore.data
                .catch { e ->
                    if (e is IOException) {
                        Log.e(TAG, "Error reading recent searches", e)
                        emit(emptyPreferences())
                    } else {
                        throw e
                    }
                }.map { prefs ->
                    val raw = prefs[Keys.RECENT_SEARCHES] ?: return@map emptyList()
                    deserializeRecentSearches(raw)
                }

        override suspend fun addRecentSearch(
            displayName: String,
            lat: Double,
            lon: Double,
        ) {
            dataStore.edit { prefs ->
                val current = deserializeRecentSearches(prefs[Keys.RECENT_SEARCHES] ?: "[]")
                val updated =
                    (listOf(RecentSearch(displayName, lat, lon)) + current)
                        .distinctBy { it.displayName }
                        .take(AppConstants.NominatimConstants.RECENT_SEARCHES_MAX_COUNT)
                prefs[Keys.RECENT_SEARCHES] = serializeRecentSearches(updated)
            }
        }

        override fun getRoutesSortNewestFirst(): Flow<Boolean> =
            dataStore.data
                .catch { e ->
                    if (e is IOException) {
                        Log.e(TAG, "Error reading routes sort preference", e)
                        emit(emptyPreferences())
                    } else {
                        throw e
                    }
                }.map { prefs -> prefs[Keys.ROUTES_SORT_NEWEST_FIRST] ?: true }

        override suspend fun setRoutesSortNewestFirst(newestFirst: Boolean) {
            dataStore.edit { prefs -> prefs[Keys.ROUTES_SORT_NEWEST_FIRST] = newestFirst }
        }

        override fun getFavoritesSortNewestFirst(): Flow<Boolean> =
            dataStore.data
                .catch { e ->
                    if (e is IOException) {
                        Log.e(TAG, "Error reading favorites sort preference", e)
                        emit(emptyPreferences())
                    } else {
                        throw e
                    }
                }.map { prefs -> prefs[Keys.FAVORITES_SORT_NEWEST_FIRST] ?: true }

        override suspend fun setFavoritesSortNewestFirst(newestFirst: Boolean) {
            dataStore.edit { prefs -> prefs[Keys.FAVORITES_SORT_NEWEST_FIRST] = newestFirst }
        }

        override fun getJitterSpeedIdleVariationPct(): Flow<Int> =
            dataStore.data
                .catch { e ->
                    if (e is IOException) {
                        Log.e(TAG, "Error reading jitter idle speed variation preference", e)
                        emit(emptyPreferences())
                    } else {
                        throw e
                    }
                }.map { prefs -> prefs[Keys.JITTER_SPEED_IDLE_VARIATION_PCT] ?: DEFAULT_JITTER_SPEED_IDLE_VARIATION_PCT }

        override fun getJitterSpeedMovingVariationPct(): Flow<Int> =
            dataStore.data
                .catch { e ->
                    if (e is IOException) {
                        Log.e(TAG, "Error reading jitter moving speed variation preference", e)
                        emit(emptyPreferences())
                    } else {
                        throw e
                    }
                }.map { prefs -> prefs[Keys.JITTER_SPEED_MOVING_VARIATION_PCT] ?: DEFAULT_JITTER_SPEED_MOVING_VARIATION_PCT }

        override suspend fun setJitterSpeedIdleVariationPct(pct: Int) {
            dataStore.edit { prefs ->
                prefs[Keys.JITTER_SPEED_IDLE_VARIATION_PCT] =
                    pct.coerceIn(
                        AppConstants.JitterConstants.SPEED_VARIATION_PCT_MIN,
                        AppConstants.JitterConstants.SPEED_VARIATION_PCT_MAX,
                    )
            }
        }

        override suspend fun setJitterSpeedMovingVariationPct(pct: Int) {
            dataStore.edit { prefs ->
                prefs[Keys.JITTER_SPEED_MOVING_VARIATION_PCT] =
                    pct.coerceIn(
                        AppConstants.JitterConstants.SPEED_VARIATION_PCT_MIN,
                        AppConstants.JitterConstants.SPEED_VARIATION_PCT_MAX,
                    )
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
            const val DEFAULT_JITTER_INTERVAL_SECONDS = AppConstants.JitterConstants.DEFAULT_MOVING_INTERVAL_SECONDS
            const val DEFAULT_JITTER_IDLE_INTERVAL_SECONDS = AppConstants.JitterConstants.DEFAULT_IDLE_INTERVAL_SECONDS
            const val MIN_JITTER_INTERVAL_SECONDS = AppConstants.JitterConstants.MIN_INTERVAL_SECONDS
            const val MAX_JITTER_INTERVAL_SECONDS = AppConstants.JitterConstants.MAX_INTERVAL_SECONDS

            const val DEFAULT_JITTER_SPEED_IDLE_VARIATION_PCT = AppConstants.JitterConstants.SPEED_IDLE_VARIATION_PCT_DEFAULT
            const val DEFAULT_JITTER_SPEED_MOVING_VARIATION_PCT = AppConstants.JitterConstants.SPEED_MOVING_VARIATION_PCT_DEFAULT
        }
    }

private const val TAG = "AppPreferencesDataSource"

private fun serializeRecentSearches(searches: List<RecentSearch>): String {
    val array = JSONArray()
    searches.forEach { search ->
        array.put(
            JSONObject().apply {
                put("displayName", search.displayName)
                put("lat", search.lat)
                put("lon", search.lon)
            },
        )
    }
    return array.toString()
}

private fun deserializeRecentSearches(raw: String): List<RecentSearch> =
    try {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { i ->
            try {
                val obj = array.getJSONObject(i)
                RecentSearch(
                    displayName = obj.getString("displayName"),
                    lat = obj.getDouble("lat"),
                    lon = obj.getDouble("lon"),
                )
            } catch (e: Exception) {
                null
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

data class SpeedProfilePreferences(
    val walkSpeedMs: Double,
    val runSpeedMs: Double,
    val bikeSpeedMs: Double,
    val activeProfileId: String,
)
