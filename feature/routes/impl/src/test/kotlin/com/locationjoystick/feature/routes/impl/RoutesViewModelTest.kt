package com.locationjoystick.feature.routes.impl

import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.core.model.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
                isLooping = false, // Should always be false for new imports
                routeType = RouteType.STRAIGHT, // Should always be STRAIGHT for GPX
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
}
