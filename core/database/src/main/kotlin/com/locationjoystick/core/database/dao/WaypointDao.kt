package com.locationjoystick.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.locationjoystick.core.database.entities.WaypointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WaypointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(waypoint: WaypointEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(waypoints: List<WaypointEntity>)

    @Query("DELETE FROM waypoints WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM waypoints WHERE routeId = :routeId")
    suspend fun deleteByRouteId(routeId: String)

    @Query("SELECT * FROM waypoints WHERE routeId = :routeId ORDER BY orderIndex ASC")
    fun getByRouteId(routeId: String): Flow<List<WaypointEntity>>
}
