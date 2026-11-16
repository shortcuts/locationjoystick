package com.locationjoystick.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow

interface ExternalTeleportPreferences {
    fun getExternalTeleportEnabled(): Flow<Boolean>

    suspend fun setExternalTeleportEnabled(enabled: Boolean)
}

internal class ExternalTeleportStore(
    private val dataStore: DataStore<Preferences>,
) : ExternalTeleportPreferences {
    override fun getExternalTeleportEnabled(): Flow<Boolean> = dataStore.pref(Keys.EXTERNAL_TELEPORT_ENABLED, false)

    override suspend fun setExternalTeleportEnabled(enabled: Boolean) = dataStore.setPref(Keys.EXTERNAL_TELEPORT_ENABLED, enabled)
}
