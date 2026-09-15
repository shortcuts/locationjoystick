package com.locationjoystick.core.routing

import com.locationjoystick.core.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteReplayTeleportTest {
    private val a = LatLng(0.0, 0.0)
    private val b = LatLng(0.001, 0.0)
    private val c = LatLng(0.002, 0.0)
    private val waypoints = listOf(a, b, c)
    private val identity = waypoints.indices.toList()

    @Test
    fun `identity boundaries hop every stop after the start`() {
        assertFalse(shouldTeleportToTargetWaypoint(true, 0, identity))
        assertTrue(shouldTeleportToTargetWaypoint(true, 1, identity))
        assertTrue(shouldTeleportToTargetWaypoint(true, 2, identity))
    }

    @Test
    fun `flag off never hops`() {
        assertFalse(shouldTeleportToTargetWaypoint(false, 1, identity))
    }

    @Test
    fun `planting ring vertices are not hop targets`() {
        val ringThenNext = listOf(0, 8)
        assertFalse(shouldTeleportToTargetWaypoint(true, 1, ringThenNext))
        assertFalse(shouldTeleportToTargetWaypoint(true, 7, ringThenNext))
        assertTrue(shouldTeleportToTargetWaypoint(true, 8, ringThenNext))
    }

    @Test
    fun `leftover carry snaps back to the ring-exit vertex`() {
        val ringExit = LatLng(0.0001, 0.0)
        val nextRingStart = LatLng(0.01, 0.0)
        val path = listOf(a, ringExit, nextRingStart)
        val carried =
            InterpolationResult(
                position = LatLng(0.005, 0.0),
                nextWaypointIndex = 2,
                reachedEnd = false,
            )
        val snapped = snapCarryIfCrossingBoundary(carried, path, true, listOf(0, 2))
        assertEquals(ringExit, snapped.position)
        assertEquals(2, snapped.nextWaypointIndex)
        assertFalse(snapped.reachedEnd)
    }

    @Test
    fun `leftover carry is unchanged when the next index is not a boundary`() {
        val carried =
            InterpolationResult(
                position = b,
                nextWaypointIndex = 1,
                reachedEnd = false,
            )
        val snapped = snapCarryIfCrossingBoundary(carried, waypoints, true, listOf(0, 2))
        assertEquals(carried, snapped)
    }
}
