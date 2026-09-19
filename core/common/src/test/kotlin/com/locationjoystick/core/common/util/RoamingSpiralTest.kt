package com.locationjoystick.core.common.util

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.distanceTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RoamingSpiralTest {
    private val center = LatLng(48.8566, 2.3522)

    @Test
    fun `loop starts at the start radius`() {
        val path =
            buildPlantingSpiralLoop(
                center,
                AppConstants.RoamingConstants.PLANTING_START_RADIUS_METERS,
                AppConstants.RoamingConstants.PLANTING_END_RADIUS_METERS,
            )
        assertTrue(path.size > 4)
        val startDist = center.distanceTo(path.first())
        assertEquals(AppConstants.RoamingConstants.PLANTING_START_RADIUS_METERS, startDist, 0.75)
    }

    @Test
    fun `loop reaches the end radius then returns to the start radius`() {
        val startR = 5.0
        val endR = 39.0
        val path = buildPlantingSpiralLoop(center, startR, endR)
        val distances = path.map { center.distanceTo(it) }
        val peak = distances.max()
        val peakIndex = distances.indices.maxBy { distances[it] }
        assertEquals(endR, peak, 1.5)
        assertTrue("peak should be mid-path, was $peakIndex of ${path.lastIndex}", peakIndex in 1 until path.lastIndex)
        assertEquals(startR, distances.last(), 0.75)
    }

    @Test
    fun `one loop is closed so the last point matches the first`() {
        val path = buildPlantingSpiralLoop(center, 5.0, 39.0)
        val gap = path.first().distanceTo(path.last())
        assertTrue("closed-loop gap was $gap m", gap < 1.0)
    }

    @Test
    fun `radii stay inside the ending radius`() {
        val endR = 39.0
        val path = buildPlantingSpiralLoop(center, 5.0, endR)
        path.forEach { point ->
            val dist = center.distanceTo(point)
            assertTrue("point at $dist m exceeds $endR", dist <= endR + 1.5)
        }
    }

    @Test
    fun `swapped start and end radii still expand then contract`() {
        val path = buildPlantingSpiralLoop(center, 39.0, 5.0)
        val distances = path.map { center.distanceTo(it) }
        assertTrue(distances.max() > distances.first() + 1.0)
        assertTrue(abs(distances.last() - distances.first()) < 1.0)
    }

    @Test
    fun `revolutions round to at least two`() {
        assertEquals(2, plantingSpiralRevolutions(5.0, 6.0))
        assertEquals(7, plantingSpiralRevolutions(5.0, 39.0))
    }
}
