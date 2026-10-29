package com.locationjoystick.core.datastore

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

internal const val TAG = "AppPreferencesDataSource"

internal fun DataStore<Preferences>.safeData(what: String): Flow<Preferences> =
    data.catch { e ->
        if (e is IOException) {
            Log.e(TAG, "Error reading $what", e)
            emit(emptyPreferences())
        } else {
            throw e
        }
    }

internal fun <T, R> DataStore<Preferences>.pref(
    key: Preferences.Key<T>,
    default: T,
    transform: (T) -> R,
): Flow<R> = safeData("preference '${key.name}'").map { prefs -> transform(prefs[key] ?: default) }

internal fun <T> DataStore<Preferences>.pref(
    key: Preferences.Key<T>,
    default: T,
): Flow<T> = pref(key, default) { it }

internal suspend fun <T> DataStore<Preferences>.setPref(
    key: Preferences.Key<T>,
    value: T,
) {
    edit { prefs -> prefs[key] = value }
}
