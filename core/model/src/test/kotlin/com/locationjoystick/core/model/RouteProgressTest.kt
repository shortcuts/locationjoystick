package com.locationjoystick.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteProgressTest {
    @Test
    fun `empty boundaries means no active replay`() {
        assertNull(computeRouteProgress(resumeWaypointIndex = 1, boundaryIndices = emptyList()))
    }

    @Test
    fun `start of a four-stop route is 1 of 4`() {
        val progress = computeRouteProgress(resumeWaypointIndex = 1, boundaryIndices = listOf(0, 1, 2, 3))
        assertEquals(RouteProgress(current = 1, total = 4), progress)
        assertEquals("1/4", progress!!.label)
    }

    @Test
    fun `walking toward the third stop is 2 of 4`() {
        val progress = computeRouteProgress(resumeWaypointIndex = 2, boundaryIndices = listOf(0, 1, 2, 3))
        assertEquals(RouteProgress(current = 2, total = 4), progress)
    }

    @Test
    fun `arrived at the last stop is N of N`() {
        val progress = computeRouteProgress(resumeWaypointIndex = 4, boundaryIndices = listOf(0, 1, 2, 3))
        assertEquals(RouteProgress(current = 4, total = 4), progress)
        assertEquals("4/4", progress!!.label)
    }

    @Test
    fun `road-expanded path counts named stops not extra vertices`() {
        // Named stops at expanded indices 0, 2, 5, 7 — same as Follow-roads jump tests.
        val boundaries = listOf(0, 2, 5, 7)

        assertEquals(
            RouteProgress(1, 4),
            computeRouteProgress(resumeWaypointIndex = 1, boundaryIndices = boundaries),
        )
        // Past the second named stop (index 2), still before the third (index 5).
        assertEquals(
            RouteProgress(2, 4),
            computeRouteProgress(resumeWaypointIndex = 3, boundaryIndices = boundaries),
        )
        assertEquals(
            RouteProgress(4, 4),
            computeRouteProgress(resumeWaypointIndex = 8, boundaryIndices = boundaries),
        )
    }
}
