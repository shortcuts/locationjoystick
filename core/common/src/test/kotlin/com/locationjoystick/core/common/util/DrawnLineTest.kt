package com.locationjoystick.core.common.util

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class DrawnLineTest {
    private val origin = LatLng(10.0, 10.0)
    private val oneDegreeLatMeters = 111_000.0

    private fun north(meters: Double) = LatLng(origin.latitude + meters / oneDegreeLatMeters, origin.longitude)

    @Test
    fun `toggle switches between off and armed`() {
        assertEquals(DrawLineState.Armed, DrawLineState.Off.toggled())
        assertEquals(DrawLineState.Off, DrawLineState.Armed.toggled())
        assertEquals(DrawLineState.Off, DrawLineState.Drawing(listOf(origin)).toggled())
    }

    @Test
    fun `start only works from armed`() {
        assertEquals(DrawLineState.Drawing(listOf(origin)), DrawLineState.Armed.started(origin))
        assertEquals(DrawLineState.Off, DrawLineState.Off.started(origin))
        val drawing = DrawLineState.Drawing(listOf(origin))
        assertEquals(drawing, drawing.started(north(50.0)))
    }

    @Test
    fun `extend ignores points under the minimum spacing`() {
        val drawing = DrawLineState.Drawing(listOf(origin))
        assertEquals(drawing, drawing.extended(north(0.5)))
        assertEquals(2, (drawing.extended(north(5.0)) as DrawLineState.Drawing).points.size)
    }

    @Test
    fun `extend is ignored outside drawing`() {
        assertEquals(DrawLineState.Armed, DrawLineState.Armed.extended(north(50.0)))
        assertEquals(DrawLineState.Off, DrawLineState.Off.extended(north(50.0)))
    }

    @Test
    fun `finish returns to armed with the line`() {
        val (state, line) = DrawLineState.Drawing(listOf(origin, north(100.0))).finished()
        assertEquals(DrawLineState.Armed, state)
        assertEquals(listOf(origin, north(100.0)), line)
    }

    @Test
    fun `finish is ignored outside drawing`() {
        assertEquals(DrawLineState.Armed to null, DrawLineState.Armed.finished())
        assertEquals(DrawLineState.Off to null, DrawLineState.Off.finished())
    }

    @Test
    fun `a single point is no line`() {
        assertNull(finishDrawnLine(listOf(origin)))
    }

    @Test
    fun `collinear points collapse to the endpoints`() {
        val line = List(20) { north(it * 10.0) }
        assertEquals(listOf(line.first(), line.last()), finishDrawnLine(line))
    }

    @Test
    fun `a long wave is capped at the route limit and keeps both ends`() {
        val max = AppConstants.ExportConstants.MAX_GPX_ROUTE_WAYPOINTS
        val zigzag = List(max * 2) { LatLng(origin.latitude + it * 0.0003, origin.longitude + 0.01 * sin(it * PI / 10)) }
        val line = finishDrawnLine(zigzag)!!
        assertEquals(max, line.size)
        assertEquals(zigzag.first(), line.first())
        assertEquals(zigzag.last(), line.last())
        assertTrue(line.zipWithNext().all { (a, b) -> a != b })
    }

    @Test
    fun `the cap keeps the true end point when float steps would drift`() {
        val spiral =
            List(10_004) {
                LatLng(
                    origin.latitude + (0.0007 + it * 1e-8) * sin(it * 0.5),
                    origin.longitude + (0.0007 + it * 1e-8) * cos(it * 0.5),
                )
            }
        val line = finishDrawnLine(spiral)!!
        assertEquals(AppConstants.ExportConstants.MAX_GPX_ROUTE_WAYPOINTS, line.size)
        assertEquals(spiral.last(), line.last())
    }
}
