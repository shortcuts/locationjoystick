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
    version = 2,
    exportSchema = true,
)
abstract class LjDatabase : RoomDatabase() {
    abstract fun routeDao(): RouteDao

    abstract fun waypointDao(): WaypointDao

    abstract fun favoriteDao(): FavoriteDao

    companion object {
        const val DATABASE_NAME = "locationjoystick.db"

        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE routes ADD COLUMN routeType TEXT NOT NULL DEFAULT 'STRAIGHT'")
                }
            }
    }
}
