package com.locationjoystick.core.common.util

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PlantingPathTest {
    @Test
    fun `orderByProximity with two points keeps original order`() {
        val a = LatLng(10.0, 10.0)
        val b = LatLng(0.0, 0.0)
        assertEquals(listOf(a, b), orderByProximity(listOf(a, b)))
    }

    @Test
    fun `orderByProximity starts at min lat plus lon then nearest neighbor`() {
        val a = LatLng(1.0, 1.0)
        val b = LatLng(0.0, 0.0)
        val c = LatLng(0.05, 0.05)
        val ordered = orderByProximity(listOf(a, b, c))
        assertEquals(listOf(b, c, a), ordered)
    }

    @Test
    fun `circleAround is closed and sits on the radius`() {
        val center = LatLng(35.68, 139.76)
        val radius = AppConstants.RouteConstants.PLANTING_DEFAULT_RADIUS_METERS
        val ring = circleAround(center, radius)
        assertTrue(ring.size >= AppConstants.RouteConstants.PLANTING_MIN_VERTICES + 1)
        assertEquals(ring.first(), ring.last())
        ring.dropLast(1).forEach { vertex ->
            val dist = haversineDistance(center, vertex)
            assertEquals("vertex $vertex distance $dist", radius, dist, 1.0)
        }
    }

    @Test
    fun `circleAround first vertex is north of the center`() {
        val center = LatLng(0.0, 0.0)
        val ring = circleAround(center, 35.0)
        assertTrue(ring[0].latitude > center.latitude)
        assertTrue(abs(ring[0].longitude - center.longitude) < 1e-5)
        assertTrue(ring[1].longitude > ring[0].longitude)
    }

    @Test
    fun `plantingCircleVertexCount clamps to min and max`() {
        assertEquals(
            AppConstants.RouteConstants.PLANTING_MIN_VERTICES,
            plantingCircleVertexCount(1.0),
        )
        assertEquals(
            AppConstants.RouteConstants.PLANTING_MAX_VERTICES,
            plantingCircleVertexCount(10_000.0),
        )
        val forDefault = plantingCircleVertexCount(35.0)
        assertTrue(forDefault in AppConstants.RouteConstants.PLANTING_MIN_VERTICES..AppConstants.RouteConstants.PLANTING_MAX_VERTICES)
    }

    @Test
    fun `buildPlantingWaypoints walks a circle then the next circle`() {
        val first = LatLng(0.0, 0.0)
        val second = LatLng(0.02, 0.0)
        val radius = 35.0
        val path = buildPlantingWaypoints(listOf(first, second), radius)
        val firstRingSize = circleAround(first, radius).size
        assertTrue(path.size > firstRingSize)
        assertEquals(path[0], path[firstRingSize - 1])
        path.take(firstRingSize).dropLast(1).forEach {
            assertEquals(radius, haversineDistance(first, it), 1.0)
        }
        val secondStart = path[firstRingSize]
        assertEquals(radius, haversineDistance(second, secondStart), 1.0)
    }

    @Test
    fun `buildPlantingReplayPath marks each ring start as a boundary`() {
        val first = LatLng(0.0, 0.0)
        val second = LatLng(0.02, 0.0)
        val radius = 35.0
        val (path, boundaries) = buildPlantingReplayPath(listOf(first, second), radius)
        assertEquals(listOf(0, circleAround(first, radius).size), boundaries)
        assertEquals(radius, haversineDistance(first, path[boundaries[0]]), 1.0)
        assertEquals(radius, haversineDistance(second, path[boundaries[1]]), 1.0)
    }

    @Test
    fun `buildPlantingWaypoints with one center is a single closed circle`() {
        val center = LatLng(51.5, -0.12)
        val path = buildPlantingWaypoints(listOf(center), 35.0)
        assertEquals(circleAround(center, 35.0), path)
    }

    @Test
    fun `clampPlantingRadius clamps to configured bounds`() {
        assertEquals(
            AppConstants.RouteConstants.PLANTING_MIN_RADIUS_METERS,
            clampPlantingRadius(1.0),
            0.0,
        )
        assertEquals(
            AppConstants.RouteConstants.PLANTING_MAX_RADIUS_METERS,
            clampPlantingRadius(999.0),
            0.0,
        )
        assertEquals(35.0, clampPlantingRadius(35.0), 0.0)
    }

    @Test
    fun `stitchRingsWithoutConnectors concatenates rings and marks each start`() {
        val first = LatLng(0.0, 0.0)
        val second = LatLng(0.02, 0.0)
        val radius = 35.0
        val rings = plantingRings(listOf(first, second), radius)
        val (path, boundaries) = stitchRingsWithoutConnectors(rings)
        assertEquals(rings[0] + rings[1], path)
        assertEquals(listOf(0, rings[0].size), boundaries)
        assertEquals(rings[0].first(), path[boundaries[0]])
        assertEquals(rings[1].first(), path[boundaries[1]])
    }
}
