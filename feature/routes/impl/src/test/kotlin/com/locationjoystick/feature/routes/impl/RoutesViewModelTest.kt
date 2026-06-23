package com.locationjoystick.feature.routes.impl

import android.content.Context
import app.cash.turbine.test
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.core.model.Waypoint
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoutesViewModelUiStateTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val routeRepository: RouteRepository = mockk(relaxed = true)
    private val locationRepository = LocationRepository()
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val routesFlow = MutableStateFlow<List<Route>>(emptyList())
    private val sortFlow = MutableStateFlow(true)
    private lateinit var viewModel: RoutesViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { routeRepository.getRoutes() } returns routesFlow
        every { settingsRepository.getRoutesSortNewestFirst() } returns sortFlow
        viewModel = RoutesViewModel(routeRepository, locationRepository, settingsRepository, context)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_emits_empty_routes_initially() =
        runTest {
            viewModel.uiState.test {
                assertEquals(emptyList<Route>(), awaitItem().routes)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun uiState_sorts_routes_newest_first() =
        runTest {
            routesFlow.value =
                listOf(
                    route("id1", "Old Route", createdAt = 1000L),
                    route("id2", "New Route", createdAt = 2000L),
                )

            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals("New Route", state.routes[0].name)
                assertEquals("Old Route", state.routes[1].name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun uiState_sorts_routes_oldest_first_when_flag_false() =
        runTest {
            sortFlow.value = false
            routesFlow.value =
                listOf(
                    route("id2", "New Route", createdAt = 2000L),
                    route("id1", "Old Route", createdAt = 1000L),
                )

            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals("Old Route", state.routes[0].name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun toggleSort_calls_settings_repository() =
        runTest {
            viewModel.toggleSort()
            coVerify { settingsRepository.setRoutesSortNewestFirst(false) }
        }

    @Test
    fun deleteRoute_calls_repository() =
        runTest {
            viewModel.deleteRoute("route-id")
            coVerify { routeRepository.deleteRoute("route-id") }
        }

    @Test
    fun renameRoute_updates_route_name() =
        runTest {
            routesFlow.value = listOf(route("r1", "Old Name", createdAt = 1000L))

            viewModel.uiState.test {
                awaitItem()
                viewModel.renameRoute("r1", "New Name")
                coVerify { routeRepository.updateRoute(match { it.name == "New Name" }) }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun playbackState_isPlaying_when_active_route_and_running() =
        runTest {
            locationRepository.setActiveRouteId("r1")
            locationRepository.startSpoofing()

            viewModel.playbackState.test {
                val state = awaitItem()
                assertEquals("r1", state.activeRouteId)
                assertTrue(state.isPlaying)
                assertFalse(state.isPaused)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun playbackState_isPaused_when_active_route_and_paused() =
        runTest {
            locationRepository.setActiveRouteId("r1")
            locationRepository.pauseSpoofing()

            viewModel.playbackState.test {
                val state = awaitItem()
                assertFalse(state.isPlaying)
                assertTrue(state.isPaused)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun playbackState_idle_when_no_active_route() =
        runTest {
            viewModel.playbackState.test {
                val state = awaitItem()
                assertFalse(state.isPlaying)
                assertFalse(state.isPaused)
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun route(
        id: String,
        name: String,
        createdAt: Long = 0L,
    ) = Route(
        id = id,
        name = name,
        waypoints = listOf(Waypoint("w1", LatLng(0.0, 0.0), 0)),
        isLooping = false,
        routeType = RouteType.STRAIGHT,
        createdAt = createdAt,
        updatedAt = createdAt,
    )
}

class RoutesViewModelTest {
    @Test
    fun testParseGpxRoutes_validGpx() {
        val gpxContent =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1">
              <trk>
                <name>Track Name</name>
                <trkseg>
                  <trkpt lat="48.8566" lon="2.3522">
                    <ele>35</ele>
                  </trkpt>
                  <trkpt lat="48.8580" lon="2.3535">
                    <ele>40</ele>
                  </trkpt>
                  <trkpt lat="48.8590" lon="2.3550">
                    <ele>42</ele>
                  </trkpt>
                </trkseg>
              </trk>
            </gpx>
            """.trimIndent()

        val routes = parseGpxRoutes(gpxContent)

        assertEquals(1, routes.size)
        assertEquals("Track Name", routes[0].name)
        val waypoints = routes[0].waypoints
        assertEquals(3, waypoints.size)
        assertEquals(48.8566, waypoints[0].latitude, 0.0001)
        assertEquals(2.3522, waypoints[0].longitude, 0.0001)
        assertEquals(48.8580, waypoints[1].latitude, 0.0001)
        assertEquals(2.3535, waypoints[1].longitude, 0.0001)
    }

    @Test
    fun testParseGpxRoutes_emptyGpx() {
        val gpxContent =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1">
              <trk>
                <trkseg>
                </trkseg>
              </trk>
            </gpx>
            """.trimIndent()

        val routes = parseGpxRoutes(gpxContent)

        assertEquals(0, routes.size)
    }

    @Test
    fun testParseGpxRoutes_missingName_fallsBackToImportedRoute() {
        val gpxContent =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1">
              <trk>
                <trkseg>
                  <trkpt lat="1.0" lon="2.0"/>
                </trkseg>
              </trk>
            </gpx>
            """.trimIndent()

        val routes = parseGpxRoutes(gpxContent)

        assertEquals(1, routes.size)
        assertEquals("Imported Route", routes[0].name)
    }

    @Test
    fun testParseGpxRoutes_multipleRteElements_oneRoutePerElement() {
        // Sample from github.com/shortcuts/locationjoystick/issues/21 — a GPX file with
        // multiple <rte> elements must import as multiple routes, not one merged route.
        val gpxContent =
            """
            <?xml version="1.0" encoding="UTF-8"?><gpx version="1.1" creator="GPS JoyStick - gpsjoystick@gmail.com - https://www.facebook.com/gpsjoystick">
            <rte><name>Zaragoza</name><number>0</number>
            <rtept lat="41.6622857209573" lon="-0.89515943080186"/>
            <rtept lat="41.6620422571306" lon="-0.8941837772727"/>
            <rtept lat="41.6614338440167" lon="-0.89422401040792"/>
            <rtept lat="41.6611297605447" lon="-0.89590240269899"/></rte>
            <rte><name>hg0829</name><number>0</number>
            <rtept lat="38.50748001528502" lon="-122.09600671884579"/>
            <rtept lat="38.50748001528502" lon="-122.09799728115425"/>
            <rtept lat="38.50882900000001" lon="-122.09700200000002"/></rte>
            <rte><name>Auckland</name><number>0</number>
            <rtept lat="-36.85221397537331" lon="174.76393103599548"><ele>33.0</ele></rtept>
            <rtept lat="-36.85317200261459" lon="174.7635106050017"><ele>12.375</ele></rtept>
            <rtept lat="-36.85413002837526" lon="174.76309016346931"><ele>8.25</ele></rtept>
            <rtept lat="-36.85346872455474" lon="174.76482085883617"><ele>33.0</ele></rtept></rte>
            <rte><name>Wellington</name><number>0</number>
            <rtept lat="-41.282965" lon="174.766473"/>
            <rtept lat="-41.283305" lon="174.765187"/>
            <rtept lat="-41.280788" lon="174.769201"/></rte>
            </gpx>
            """.trimIndent()

        val routes = parseGpxRoutes(gpxContent)

        assertEquals(4, routes.size)
        assertEquals("Zaragoza", routes[0].name)
        assertEquals(4, routes[0].waypoints.size)
        assertEquals(41.6622857209573, routes[0].waypoints[0].latitude, 0.0000001)
        assertEquals("hg0829", routes[1].name)
        assertEquals(3, routes[1].waypoints.size)
        assertEquals("Auckland", routes[2].name)
        assertEquals(4, routes[2].waypoints.size)
        assertEquals("Wellington", routes[3].name)
        assertEquals(3, routes[3].waypoints.size)
    }

    @Test
    fun testParseGpxRoutes_multipleUnnamedSegments_numbersFallbackNames() {
        val gpxContent =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1">
              <rte><rtept lat="1.0" lon="2.0"/><rtept lat="3.0" lon="4.0"/></rte>
              <rte><rtept lat="5.0" lon="6.0"/><rtept lat="7.0" lon="8.0"/></rte>
            </gpx>
            """.trimIndent()

        val routes = parseGpxRoutes(gpxContent)

        assertEquals(2, routes.size)
        assertEquals("Imported Route 1", routes[0].name)
        assertEquals("Imported Route 2", routes[1].name)
    }

    @Test
    fun testWaypointCreation_correctOrderIndex() {
        val latLngs =
            listOf(
                LatLng(48.8566, 2.3522),
                LatLng(48.8580, 2.3535),
                LatLng(48.8590, 2.3550),
            )

        val waypoints =
            latLngs.mapIndexed { index, latLng ->
                Waypoint(
                    id = "test-$index",
                    position = latLng,
                    orderIndex = index,
                )
            }

        assertEquals(3, waypoints.size)
        assertEquals(0, waypoints[0].orderIndex)
        assertEquals(1, waypoints[1].orderIndex)
        assertEquals(2, waypoints[2].orderIndex)
    }

    @Test
    fun testRouteCreation_fromWaypoints() {
        val waypoints =
            listOf(
                Waypoint("1", LatLng(48.8566, 2.3522), 0),
                Waypoint("2", LatLng(48.8580, 2.3535), 1),
                Waypoint("3", LatLng(48.8590, 2.3550), 2),
            )

        val route =
            Route(
                id = "route-123",
                name = "Test Route",
                waypoints = waypoints,
                isLooping = false,
                routeType = RouteType.STRAIGHT,
                createdAt = 1000L,
                updatedAt = 1000L,
            )

        assertEquals(3, route.waypoints.size)
        assertEquals("Test Route", route.name)
        assertEquals(RouteType.STRAIGHT, route.routeType)
        assertTrue(!route.isLooping)
    }

    @Test
    fun testRouteDefaults_importedRoute() {
        val waypoints =
            listOf(
                Waypoint("1", LatLng(48.8566, 2.3522), 0),
            )

        val route =
            Route(
                id = "route-123",
                name = "Imported",
                waypoints = waypoints,
                isLooping = false,
                routeType = RouteType.STRAIGHT,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )

        assertEquals(false, route.isLooping)
        assertEquals(RouteType.STRAIGHT, route.routeType)
    }

    @Test
    fun testLatLngToWaypoint_mapping() {
        val latLng = LatLng(48.8566, 2.3522)
        val waypoint =
            Waypoint(
                id = "wp-1",
                position = latLng,
                orderIndex = 0,
            )

        assertEquals(48.8566, waypoint.position.latitude, 0.0001)
        assertEquals(2.3522, waypoint.position.longitude, 0.0001)
        assertEquals(0, waypoint.orderIndex)
    }

    // GPX fixture tests — real files from external apps

    private fun loadFixture(name: String): String =
        javaClass.classLoader!!
            .getResourceAsStream(name)!!
            .bufferedReader()
            .readText()

    @Test
    fun `fixture routeRandom trkpt parses single route with all 344 waypoints`() {
        val gpx = loadFixture("route-random-2026-05-25T13-38-39.gpx")

        val routes = parseGpxRoutes(gpx)

        assertEquals(1, routes.size)
        assertEquals(344, routes[0].waypoints.size)
    }

    @Test
    fun `fixture routeRandom first and last coordinates correct`() {
        val gpx = loadFixture("route-random-2026-05-25T13-38-39.gpx")

        val waypoints = parseGpxRoutes(gpx).single().waypoints

        assertEquals(51.512219, waypoints.first().latitude, 0.000001)
        assertEquals(-0.132268, waypoints.first().longitude, 0.000001)
        assertEquals(51.512219, waypoints.last().latitude, 0.000001)
        assertEquals(-0.132268, waypoints.last().longitude, 0.000001)
    }

    @Test
    fun `fixture routeRandom name extracted from metadata`() {
        val gpx = loadFixture("route-random-2026-05-25T13-38-39.gpx")

        val name = parseGpxRoutes(gpx).single().name

        assertEquals("Generated Route", name)
    }

    @Test
    fun `fixture gpsJoystick parses two separate routes, not one merged route`() {
        // Regression for issue #21: this real export has 2 <rte> elements (68 + 117 points)
        // that the old single-flatten parser merged into one 185-point "Paris Jardins" route.
        val gpx = loadFixture("gpsjoystick_20250408232304.gpx")

        val routes = parseGpxRoutes(gpx)

        assertEquals(2, routes.size)
        assertEquals("Paris Jardins", routes[0].name)
        assertEquals(68, routes[0].waypoints.size)
        assertEquals("Paris Discord", routes[1].name)
        assertEquals(117, routes[1].waypoints.size)
    }

    @Test
    fun `fixture gpsJoystick 20260613 rtept with scientific notation coords parses two routes`() {
        val gpx = loadFixture("gpsjoystick_20260613213743.gpx")

        val routes = parseGpxRoutes(gpx)

        assertEquals(2, routes.size)
        assertEquals("2", routes[0].name)
        assertEquals(9, routes[0].waypoints.size) // one malformed <rtept/> (no lat/lon) is skipped
        assertEquals("1", routes[1].name)
        assertEquals(10, routes[1].waypoints.size) // one malformed <rtept/> (no lat/lon) is skipped
    }

    @Test
    fun `mixed trkpt and rtept in same file parses as separate routes`() {
        val gpxContent =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1">
              <trk>
                <name>Track</name>
                <trkseg>
                  <trkpt lat="1.0" lon="2.0"/>
                  <trkpt lat="3.0" lon="4.0"/>
                </trkseg>
              </trk>
              <rte>
                <name>Route</name>
                <rtept lat="5.0" lon="6.0"/>
                <rtept lat="7.0" lon="8.0"/>
              </rte>
            </gpx>
            """.trimIndent()

        val routes = parseGpxRoutes(gpxContent)

        assertEquals(2, routes.size)
        assertEquals("Track", routes[0].name)
        assertEquals(listOf(LatLng(1.0, 2.0), LatLng(3.0, 4.0)), routes[0].waypoints)
        assertEquals("Route", routes[1].name)
        assertEquals(listOf(LatLng(5.0, 6.0), LatLng(7.0, 8.0)), routes[1].waypoints)
    }
}
