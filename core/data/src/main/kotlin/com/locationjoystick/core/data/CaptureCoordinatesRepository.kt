package com.locationjoystick.core.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.appendCapturedPoint
import com.locationjoystick.core.model.LatLng
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaptureCoordinatesRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        private object Keys {
            val CAPTURE_MODE_ENABLED = booleanPreferencesKey(AppConstants.DataStoreConstants.KEY_CAPTURE_MODE_ENABLED)
            val CAPTURE_ENABLED = booleanPreferencesKey(AppConstants.DataStoreConstants.KEY_CAPTURE_ENABLED)
            val JUMP_ENABLED = booleanPreferencesKey(AppConstants.DataStoreConstants.KEY_CAPTURE_JUMP_ENABLED)
            val CAPTURE_POINTS = stringPreferencesKey(AppConstants.DataStoreConstants.KEY_CAPTURE_POINTS)
            val PREVIOUS_BROWSER = stringPreferencesKey(AppConstants.DataStoreConstants.KEY_CAPTURE_PREVIOUS_BROWSER)
            val HELPER_OPEN = booleanPreferencesKey(AppConstants.DataStoreConstants.KEY_CAPTURE_HELPER_OPEN)
        }

        /** Shared read path: swallow [IOException] (e.g. disk failure) as empty preferences. */
        private val safeData: Flow<Preferences> =
            dataStore.data
                .catch { e ->
                    if (e is IOException) {
                        Log.e(TAG, "Error reading capture coordinates preferences", e)
                        emit(emptyPreferences())
                    } else {
                        throw e
                    }
                }

        private fun <T> pref(
            key: Preferences.Key<T>,
            default: T,
        ): Flow<T> = safeData.map { prefs -> prefs[key] ?: default }

        /** List action. The key predates the separate overall capture-mode switch. */
        val captureEnabled: Flow<Boolean> =
            pref(Keys.CAPTURE_ENABLED, false)

        val jumpEnabled: Flow<Boolean> =
            pref(Keys.JUMP_ENABLED, false)

        /**
         * Overall intercept mode. Before this key existed, either enabled action meant the mode was on.
         * Preserve that state until the user explicitly changes the new switch.
         */
        val captureModeEnabled: Flow<Boolean> =
            safeData.map { prefs ->
                prefs[Keys.CAPTURE_MODE_ENABLED]
                    ?: ((prefs[Keys.CAPTURE_ENABLED] ?: false) || (prefs[Keys.JUMP_ENABLED] ?: false))
            }

        val points: Flow<List<LatLng>> =
            safeData.map { prefs -> decodePoints(prefs[Keys.CAPTURE_POINTS]) }

        val helperOpen: Flow<Boolean> =
            pref(Keys.HELPER_OPEN, false)

        val previousBrowserPackage: Flow<String?> =
            safeData.map { prefs -> prefs[Keys.PREVIOUS_BROWSER]?.takeIf { it.isNotBlank() } }

        suspend fun setCaptureEnabled(enabled: Boolean) {
            dataStore.edit { prefs -> prefs[Keys.CAPTURE_ENABLED] = enabled }
        }

        suspend fun setCaptureModeEnabled(enabled: Boolean) {
            dataStore.edit { prefs -> prefs[Keys.CAPTURE_MODE_ENABLED] = enabled }
        }

        suspend fun setJumpEnabled(enabled: Boolean) {
            dataStore.edit { prefs -> prefs[Keys.JUMP_ENABLED] = enabled }
        }

        suspend fun appendPoint(point: LatLng) {
            dataStore.edit { prefs ->
                val existing = decodePoints(prefs[Keys.CAPTURE_POINTS])
                prefs[Keys.CAPTURE_POINTS] = encodePoints(appendCapturedPoint(existing, point))
            }
        }

        suspend fun clearPoints() {
            dataStore.edit { prefs -> prefs[Keys.CAPTURE_POINTS] = encodePoints(emptyList()) }
        }

        suspend fun removeLast() {
            dataStore.edit { prefs ->
                val existing = decodePoints(prefs[Keys.CAPTURE_POINTS])
                prefs[Keys.CAPTURE_POINTS] = encodePoints(existing.dropLast(1))
            }
        }

        suspend fun setHelperOpen(open: Boolean) {
            dataStore.edit { prefs -> prefs[Keys.HELPER_OPEN] = open }
        }

        suspend fun setPreviousBrowserPackage(packageName: String) {
            val trimmed = packageName.trim()
            if (trimmed.isEmpty()) return
            dataStore.edit { prefs -> prefs[Keys.PREVIOUS_BROWSER] = trimmed }
        }

        companion object {
            private const val TAG = "CaptureCoordsRepo"

            internal fun encodePoints(points: List<LatLng>): String {
                val arr = JSONArray()
                for (point in points) {
                    arr.put(
                        JSONObject()
                            .put("lat", point.latitude)
                            .put("lon", point.longitude),
                    )
                }
                return arr.toString()
            }

            internal fun decodePoints(raw: String?): List<LatLng> {
                if (raw.isNullOrBlank()) return emptyList()
                return runCatching {
                    val arr = JSONArray(raw)
                    buildList {
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val lat = obj.getDouble("lat")
                            val lon = obj.getDouble("lon")
                            if (lat in -90.0..90.0 && lon in -180.0..180.0) {
                                add(LatLng(lat, lon))
                            }
                        }
                    }
                }.getOrDefault(emptyList())
            }
        }
    }
