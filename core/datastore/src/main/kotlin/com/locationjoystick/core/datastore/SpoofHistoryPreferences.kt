package com.locationjoystick.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.SpoofHistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToLong

interface SpoofHistoryPreferences {
    /** Gets the teleport history, newest first. */
    fun getSpoofHistory(): Flow<List<SpoofHistoryEntry>>

    /** Records a teleport. A match within about 1 m is replaced; the list keeps the newest entries only. */
    suspend fun addSpoofHistory(
        lat: Double,
        lon: Double,
        atMs: Long,
    )

    /** Removes every history entry. */
    suspend fun clearSpoofHistory()
}

internal class SpoofHistoryStore(
    private val dataStore: DataStore<Preferences>,
) : SpoofHistoryPreferences {
    override fun getSpoofHistory(): Flow<List<SpoofHistoryEntry>> =
        dataStore.safeData("spoof history").map { prefs -> parseSpoofHistory(prefs[Keys.SPOOF_HISTORY]) }

    override suspend fun addSpoofHistory(
        lat: Double,
        lon: Double,
        atMs: Long,
    ) {
        dataStore.edit { prefs ->
            val updated =
                (listOf(SpoofHistoryEntry(lat, lon, atMs)) + parseSpoofHistory(prefs[Keys.SPOOF_HISTORY]))
                    .distinctBy { it.lat.toMicroDegrees() to it.lon.toMicroDegrees() }
                    .take(AppConstants.ProfileConstants.SPOOF_HISTORY_MAX_COUNT)
            prefs[Keys.SPOOF_HISTORY] = serializeSpoofHistory(updated)
        }
    }

    override suspend fun clearSpoofHistory() {
        dataStore.edit { prefs -> prefs.remove(Keys.SPOOF_HISTORY) }
    }
}

// 1e5 degrees is about 1 m: pasted links differ in trailing digits for the same spot.
private fun Double.toMicroDegrees(): Long = (this * 1e5).roundToLong()

private fun serializeSpoofHistory(entries: List<SpoofHistoryEntry>): String {
    val array = JSONArray()
    entries.forEach { e ->
        array.put(
            JSONObject().apply {
                put("lat", e.lat)
                put("lon", e.lon)
                put("atMs", e.atMs)
            },
        )
    }
    return array.toString()
}

private fun parseSpoofHistory(raw: String?): List<SpoofHistoryEntry> {
    if (raw == null) return emptyList()
    return try {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { i ->
            try {
                val obj = array.getJSONObject(i)
                SpoofHistoryEntry(obj.getDouble("lat"), obj.getDouble("lon"), obj.getLong("atMs"))
            } catch (e: Exception) {
                null
            }
        }
    } catch (e: Exception) {
        emptyList()
    }
}
