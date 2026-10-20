package com.locationjoystick.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.locationjoystick.core.database.dao.FavoriteDao
import com.locationjoystick.core.database.dao.RouteDao
import com.locationjoystick.core.database.dao.WaypointDao
import com.locationjoystick.core.database.entities.FavoriteEntity
import com.locationjoystick.core.database.entities.RouteEntity
import com.locationjoystick.core.database.entities.WaypointEntity

@Database(
    entities = [
        RouteEntity::class,
        WaypointEntity::class,
        FavoriteEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class LjDatabase : RoomDatabase() {
    abstract fun routeDao(): RouteDao

    abstract fun waypointDao(): WaypointDao

    abstract fun favoriteDao(): FavoriteDao

    companion object {
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE routes ADD COLUMN routeType TEXT NOT NULL DEFAULT 'STRAIGHT'")
                }
            }

        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE favorites ADD COLUMN category TEXT")
                }
            }

        val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE routes ADD COLUMN speedProfileId TEXT")
                }
            }

        val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE waypoints ADD COLUMN waitSeconds INTEGER NOT NULL DEFAULT 0")
                }
            }

        val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE routes ADD COLUMN randomizeTeleportOrder INTEGER NOT NULL DEFAULT 0")
                }
            }

        val MIGRATION_6_7 =
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE routes ADD COLUMN category TEXT")
                }
            }

        val MIGRATION_7_8 =
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE routes ADD COLUMN plantingRadiusMeters REAL")
                }
            }

        val ALL_MIGRATIONS: Array<Migration> =
            arrayOf(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
            )
    }
}
