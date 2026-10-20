package com.locationjoystick.core.data

import android.app.Application
import androidx.room.Room
import com.locationjoystick.core.database.LjDatabase
import com.locationjoystick.core.database.dao.RouteDao
import com.locationjoystick.core.database.entities.RouteEntity
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.Waypoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {
    private lateinit var database: LjDatabase

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication() as Application, LjDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun fav(id: String) = FavoriteLocation(id = id, name = id, position = LatLng(1.0, 2.0), createdAt = 1L)

    private fun route(id: String) =
        Route(
            id = id,
            name = id,
            waypoints = listOf(Waypoint(id = "wp_$id", position = LatLng(1.0, 2.0), orderIndex = 0)),
            createdAt = 1L,
            updatedAt = 1L,
        )

    private fun repository(routeDao: RouteDao = database.routeDao()) = BackupRepository(database, database.favoriteDao(), routeDao)

    private suspend fun routeIds() =
        database
            .routeDao()
            .getAllWithWaypoints()
            .first()
            .map { it.route.id }

    private suspend fun favoriteIds() =
        database
            .favoriteDao()
            .getAll()
            .first()
            .map { it.id }

    @Test
    fun `replace import that fails mid-insert keeps old data`() =
        runBlocking {
            repository().apply(listOf(fav("old")), listOf(route("old")), clearFavorites = false, clearRoutes = false)
            val real = database.routeDao()
            var inserts = 0
            val failing =
                object : RouteDao by real {
                    override suspend fun insert(route: RouteEntity) {
                        if (++inserts == 2) error("boom") else real.insert(route)
                    }
                }

            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    repository(failing).apply(
                        listOf(fav("new")),
                        listOf(route("a"), route("b"), route("c")),
                        clearFavorites = true,
                        clearRoutes = true,
                    )
                }
            }

            assertEquals(listOf("old"), favoriteIds())
            assertEquals(listOf("old"), routeIds())
            assertEquals(
                listOf("wp_old"),
                database
                    .routeDao()
                    .getWithWaypoints("old")
                    .first()!!
                    .waypoints
                    .map { it.id },
            )
        }

    @Test
    fun `replace import success swaps data`() =
        runBlocking {
            repository().apply(listOf(fav("old")), listOf(route("old")), clearFavorites = false, clearRoutes = false)
            repository().apply(listOf(fav("new")), listOf(route("new")), clearFavorites = true, clearRoutes = true)

            assertEquals(listOf("new"), favoriteIds())
            assertEquals(listOf("new"), routeIds())
        }

    @Test
    fun `merge keeps existing rows`() =
        runBlocking {
            repository().apply(listOf(fav("old")), listOf(route("old")), clearFavorites = false, clearRoutes = false)
            repository().apply(listOf(fav("new")), listOf(route("new")), clearFavorites = false, clearRoutes = false)

            assertEquals(setOf("old", "new"), favoriteIds().toSet())
            assertEquals(setOf("old", "new"), routeIds().toSet())
        }
}
