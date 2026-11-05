package com.locationjoystick.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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

    // Schema 6 shipped in 0.27.0; older schemas were proven by earlier releases.
    @Test
    fun rows_saved_by_0_27_0_survive_migration_to_latest() {
        helper.createDatabase(DB_NAME, 6).apply {
            execSQL(
                "INSERT INTO routes (id, name, isLooping, routeType, randomizeTeleportOrder, createdAt, updatedAt) " +
                    "VALUES ('r1', 'Route', 1, 'STRAIGHT', 0, 1, 2)",
            )
            execSQL(
                "INSERT INTO waypoints (id, routeId, latitude, longitude, orderIndex, waitSeconds) VALUES ('w1', 'r1', 48.8, 2.3, 0, 0)",
            )
            execSQL("INSERT INTO favorites (id, name, latitude, longitude, createdAt) VALUES ('f1', 'Fav', 48.8, 2.3, 1)")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB_NAME, 8, true, *LjDatabase.ALL_MIGRATIONS)
        assertEquals(1, db.count("routes"))
        assertEquals(1, db.count("waypoints"))
        assertEquals(1, db.count("favorites"))
        db.close()
    }

    private fun SupportSQLiteDatabase.count(table: String): Int =
        query("SELECT COUNT(*) FROM $table").use {
            it.moveToFirst()
            it.getInt(0)
        }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
