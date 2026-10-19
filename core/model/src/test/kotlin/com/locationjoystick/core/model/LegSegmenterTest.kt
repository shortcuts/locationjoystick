package com.locationjoystick.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LegSegmenterTest {
    private val start = LatLng(48.8566, 2.3522)

    /** Position [meters] from [from] along [bearing]. */
    private fun at(
        from: LatLng,
        bearing: Double,
        meters: Double,
    ) = CalibrationWalkMath.commandedTarget(from, bearing, meters)

    /** Feeds 3 s samples of 1.4 m/s along [bearing] from [from]; returns the last position and any closed leg. */
    private fun LegSegmenter.walk(
        from: LatLng,
        bearing: Double,
        samples: Int,
        t0: Double,
    ): Pair<LatLng, List<Leg>> {
        val closed = mutableListOf<Leg>()
        var p = from
        for (i in 1..samples) {
            p = at(from, bearing, 4.2 * i)
            add(t0 + 3.0 * i, p)?.let { closed += it }
        }
        return p to closed
    }

    @Test
    fun straightThirtyMeterWalkIsOneLeg() {
        val seg = LegSegmenter()
        seg.add(0.0, start)
        val (_, closed) = seg.walk(start, 90.0, 8, 0.0)
        assertEquals(0, closed.size)
        val leg = seg.close()!!
        assertEquals(9, leg.positions.size)
        assertEquals(90.0, leg.bearingDeg, 0.5)
    }

    @Test
    fun ninetyDegreeTurnClosesTheLeg() {
        val seg = LegSegmenter()
        seg.add(0.0, start)
        val (corner, _) = seg.walk(start, 0.0, 8, 0.0)
        val (_, closed) = seg.walk(corner, 90.0, 1, 24.0)
        assertEquals(1, closed.size)
        assertEquals(0.0, closed[0].bearingDeg, 0.5)
        assertEquals(1, seg.size) // the turning sample opens the next leg
    }

    @Test
    fun standingStillClosesTheLegAndShortLegsAreDropped() {
        val seg = LegSegmenter()
        seg.add(0.0, start)
        val (end, _) = seg.walk(start, 0.0, 7, 0.0)
        assertNotNull(seg.add(24.0, end)) // no movement between samples
        val tiny = LegSegmenter()
        tiny.add(0.0, start)
        tiny.add(3.0, at(start, 0.0, 4.2))
        assertNull(tiny.close())
    }

    @Test
    fun legTooShortForAK4PairIsDropped() {
        // 6 samples 0.45 m apart: the 4-step pair spans 1.8 m, under FrameFlow's 2 m minimum move
        val seg = LegSegmenter()
        for (i in 0..5) seg.add(i.toDouble(), at(start, 90.0, 0.45 * i))
        assertNull(seg.close())
    }

    @Test
    fun legIsCutAtMaxSamples() {
        val seg = LegSegmenter(maxSamples = 10)
        seg.add(0.0, start)
        val (_, closed) = seg.walk(start, 45.0, 9, 0.0)
        assertEquals(1, closed.size)
        assertEquals(10, closed[0].positions.size)
    }
}
