package com.locationjoystick.core.datastore

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Moves the two values that were stored under compass-era key names to their current keys, so a
 * saved disclosure choice and picked game survive the rename. A value already under the new key
 * wins; the old key is always dropped.
 */
object LegacyKeyMigration : DataMigration<Preferences> {
    private val renames =
        listOf(
            stringPreferencesKey("compass_disclosure_choice") to stringPreferencesKey("accessibility_disclosure_choice"),
            stringPreferencesKey("compass_test_target_package") to stringPreferencesKey("tap_to_walk_game_package"),
        )

    override suspend fun shouldMigrate(currentData: Preferences): Boolean = renames.any { (old, _) -> old in currentData }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs: MutablePreferences = currentData.toMutablePreferences()
        for ((old, new) in renames) {
            val value = prefs[old] ?: continue
            if (new !in prefs) prefs[new] = value
            prefs.remove(old)
        }
        return prefs
    }

    override suspend fun cleanUp() = Unit
}
