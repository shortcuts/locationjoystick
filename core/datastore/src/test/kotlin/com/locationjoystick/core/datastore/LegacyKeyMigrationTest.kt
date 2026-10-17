package com.locationjoystick.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LegacyKeyMigrationTest {
    @get:Rule val tmp = TemporaryFolder()

    private val oldChoice = stringPreferencesKey("compass_disclosure_choice")
    private val newChoice = stringPreferencesKey("accessibility_disclosure_choice")

    // The seeding store must be closed before the migrating one opens the same file.
    private suspend fun seed(
        file: java.io.File,
        block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit,
    ) {
        val scope = CoroutineScope(Job())
        PreferenceDataStoreFactory.create(scope = scope) { file }.edit(block)
        scope.cancel()
    }

    @Test
    fun `old key becomes the new key and vanishes`() =
        runTest {
            val file = tmp.newFolder().resolve("p.preferences_pb")
            seed(file) {
                it[oldChoice] = "accepted"
            }
            // Same file, fresh store with the migration: what the app does on first launch after update.
            val migrated =
                PreferenceDataStoreFactory
                    .create(scope = backgroundScope, migrations = listOf(LegacyKeyMigration)) { file }
                    .data
                    .first()
            assertEquals("accepted", migrated[newChoice])
            assertNull(migrated[oldChoice])
        }

    @Test
    fun `existing new key wins over the old one`() =
        runTest {
            val file = tmp.newFolder().resolve("p.preferences_pb")
            seed(file) {
                it[oldChoice] = "declined"
                it[newChoice] = "accepted"
            }
            val migrated =
                PreferenceDataStoreFactory
                    .create(scope = backgroundScope, migrations = listOf(LegacyKeyMigration)) { file }
                    .data
                    .first()
            assertEquals("accepted", migrated[newChoice])
            assertNull(migrated[oldChoice])
        }
}
