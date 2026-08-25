package com.locationjoystick.core.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [DataStore]<[Preferences]> for tests — no disk I/O, no Android Context needed. */
class FakePreferencesDataStore : DataStore<Preferences> {
    private val flow = MutableStateFlow<Preferences>(emptyPreferences())

    override val data: Flow<Preferences> = flow

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(flow.value)
        flow.value = updated
        return updated
    }

    /** Writes a raw string key, bypassing any typed setter — used to simulate a malformed
     * or legacy stored value. */
    fun writeRaw(
        key: String,
        value: String,
    ) {
        val mutable = flow.value.toMutablePreferences()
        mutable[stringPreferencesKey(key)] = value
        flow.value = mutable
    }
}
