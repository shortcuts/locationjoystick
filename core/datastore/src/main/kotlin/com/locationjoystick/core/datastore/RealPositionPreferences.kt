package com.locationjoystick.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RealPositionSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface RealPositionPreferences {
    /** Gets whether the last known real position is pinned to the favorites list. */
    fun getLastKnownPositionEnabled(): Flow<Boolean>

    /** Sets whether the last known real position is pinned to the favorites list. */
    suspend fun setLastKnownPositionEnabled(enabled: Boolean)

    /** Gets the real position saved at the last fresh spoofing start, or null if none. */
    fun getRealPositionSnapshot(): Flow<RealPositionSnapshot?>

    /** Replaces the saved real position. */
    suspend fun setRealPositionSnapshot(snapshot: RealPositionSnapshot)
}

internal class RealPositionStore(
    private val dataStore: DataStore<Preferences>,
) : RealPositionPreferences {
    override fun getLastKnownPositionEnabled(): Flow<Boolean> =
        dataStore.pref(Keys.LAST_KNOWN_POSITION_ENABLED, AppConstants.ProfileConstants.LAST_KNOWN_POSITION_ENABLED_DEFAULT)

    override suspend fun setLastKnownPositionEnabled(enabled: Boolean) = dataStore.setPref(Keys.LAST_KNOWN_POSITION_ENABLED, enabled)

    override fun getRealPositionSnapshot(): Flow<RealPositionSnapshot?> =
        dataStore.safeData("real position snapshot").map { prefs ->
            val lat = prefs[Keys.REAL_SNAPSHOT_LATITUDE]
            val lon = prefs[Keys.REAL_SNAPSHOT_LONGITUDE]
            val takenAt = prefs[Keys.REAL_SNAPSHOT_TAKEN_AT_MS]
            if (lat != null && lon != null && takenAt != null) RealPositionSnapshot(LatLng(lat, lon), takenAt) else null
        }

    override suspend fun setRealPositionSnapshot(snapshot: RealPositionSnapshot) {
        dataStore.edit { prefs ->
            prefs[Keys.REAL_SNAPSHOT_LATITUDE] = snapshot.position.latitude
            prefs[Keys.REAL_SNAPSHOT_LONGITUDE] = snapshot.position.longitude
            prefs[Keys.REAL_SNAPSHOT_TAKEN_AT_MS] = snapshot.takenAtMs
        }
    }
}
