package com.locationjoystick.core.routing

import com.locationjoystick.core.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RouteInterpolatorTest {
    private lateinit var interpolator: RouteInterpolator

    @Before
    fun setUp() {
        interpolator = RouteInterpolator()
    }

    // advancePosition

    @Test
    fun `advancePosition zero distance returns same point`() {
        val from = LatLng(51.5, 0.0)
        val result = interpolator.advancePosition(from, 0.0, 0.0)
        assertEquals(from.latitude, result.latitude, 0.0001)
        assertEquals(from.longitude, result.longitude, 0.0001)
    }

    @Test
    fun `advancePosition 100m north increases latitude only`() {
        val from = LatLng(0.0, 0.0)
        val result = interpolator.advancePosition(from, 0.0, 100.0)
        assertTrue("latitude should increase", result.latitude > from.latitude)
        assertEquals(0.0, result.longitude, 0.0001)
    }

    @Test
    fun `advancePosition 100m east increases longitude only`() {
        val from = LatLng(0.0, 0.0)
        val result = interpolator.advancePosition(from, 90.0, 100.0)
        assertTrue("longitude should increase", result.longitude > from.longitude)
        assertEquals(0.0, result.latitude, 0.0001)
    }

    @Test
    fun `advancePosition 100m south decreases latitude`() {
        val from = LatLng(1.0, 0.0)
        val result = interpolator.advancePosition(from, 180.0, 100.0)
        assertTrue("latitude should decrease", result.latitude < from.latitude)
    }

    @Test
    fun `advancePosition 100m north moves approx 100m`() {
        val from = LatLng(0.0, 0.0)
        val result = interpolator.advancePosition(from, 0.0, 100.0)
        // 100m / 6371000 * (180/π) ≈ 0.0008994°
        assertEquals(0.0008994, result.latitude, 0.00001)
    }

    @Test
    fun `advancePosition 1000m at 45 degrees moves roughly equal lat and lon`() {
        val from = LatLng(0.0, 0.0)
        val result = interpolator.advancePosition(from, 45.0, 1000.0)
        // At equator, 45° → lat and lon displacement should be similar
        assertEquals(result.latitude, result.longitude, 0.0001)
    }

    // interpolateAlongRoute — guard conditions

    @Test
    fun `interpolateAlongRoute empty waypoints returns reachedEnd`() {
        val pos = LatLng(0.0, 0.0)
        val result = interpolator.interpolateAlongRoute(emptyList(), pos, 0, 1.4, 1000)
        assertTrue(result.reachedEnd)
    }

    @Test
    fun `interpolateAlongRoute single waypoint returns reachedEnd`() {
        val pos = LatLng(0.0, 0.0)
        val result = interpolator.interpolateAlongRoute(listOf(LatLng(1.0, 0.0)), pos, 0, 1.4, 1000)
        assertTrue(result.reachedEnd)
    }

    @Test
    fun `interpolateAlongRoute index past waypoints returns reachedEnd`() {
        val pos = LatLng(0.0, 0.0)
        val waypoints = listOf(LatLng(0.0, 0.0), LatLng(1.0, 0.0))
        val result = interpolator.interpolateAlongRoute(waypoints, pos, 99, 1.4, 1000)
        assertTrue(result.reachedEnd)
    }

    // interpolateAlongRoute — normal advance

    @Test
    fun `interpolateAlongRoute advances toward target waypoint`() {
        val start = LatLng(0.0, 0.0)
        val target = LatLng(0.01, 0.0) // ~1111m north
        val waypoints = listOf(start, target)
        // speed 1.4 m/s, 1000ms → 1.4m advance; target is 1111m away
        val result = interpolator.interpolateAlongRoute(waypoints, start, 1, 1.4, 1000)
        assertFalse(result.reachedEnd)
        assertTrue("should move north", result.position.latitude > start.latitude)
        assertEquals(1, result.nextWaypointIndex) // index unchanged while advancing
    }

    @Test
    fun `interpolateAlongRoute advance distance proportional to speed`() {
        val start = LatLng(0.0, 0.0)
        val farTarget = LatLng(1.0, 0.0) // very far
        val waypoints = listOf(start, farTarget)

        val slow = interpolator.interpolateAlongRoute(waypoints, start, 1, 1.0, 1000)
        val fast = interpolator.interpolateAlongRoute(waypoints, start, 1, 3.0, 1000)
        assertTrue("faster speed should advance further north", fast.position.latitude > slow.position.latitude)
    }

    // interpolateAlongRoute — snap to waypoint

    @Test
    fun `interpolateAlongRoute snaps when within 1m threshold and advances index`() {
        val start = LatLng(0.0, 0.0)
        // 0.000008° ≈ 0.89m at equator — within 1m snap threshold
        val nearTarget = LatLng(0.000008, 0.0)
        val nextTarget = LatLng(0.1, 0.0)
        val waypoints = listOf(start, nearTarget, nextTarget)

        val result = interpolator.interpolateAlongRoute(waypoints, start, 1, 1.4, 1000)
        assertFalse(result.reachedEnd)
        assertEquals(2, result.nextWaypointIndex)
        assertEquals(nearTarget.latitude, result.position.latitude, 0.000001)
    }

    @Test
    fun `interpolateAlongRoute reaching last waypoint within threshold sets reachedEnd`() {
        val start = LatLng(0.0, 0.0)
        val nearTarget = LatLng(0.000008, 0.0) // ~0.89m — within snap threshold
        val waypoints = listOf(start, nearTarget)

        val result = interpolator.interpolateAlongRoute(waypoints, start, 1, 1.4, 1000)
        assertTrue(result.reachedEnd)
    }

    @Test
    fun `interpolateAlongRoute snaps when advance distance exceeds remaining distance`() {
        val start = LatLng(0.0, 0.0)
        val closeTarget = LatLng(0.00001, 0.0) // ~1.1m away
        val nextTarget = LatLng(0.1, 0.0)
        val waypoints = listOf(start, closeTarget, nextTarget)
        // speed 5 m/s, 1000ms → 5m advance; target is ~1.1m — advance overshoots → snap
        val result = interpolator.interpolateAlongRoute(waypoints, start, 1, 5.0, 1000)
        assertFalse(result.reachedEnd)
        assertEquals(2, result.nextWaypointIndex)
    }
}
