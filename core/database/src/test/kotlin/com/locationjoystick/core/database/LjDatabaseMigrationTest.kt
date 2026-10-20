package com.locationjoystick.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LjDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LjDatabase::class.java)

    @Test
    fun migrate1To8() {
        helper.createDatabase(DB_NAME, 1).close()
        helper
            .runMigrationsAndValidate(
                DB_NAME,
                8,
                true,
                *LjDatabase.ALL_MIGRATIONS,
            ).close()
    }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
