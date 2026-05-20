package com.locationjoystick.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.locationjoystick.core.database.entities.RouteEntity
import com.locationjoystick.core.database.entities.WaypointEntity
import kotlinx.coroutines.flow.Flow

data class RouteWithWaypoints(
    @Embedded val route: RouteEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "routeId",
    )
    val waypoints: List<WaypointEntity>,
)

@Dao
interface RouteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(route: RouteEntity)

    @Update
    suspend fun update(route: RouteEntity)

    /**
     * Atomically replaces all waypoints for a route.
     * Deletes existing waypoints then inserts new ones in a single transaction,
     * preventing a partial-write state where the route exists with no waypoints.
     */
    @Transaction
    suspend fun replaceWaypoints(
        routeId: String,
        waypoints: List<WaypointEntity>,
    ) {
        deleteWaypointsByRouteId(routeId)
        insertWaypoints(waypoints)
    }

    @Delete
    suspend fun delete(route: RouteEntity)

    @Query("SELECT * FROM routes WHERE id = :id")
    suspend fun getById(id: String): RouteEntity?

    @Query("SELECT * FROM routes ORDER BY createdAt DESC")
    fun getAll(): Flow<List<RouteEntity>>

    @Transaction
    @Query("SELECT * FROM routes WHERE id = :routeId")
    fun getWithWaypoints(routeId: String): Flow<RouteWithWaypoints?>

    @Transaction
    @Query("SELECT * FROM routes ORDER BY createdAt DESC")
    fun getAllWithWaypoints(): Flow<List<RouteWithWaypoints>>

    @Query("DELETE FROM waypoints WHERE routeId = :routeId")
    suspend fun deleteWaypointsByRouteId(routeId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaypoints(waypoints: List<WaypointEntity>)

    @Query("DELETE FROM waypoints WHERE id = :waypointId")
    suspend fun deleteWaypointById(waypointId: String)
}
