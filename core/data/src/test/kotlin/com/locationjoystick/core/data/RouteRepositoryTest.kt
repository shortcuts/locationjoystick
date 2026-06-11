package com.locationjoystick.core.data

import android.content.Context
import app.cash.turbine.test
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.core.model.Waypoint
import com.locationjoystick.core.testing.FakeRouteDao
import com.locationjoystick.core.testing.FakeWaypointDao
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RouteRepositoryTest {
    private lateinit var routeDao: FakeRouteDao
    private lateinit var waypointDao: FakeWaypointDao
    private lateinit var repository: RouteRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        waypointDao = FakeWaypointDao()
        routeDao = FakeRouteDao(waypointDao)
        repository = RouteRepository(routeDao, mockk<Context>(relaxed = true), testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // getRoutes

    @Test
    fun `getRoutes emits empty list initially`() =
        runTest {
            repository.getRoutes().test {
                assertTrue(awaitItem().isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getRoutes emits added route`() =
        runTest {
            val route =
                createRoute(
                    id = "route-1",
                    name = "Morning Walk",
                    waypoints =
                        listOf(
                            Waypoint(id = "wp-1", position = LatLng(0.0, 0.0), orderIndex = 0),
                            Waypoint(id = "wp-2", position = LatLng(1.0, 0.0), orderIndex = 1),
                        ),
                )

            repository.getRoutes().test {
                awaitItem() // empty

                repository.insertRoute(route)

                val routes = awaitItem()
                assertEquals(1, routes.size)
                assertEquals("Morning Walk", routes[0].name)
                assertEquals(2, routes[0].waypoints.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getRoutes returns multiple routes sorted by createdAt desc`() =
        runTest {
            val route1 = createRoute("a", "Old", createdAt = 100L)
            val route2 = createRoute("b", "New", createdAt = 200L)

            repository.insertRoute(route1)
            repository.insertRoute(route2)

            repository.getRoutes().test {
                val routes = awaitItem()
                assertEquals(2, routes.size)
                assertEquals("New", routes[0].name)
                assertEquals("Old", routes[1].name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // getRouteWithWaypoints

    @Test
    fun `getRouteWithWaypoints returns route with waypoints`() =
        runTest {
            val waypoints =
                listOf(
                    Waypoint(id = "wp-1", position = LatLng(48.8566, 2.3522), orderIndex = 0),
                    Waypoint(id = "wp-2", position = LatLng(48.8570, 2.3530), orderIndex = 1),
                )
            val route = createRoute("route-1", "Paris Walk", waypoints = waypoints)
            repository.insertRoute(route)

            repository.getRouteWithWaypoints("route-1").test {
                val result = awaitItem()
                assertNotNull(result)
                assertEquals("Paris Walk", result!!.name)
                assertEquals(2, result.waypoints.size)
                assertEquals(LatLng(48.8566, 2.3522), result.waypoints[0].position)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getRouteWithWaypoints returns null for non-existent id`() =
        runTest {
            repository.getRouteWithWaypoints("does-not-exist").test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // insertRoute

    @Test
    fun `insertRoute returns success`() =
        runTest {
            val route = createRoute("route-1", "Test Route")
            val result = repository.insertRoute(route)
            assertTrue(result.isSuccess)
        }

    @Test
    fun `insertRoute stores waypoints`() =
        runTest {
            val waypoints =
                listOf(
                    Waypoint(id = "wp-1", position = LatLng(0.0, 0.0), orderIndex = 0),
                    Waypoint(id = "wp-2", position = LatLng(1.0, 1.0), orderIndex = 1),
                    Waypoint(id = "wp-3", position = LatLng(2.0, 2.0), orderIndex = 2),
                )
            val route = createRoute("route-1", "Test", waypoints = waypoints)
            repository.insertRoute(route)

            repository.getRouteWithWaypoints("route-1").test {
                val result = awaitItem()
                assertNotNull(result)
                assertEquals(3, result!!.waypoints.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // updateRoute

    @Test
    fun `updateRoute returns success`() =
        runTest {
            val route = createRoute("route-1", "Original")
            repository.insertRoute(route)

            val updated = route.copy(name = "Updated Name")
            val result = repository.updateRoute(updated)
            assertTrue(result.isSuccess)
        }

    @Test
    fun `updateRoute changes route name`() =
        runTest {
            val route =
                createRoute(
                    "route-1",
                    "Original",
                    waypoints =
                        listOf(
                            Waypoint(id = "wp-1", position = LatLng(0.0, 0.0), orderIndex = 0),
                        ),
                )
            repository.insertRoute(route)

            val updated = route.copy(name = "Updated")
            repository.updateRoute(updated)

            repository.getRouteWithWaypoints("route-1").test {
                val result = awaitItem()
                assertEquals("Updated", result!!.name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `updateRoute replaces waypoints`() =
        runTest {
            val originalWaypoints =
                listOf(
                    Waypoint(id = "wp-1", position = LatLng(0.0, 0.0), orderIndex = 0),
                    Waypoint(id = "wp-2", position = LatLng(1.0, 0.0), orderIndex = 1),
                )
            val route = createRoute("route-1", "Test", waypoints = originalWaypoints)
            repository.insertRoute(route)

            val newWaypoints =
                listOf(
                    Waypoint(id = "wp-3", position = LatLng(5.0, 5.0), orderIndex = 0),
                )
            val updated = route.copy(waypoints = newWaypoints)
            repository.updateRoute(updated)

            repository.getRouteWithWaypoints("route-1").test {
                val result = awaitItem()
                assertEquals(1, result!!.waypoints.size)
                assertEquals(LatLng(5.0, 5.0), result.waypoints[0].position)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // deleteRoute

    @Test
    fun `deleteRoute returns success`() =
        runTest {
            val route = createRoute("route-1", "To Delete")
            repository.insertRoute(route)

            val result = repository.deleteRoute("route-1")
            assertTrue(result.isSuccess)
        }

    @Test
    fun `deleteRoute removes route from flow`() =
        runTest {
            val route = createRoute("route-1", "To Delete")
            repository.insertRoute(route)

            repository.getRoutes().test {
                val initial = awaitItem()
                assertEquals(1, initial.size)

                repository.deleteRoute("route-1")

                val afterDelete = awaitItem()
                assertTrue(afterDelete.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `deleteRoute non-existent id succeeds`() =
        runTest {
            val result = repository.deleteRoute("does-not-exist")
            assertTrue(result.isSuccess)
        }

    // removeWaypoint

    @Test
    fun `removeWaypoint returns success`() =
        runTest {
            val route =
                createRoute(
                    "route-1",
                    "Test",
                    waypoints =
                        listOf(
                            Waypoint(id = "wp-1", position = LatLng(0.0, 0.0), orderIndex = 0),
                        ),
                )
            repository.insertRoute(route)

            val result = repository.removeWaypoint("wp-1")
            assertTrue(result.isSuccess)
        }

    @Test
    fun `removeWaypoint removes waypoint from route`() =
        runTest {
            val waypoints =
                listOf(
                    Waypoint(id = "wp-1", position = LatLng(0.0, 0.0), orderIndex = 0),
                    Waypoint(id = "wp-2", position = LatLng(1.0, 0.0), orderIndex = 1),
                )
            val route = createRoute("route-1", "Test", waypoints = waypoints)
            repository.insertRoute(route)

            repository.removeWaypoint("wp-1")

            repository.getRouteWithWaypoints("route-1").test {
                val result = awaitItem()
                assertEquals(1, result!!.waypoints.size)
                assertEquals("wp-2", result.waypoints[0].id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // renameRoute

    @Test
    fun `renameRoute returns success`() =
        runTest {
            val route = createRoute("route-1", "Old Name")
            repository.insertRoute(route)

            val result = repository.renameRoute("route-1", "New Name")
            assertTrue(result.isSuccess)
        }

    @Test
    fun `renameRoute changes route name`() =
        runTest {
            val route = createRoute("route-1", "Old Name")
            repository.insertRoute(route)

            repository.renameRoute("route-1", "New Name")

            repository.getRouteWithWaypoints("route-1").test {
                val result = awaitItem()
                assertEquals("New Name", result!!.name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `renameRoute updates updatedAt timestamp`() =
        runTest {
            val route = createRoute("route-1", "Test", createdAt = 1000L, updatedAt = 1000L)
            repository.insertRoute(route)

            repository.renameRoute("route-1", "Renamed")

            repository.getRouteWithWaypoints("route-1").test {
                val result = awaitItem()
                assertTrue(result!!.updatedAt > 1000L)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `renameRoute non-existent id succeeds silently`() =
        runTest {
            val result = repository.renameRoute("does-not-exist", "New Name")
            assertTrue(result.isSuccess)
        }

    // GUIDED route type

    @Test
    fun `insertRoute preserves GUIDED route type`() =
        runTest {
            val route =
                createRoute(
                    id = "guided-1",
                    name = "Guided Route",
                    routeType = RouteType.GUIDED,
                    waypoints =
                        listOf(
                            Waypoint(id = "wp-1", position = LatLng(0.0, 0.0), orderIndex = 0),
                        ),
                )
            repository.insertRoute(route)

            repository.getRouteWithWaypoints("guided-1").test {
                val result = awaitItem()
                assertEquals(RouteType.GUIDED, result!!.routeType)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // looping route

    @Test
    fun `insertRoute preserves isLooping flag`() =
        runTest {
            val route =
                createRoute(
                    id = "loop-1",
                    name = "Loop Route",
                    isLooping = true,
                    waypoints =
                        listOf(
                            Waypoint(id = "wp-1", position = LatLng(0.0, 0.0), orderIndex = 0),
                        ),
                )
            repository.insertRoute(route)

            repository.getRouteWithWaypoints("loop-1").test {
                val result = awaitItem()
                assertTrue(result!!.isLooping)
                cancelAndIgnoreRemainingEvents()
            }
        }
}

private fun createRoute(
    id: String,
    name: String,
    waypoints: List<Waypoint> = emptyList(),
    isLooping: Boolean = false,
    routeType: RouteType = RouteType.STRAIGHT,
    createdAt: Long = System.currentTimeMillis(),
    updatedAt: Long = System.currentTimeMillis(),
): Route =
    Route(
        id = id,
        name = name,
        waypoints = waypoints,
        isLooping = isLooping,
        routeType = routeType,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
