package com.locationjoystick.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow

interface ReconnectPreferences {
    fun getReconnectTapEnabled(): Flow<Boolean>

    suspend fun setReconnectTapEnabled(enabled: Boolean)
}

internal class ReconnectStore(
    private val dataStore: DataStore<Preferences>,
) : ReconnectPreferences {
    override fun getReconnectTapEnabled(): Flow<Boolean> = dataStore.pref(Keys.RECONNECT_TAP_ENABLED, false)

    override suspend fun setReconnectTapEnabled(enabled: Boolean) = dataStore.setPref(Keys.RECONNECT_TAP_ENABLED, enabled)
}
