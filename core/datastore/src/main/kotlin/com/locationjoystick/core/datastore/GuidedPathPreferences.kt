package com.locationjoystick.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.locationjoystick.core.model.GuidedPath
import kotlinx.coroutines.flow.Flow

interface GuidedPathPreferences {
    fun getGuidedPath(): Flow<String>

    suspend fun setGuidedPath(name: String)
}

internal class GuidedPathStore(
    private val dataStore: DataStore<Preferences>,
) : GuidedPathPreferences {
    override fun getGuidedPath(): Flow<String> = dataStore.pref(Keys.GUIDED_PATH, GuidedPath.DEFAULT.name)

    override suspend fun setGuidedPath(name: String) = dataStore.setPref(Keys.GUIDED_PATH, name)
}
