package com.locationjoystick.core.common.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RdpSimplificationTest {
    @Test
    fun `single point returns single point`() {
        val points = listOf(LatLng(0.0, 0.0))
        assertEquals(points, rdpSimplify(points, 5.0))
    }

    @Test
    fun `two points returns both points`() {
        val points = listOf(LatLng(0.0, 0.0), LatLng(1.0, 0.0))
        assertEquals(points, rdpSimplify(points, 5.0))
    }

    @Test
    fun `collinear points with small epsilon returns only endpoints`() {
        // Straight line south → north, middle point on the line
        val points =
            listOf(
                LatLng(0.0, 0.0),
                LatLng(0.5, 0.0),
                LatLng(1.0, 0.0),
            )
        val result = rdpSimplify(points, 1.0)
        assertEquals(2, result.size)
        assertEquals(LatLng(0.0, 0.0), result.first())
        assertEquals(LatLng(1.0, 0.0), result.last())
    }

    @Test
    fun `point significantly off line is retained`() {
        // A then B (far off-line) then C — B deviates ~100m from AC
        val a = LatLng(0.0, 0.0)
        val b = LatLng(0.0, 0.001) // ~111m east of the A→C axis
        val c = LatLng(0.001, 0.0) // ~111m north
        val points = listOf(a, b, c)
        val result = rdpSimplify(points, 5.0)
        assertTrue("off-line point should be retained", result.contains(b))
    }

    @Test
    fun `empty list returns empty list`() {
        assertTrue(rdpSimplify(emptyList(), 5.0).isEmpty())
    }

    @Test
    fun `large epsilon collapses all middle points`() {
        val points = (0..10).map { i -> LatLng(i * 0.001, i * 0.0001) }
        val result = rdpSimplify(points, 100_000.0)
        assertEquals(2, result.size)
    }
}
