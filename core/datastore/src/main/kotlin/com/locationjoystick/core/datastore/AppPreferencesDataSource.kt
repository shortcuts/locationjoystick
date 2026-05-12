package com.locationjoystick.core.datastore

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.locationjoystick.core.model.SpeedProfile
import com.locationjoystick.core.model.WidgetFeature
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferencesDataSource
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        private object Keys {
            val WALK_SPEED_MS = doublePreferencesKey("walk_speed_ms")
            val RUN_SPEED_MS = doublePreferencesKey("run_speed_ms")
            val BIKE_SPEED_MS = doublePreferencesKey("bike_speed_ms")
            val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
            val WIDGET_ITEMS = stringSetPreferencesKey("widget_items")
            val ROAMING_RADIUS_METERS = doublePreferencesKey("roaming_radius_meters")
            val ROAMING_DURATION_SECONDS = doublePreferencesKey("roaming_duration_seconds")
            val ROAMING_ROAD_FOLLOWING = booleanPreferencesKey("roaming_road_following")
            val ROAMING_TRANSPORT_MODE = stringPreferencesKey("roaming_transport_mode")
            val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
            val SPEED_UNIT = stringPreferencesKey("speed_unit")
        }

        fun getSpeedProfiles(): Flow<SpeedProfilePreferences> =
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

        suspend fun setWalkSpeed(ms: Double) {
            dataStore.edit { prefs -> prefs[Keys.WALK_SPEED_MS] = ms.coerceIn(MIN_SPEED_MS, MAX_SPEED_MS) }
        }

        suspend fun setRunSpeed(ms: Double) {
            dataStore.edit { prefs -> prefs[Keys.RUN_SPEED_MS] = ms.coerceIn(MIN_SPEED_MS, MAX_SPEED_MS) }
        }

        suspend fun setBikeSpeed(ms: Double) {
            dataStore.edit { prefs -> prefs[Keys.BIKE_SPEED_MS] = ms.coerceIn(MIN_SPEED_MS, MAX_SPEED_MS) }
        }

        suspend fun setActiveProfileId(profileId: String) {
            dataStore.edit { prefs -> prefs[Keys.ACTIVE_PROFILE_ID] = profileId }
        }

        fun getWidgetItems(): Flow<Set<String>> =
            dataStore.data
                .catch { e ->
                    if (e is IOException) {
                        Log.e(TAG, "Error reading widget preferences", e)
                        emit(emptyPreferences())
                    } else {
                        throw e
                    }
                }.map { prefs -> prefs[Keys.WIDGET_ITEMS] ?: DEFAULT_WIDGET_ITEMS }

        suspend fun setWidgetItems(items: Set<String>) {
            dataStore.edit { prefs -> prefs[Keys.WIDGET_ITEMS] = items }
        }

        fun getRoamingConfig(): Flow<RoamingPreferences> =
            dataStore.data
                .catch { e ->
                    if (e is IOException) {
                        Log.e(TAG, "Error reading roaming preferences", e)
                        emit(emptyPreferences())
                    } else {
                        throw e
                    }
                }.map { prefs ->
                    RoamingPreferences(
                        radiusMeters = prefs[Keys.ROAMING_RADIUS_METERS] ?: DEFAULT_ROAMING_RADIUS_METERS,
                        durationSeconds = (prefs[Keys.ROAMING_DURATION_SECONDS] ?: DEFAULT_ROAMING_DURATION_SECONDS).toLong(),
                        roadFollowing = prefs[Keys.ROAMING_ROAD_FOLLOWING] ?: false,
                        transportMode = prefs[Keys.ROAMING_TRANSPORT_MODE] ?: DEFAULT_ROAMING_TRANSPORT_MODE,
                    )
                }

        suspend fun setRoamingConfig(
            radiusMeters: Double,
            durationSeconds: Long,
            roadFollowing: Boolean,
            transportMode: String,
        ) {
            dataStore.edit { prefs ->
                prefs[Keys.ROAMING_RADIUS_METERS] = radiusMeters
                prefs[Keys.ROAMING_DURATION_SECONDS] = durationSeconds.toDouble()
                prefs[Keys.ROAMING_ROAD_FOLLOWING] = roadFollowing
                prefs[Keys.ROAMING_TRANSPORT_MODE] = transportMode
            }
        }

        fun getOnboardingComplete(): Flow<Boolean> =
            dataStore.data
                .catch { e ->
                    if (e is IOException) {
                        Log.e(TAG, "Error reading onboarding preferences", e)
                        emit(emptyPreferences())
                    } else {
                        throw e
                    }
                }.map { prefs -> prefs[Keys.ONBOARDING_COMPLETE] ?: false }

        suspend fun setOnboardingComplete(complete: Boolean) {
            dataStore.edit { prefs -> prefs[Keys.ONBOARDING_COMPLETE] = complete }
        }

        fun getSpeedUnit(): Flow<String> =
            dataStore.data
                .catch { e ->
                    if (e is IOException) {
                        Log.e(TAG, "Error reading speed unit preference", e)
                        emit(emptyPreferences())
                    } else {
                        throw e
                    }
                }.map { prefs -> prefs[Keys.SPEED_UNIT] ?: "KMH" }

        suspend fun setSpeedUnit(unit: String) {
            dataStore.edit { prefs -> prefs[Keys.SPEED_UNIT] = unit }
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

        companion object {
            const val DATASTORE_FILE_NAME = "app_preferences"

            const val DEFAULT_WALK_SPEED_MS = 1.4
            const val DEFAULT_RUN_SPEED_MS = 3.0
            const val DEFAULT_BIKE_SPEED_MS = 5.5
            const val DEFAULT_ACTIVE_PROFILE_ID = "walk"

            const val MIN_SPEED_MS = 0.1
            const val MAX_SPEED_MS = 15.0

            val DEFAULT_WIDGET_ITEMS: Set<String> =
                setOf(
                    WidgetFeature.JOYSTICK_TOGGLE.name.lowercase(),
                    WidgetFeature.SPEED_CYCLE.name.lowercase(),
                )

            const val DEFAULT_ROAMING_RADIUS_METERS = 2000.0
            const val DEFAULT_ROAMING_DURATION_SECONDS = 1800.0
            const val DEFAULT_ROAMING_TRANSPORT_MODE = "walk"
        }
    }

private const val TAG = "AppPreferencesDataSource"

data class SpeedProfilePreferences(
    val walkSpeedMs: Double,
    val runSpeedMs: Double,
    val bikeSpeedMs: Double,
    val activeProfileId: String,
)

data class RoamingPreferences(
    val radiusMeters: Double,
    val durationSeconds: Long,
    val roadFollowing: Boolean,
    val transportMode: String,
)
