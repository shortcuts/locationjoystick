package com.locationjoystick.core.map.geojson

import com.locationjoystick.core.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoJsonUtilsTest {
    private val empty = """{"type":"FeatureCollection","features":[]}"""

    private fun countFeatures(geoJson: String): Int {
        if (geoJson == empty) return 0
        // Count "type":"Feature" occurrences (excludes the outer FeatureCollection type).
        return Regex(""""type":"Feature"""").findAll(geoJson).count()
    }

    // -------------------------------------------------------------------------
    // emptyGeoJson
    // -------------------------------------------------------------------------

    @Test
    fun `emptyGeoJson returns empty feature collection`() {
        assertEquals(empty, emptyGeoJson())
    }

    // -------------------------------------------------------------------------
    // buildPositionGeoJson
    // -------------------------------------------------------------------------

    @Test
    fun `buildPositionGeoJson with null returns empty`() {
        assertEquals(empty, buildPositionGeoJson(null))
    }

    @Test
    fun `buildPositionGeoJson with position emits point with lon-lat order`() {
        val result = buildPositionGeoJson(LatLng(48.8566, 2.3522))
        assertTrue(result.contains(""""type":"Point""""))
        assertTrue("coordinates must be [lon,lat]", result.contains("""[2.3522,48.8566]"""))
        assertEquals(1, countFeatures(result))
    }

    // -------------------------------------------------------------------------
    // buildLineGeoJson
    // -------------------------------------------------------------------------

    @Test
    fun `buildLineGeoJson with fewer than 2 points returns empty`() {
        assertEquals(empty, buildLineGeoJson(emptyList()))
        assertEquals(empty, buildLineGeoJson(listOf(LatLng(1.0, 2.0))))
    }

    @Test
    fun `buildLineGeoJson with 2 points emits LineString in lon-lat order`() {
        val result = buildLineGeoJson(listOf(LatLng(48.0, 2.0), LatLng(49.0, 3.0)))
        assertTrue(result.contains(""""type":"LineString""""))
        assertTrue(result.contains("""[2.0,48.0],[3.0,49.0]"""))
        assertEquals(1, countFeatures(result))
    }

    // -------------------------------------------------------------------------
    // buildPointsGeoJson
    // -------------------------------------------------------------------------

    @Test
    fun `buildPointsGeoJson with empty list returns empty`() {
        assertEquals(empty, buildPointsGeoJson(emptyList()))
    }

    @Test
    fun `buildPointsGeoJson emits one feature per point`() {
        val result = buildPointsGeoJson(listOf(LatLng(1.0, 2.0), LatLng(3.0, 4.0), LatLng(5.0, 6.0)))
        assertEquals(3, countFeatures(result))
        assertTrue(result.contains("""[2.0,1.0]"""))
        assertTrue(result.contains("""[6.0,5.0]"""))
    }

    // -------------------------------------------------------------------------
    // buildMarkerGeoJson
    // -------------------------------------------------------------------------

    @Test
    fun `buildMarkerGeoJson emits single point in lon-lat order`() {
        val result = buildMarkerGeoJson(lat = 10.5, lon = 20.25)
        assertEquals(1, countFeatures(result))
        assertTrue(result.contains(""""type":"Point""""))
        assertTrue(result.contains("""[20.25,10.5]"""))
    }

    // -------------------------------------------------------------------------
    // buildSegmentsGeoJson
    // -------------------------------------------------------------------------

    @Test
    fun `buildSegmentsGeoJson with empty list returns empty`() {
        assertEquals(empty, buildSegmentsGeoJson(emptyList()))
    }

    @Test
    fun `buildSegmentsGeoJson emits one LineString feature per segment`() {
        val segments =
            listOf(
                listOf(LatLng(1.0, 2.0), LatLng(3.0, 4.0)),
                listOf(LatLng(5.0, 6.0), LatLng(7.0, 8.0)),
            )
        val result = buildSegmentsGeoJson(segments)
        assertEquals(2, countFeatures(result))
        assertEquals(2, Regex(""""type":"LineString"""").findAll(result).count())
    }

    // -------------------------------------------------------------------------
    // buildWaypointsGeoJson
    // -------------------------------------------------------------------------

    @Test
    fun `buildWaypointsGeoJson with empty list returns empty`() {
        assertEquals(empty, buildWaypointsGeoJson(emptyList()))
    }

    @Test
    fun `buildWaypointsGeoJson emits one point feature per waypoint`() {
        val result = buildWaypointsGeoJson(listOf(LatLng(1.0, 2.0), LatLng(3.0, 4.0)))
        assertEquals(2, countFeatures(result))
        assertEquals(2, Regex(""""type":"Point"""").findAll(result).count())
    }

    // -------------------------------------------------------------------------
    // buildRouteTraceGeoJson
    // -------------------------------------------------------------------------

    @Test
    fun `buildRouteTraceGeoJson with empty waypoints returns two empty strings`() {
        val (traced, remaining) = buildRouteTraceGeoJson(emptyList(), LatLng(1.0, 2.0))
        assertEquals(empty, traced)
        assertEquals(empty, remaining)
    }

    @Test
    fun `buildRouteTraceGeoJson near first waypoint keeps full route as remaining`() {
        val waypoints =
            listOf(
                LatLng(48.0, 2.0),
                LatLng(48.1, 2.1),
                LatLng(48.2, 2.2),
                LatLng(48.3, 2.3),
            )
        // Current position essentially on the first waypoint.
        val (traced, remaining) = buildRouteTraceGeoJson(waypoints, LatLng(48.0001, 2.0001))

        // Traced is [wp0] + current = 2 points -> a valid LineString feature.
        assertEquals(1, countFeatures(traced))
        // Remaining is current + all waypoints after index 0 = current + wp1..wp3 = 4 points.
        assertEquals(1, countFeatures(remaining))
        assertTrue("remaining must include the last waypoint", remaining.contains("""[2.3,48.3]"""))
    }

    @Test
    fun `buildRouteTraceGeoJson near last waypoint leaves remaining as a single-point stub`() {
        val waypoints =
            listOf(
                LatLng(48.0, 2.0),
                LatLng(48.1, 2.1),
                LatLng(48.2, 2.2),
                LatLng(48.3, 2.3),
            )
        // Current position essentially on the last waypoint.
        val (traced, remaining) = buildRouteTraceGeoJson(waypoints, LatLng(48.3001, 2.3001))

        // Traced is wp0..wp3 + current = 5 points -> one LineString feature.
        assertEquals(1, countFeatures(traced))
        assertTrue("traced must include the first waypoint", traced.contains("""[2.0,48.0]"""))
        // Remaining is only [current] (no waypoints after last index) = 1 point -> empty (< 2).
        assertEquals(empty, remaining)
    }

    @Test
    fun `buildRouteTraceGeoJson near middle waypoint splits route at that index`() {
        val waypoints =
            listOf(
                LatLng(48.0, 2.0),
                LatLng(48.1, 2.1),
                LatLng(48.2, 2.2),
                LatLng(48.3, 2.3),
            )
        // Closest to index 1 (48.1, 2.1).
        val (traced, remaining) = buildRouteTraceGeoJson(waypoints, LatLng(48.1001, 2.1001))

        // Traced = wp0, wp1, current => contains wp0 and wp1, not wp2/wp3.
        assertTrue(traced.contains("""[2.0,48.0]"""))
        assertTrue(traced.contains("""[2.1,48.1]"""))
        assertFalse(traced.contains("""[2.2,48.2]"""))

        // Remaining = current, wp2, wp3 => contains wp2 and wp3, not wp0/wp1.
        assertTrue(remaining.contains("""[2.2,48.2]"""))
        assertTrue(remaining.contains("""[2.3,48.3]"""))
        assertFalse(remaining.contains("""[2.0,48.0]"""))
    }

    @Test
    fun `buildRouteTraceGeoJson uses haversine distance to find closest waypoint`() {
        // Waypoints span a longitude line; closest by great-circle distance is index 2.
        val waypoints =
            listOf(
                LatLng(0.0, 0.0),
                LatLng(0.0, 10.0),
                LatLng(0.0, 20.0),
                LatLng(0.0, 30.0),
            )
        val (traced, _) = buildRouteTraceGeoJson(waypoints, LatLng(0.0, 19.5))

        // Closest is index 2 (lon 20). Traced = wp0, wp1, wp2, current; must include wp2 not wp3.
        assertTrue(traced.contains("""[20.0,0.0]"""))
        assertFalse(traced.contains("""[30.0,0.0]"""))
    }
}
