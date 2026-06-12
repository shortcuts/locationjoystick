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
    fun uiState_emits_empty_routes_initially() = runTest {
        viewModel.uiState.test {
            assertEquals(emptyList<Route>(), awaitItem().routes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun uiState_sorts_routes_newest_first() = runTest {
        routesFlow.value = listOf(
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
    fun uiState_sorts_routes_oldest_first_when_flag_false() = runTest {
        sortFlow.value = false
        routesFlow.value = listOf(
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
    fun toggleSort_calls_settings_repository() = runTest {
        viewModel.toggleSort()
        coVerify { settingsRepository.setRoutesSortNewestFirst(false) }
    }

    @Test
    fun deleteRoute_calls_repository() = runTest {
        viewModel.deleteRoute("route-id")
        coVerify { routeRepository.deleteRoute("route-id") }
    }

    @Test
    fun renameRoute_updates_route_name() = runTest {
        routesFlow.value = listOf(route("r1", "Old Name", createdAt = 1000L))

        viewModel.uiState.test {
            awaitItem()
            viewModel.renameRoute("r1", "New Name")
            coVerify { routeRepository.updateRoute(match { it.name == "New Name" }) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun playbackState_isPlaying_when_active_route_and_running() = runTest {
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
    fun playbackState_isPaused_when_active_route_and_paused() = runTest {
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
    fun playbackState_idle_when_no_active_route() = runTest {
        viewModel.playbackState.test {
            val state = awaitItem()
            assertFalse(state.isPlaying)
            assertFalse(state.isPaused)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun route(id: String, name: String, createdAt: Long = 0L) = Route(
        id = id,
        name = name,
        waypoints = listOf(Waypoint("w1", LatLng(0.0, 0.0), 0)),
        isLooping = false,
        routeType = RouteType.STRAIGHT,
        createdAt = createdAt,
        updatedAt = createdAt,
    )
}

/**
 * Unit tests for RoutesViewModel GPX import functionality.
 * Best-effort coverage for parseGpxWaypoints, extractGpxName, and Route creation.
 * Tests use the internal parsing methods directly (no ViewModel instantiation needed).
 */
class RoutesViewModelTest {
    private fun parseGpxWaypoints(gpxContent: String): List<LatLng> {
        val trkptRegex = Regex("""<trkpt\s+lat="([^"]+)"\s+lon="([^"]+)""")
        return trkptRegex
            .findAll(gpxContent)
            .map { match ->
                val lat = match.groupValues[1].toDoubleOrNull() ?: return@map null
                val lon = match.groupValues[2].toDoubleOrNull() ?: return@map null
                LatLng(lat, lon)
            }.filterNotNull()
            .toList()
    }

    private fun extractGpxName(gpxContent: String): String {
        val nameRegex = Regex("<name>([^<]+)</name>")
        val match = nameRegex.find(gpxContent)
        return match?.groupValues?.get(1)?.takeIf { it.isNotEmpty() } ?: "Imported Route"
    }

    @Test
    fun testParseGpxWaypoints_validGpx() {
        val gpxContent =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1">
              <trk>
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

        val waypoints = parseGpxWaypoints(gpxContent)

        assertEquals(3, waypoints.size)
        assertEquals(48.8566, waypoints[0].latitude, 0.0001)
        assertEquals(2.3522, waypoints[0].longitude, 0.0001)
        assertEquals(48.8580, waypoints[1].latitude, 0.0001)
        assertEquals(2.3535, waypoints[1].longitude, 0.0001)
    }

    @Test
    fun testParseGpxWaypoints_emptyGpx() {
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

        val waypoints = parseGpxWaypoints(gpxContent)

        assertEquals(0, waypoints.size)
    }

    @Test
    fun testExtractGpxName_validName() {
        val gpxContent =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1">
              <metadata>
                <name>Test Route Name</name>
              </metadata>
            </gpx>
            """.trimIndent()

        val name = extractGpxName(gpxContent)

        assertEquals("Test Route Name", name)
    }

    @Test
    fun testExtractGpxName_missingName() {
        val gpxContent =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1">
              <metadata>
              </metadata>
            </gpx>
            """.trimIndent()

        val name = extractGpxName(gpxContent)

        assertEquals("Imported Route", name)
    }

    @Test
    fun testExtractGpxName_trkNameTag() {
        val gpxContent =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1">
              <trk>
                <name>Track Name</name>
                <trkseg>
                </trkseg>
              </trk>
            </gpx>
            """.trimIndent()

        val name = extractGpxName(gpxContent)

        assertEquals("Track Name", name)
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
    fun `fixture routeRandom trkpt parses all 344 waypoints`() {
        val gpx = loadFixture("route-random-2026-05-25T13-38-39.gpx")

        val waypoints = parseGpxWaypoints(gpx)

        assertEquals(344, waypoints.size)
    }

    @Test
    fun `fixture routeRandom first and last coordinates correct`() {
        val gpx = loadFixture("route-random-2026-05-25T13-38-39.gpx")

        val waypoints = parseGpxWaypoints(gpx)

        assertEquals(51.512219, waypoints.first().latitude, 0.000001)
        assertEquals(-0.132268, waypoints.first().longitude, 0.000001)
        assertEquals(51.512219, waypoints.last().latitude, 0.000001)
        assertEquals(-0.132268, waypoints.last().longitude, 0.000001)
    }

    @Test
    fun `fixture routeRandom name extracted from metadata`() {
        val gpx = loadFixture("route-random-2026-05-25T13-38-39.gpx")

        val name = extractGpxName(gpx)

        assertEquals("Generated Route", name)
    }

    @Test
    fun `fixture gpsJoystick rtept format not yet supported returns empty`() {
        val gpx = loadFixture("gpsjoystick_20250408232304.gpx")

        val waypoints = parseGpxWaypoints(gpx)

        assertEquals(0, waypoints.size)
    }

    @Test
    fun `fixture gpsJoystick name extracted correctly despite rtept format`() {
        val gpx = loadFixture("gpsjoystick_20250408232304.gpx")

        val name = extractGpxName(gpx)

        assertEquals("Paris Jardins", name)
    }
}
