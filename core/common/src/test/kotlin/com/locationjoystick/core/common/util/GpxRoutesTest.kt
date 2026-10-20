package com.locationjoystick.core.common.util

import org.junit.Assert.assertEquals
import org.junit.Test

class GpxRoutesTest {
    @Test
    fun `points with NaN or out-of-range coords are skipped`() {
        val gpx =
            """
            <gpx version="1.1"><trk><trkseg>
              <trkpt lat="NaN" lon="2.3"/>
              <trkpt lat="48.0" lon="Infinity"/>
              <trkpt lat="200" lon="2.3"/>
              <trkpt lat="48.0" lon="-181"/>
              <trkpt lat="48.0" lon="2.0"/>
              <trkpt lat="48.1" lon="2.1"/>
            </trkseg></trk></gpx>
            """.trimIndent()

        val points = parseGpxRoutes(gpx).single().waypoints

        assertEquals(listOf(48.0 to 2.0, 48.1 to 2.1), points.map { it.latitude to it.longitude })
    }

    @Test
    fun `route with only invalid points is dropped`() {
        val gpx = """<gpx version="1.1"><trk><trkseg><trkpt lat="NaN" lon="2.3"/></trkseg></trk></gpx>"""

        assertEquals(0, parseGpxRoutes(gpx).size)
    }
}
