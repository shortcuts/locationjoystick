package com.locationjoystick.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

class GroundFlowFitTest {
    private val w = 540
    private val h = 1170

    /** Smooth value noise on a 1.5 m lattice: the ground the camera sees, in metres east/north. */
    private fun ground(
        e: Double,
        n: Double,
    ): Double {
        val x = e / 1.5
        val y = n / 1.5
        val i = floor(x).toInt()
        val j = floor(y).toInt()
        var fx = x - i
        var fy = y - j
        fx = fx * fx * (3 - 2 * fx)
        fy = fy * fy * (3 - 2 * fy)
        val top = lattice(i, j) * (1 - fx) + lattice(i + 1, j) * fx
        val bot = lattice(i, j + 1) * (1 - fx) + lattice(i + 1, j + 1) * fx
        return top * (1 - fy) + bot * fy
    }

    private fun lattice(
        i: Int,
        j: Int,
    ): Double {
        var v = ((i.toLong() * 73856093L) xor (j.toLong() * 19349663L)) and 0xFFFFFFFFL
        v = (v * 2654435761L) and 0xFFFFFFFFL
        return ((v shr 16) and 0xFFL).toDouble()
    }

    /** Screenshot with the player at [east], [north] under [cal]; past the horizon is flat sky. */
    private fun frame(
        cal: GroundCalibration,
        east: Double,
        north: Double,
    ): GrayImage {
        val map = GroundMap(cal, w.toDouble(), h.toDouble())
        val px = ByteArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val g = map.pixelToGround(x.toDouble(), y.toDouble())
                px[y * w + x] = (if (g == null) 200.0 else ground(g.eastMeters + east, g.northMeters + north)).toInt().toByte()
            }
        }
        return GrayImage(w, h, px)
    }

    private fun walk(
        cal: GroundCalibration,
        bearingDeg: Double,
        steps: Int,
        stepM: Double = 4.0,
    ): List<LegMove> {
        val b = Math.toRadians(bearingDeg)
        val crops = (0..steps).map { GroundFlowFit.crops(frame(cal, it * stepM * sin(b), it * stepM * cos(b))) }
        val d = GroundOffset(stepM * sin(b), stepM * cos(b))
        return (0 until steps).mapNotNull { GroundFlowFit.move(crops[it], crops[it + 1], d) }
    }

    private fun angleGap(
        a: Double,
        b: Double,
    ): Double {
        val d = abs(a - b) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }

    @Test
    fun recoversHeadingAndScaleFromOneStraightWalk() {
        for ((heading, scale, bearing) in listOf(Triple(-16.0, 6.25, 90.0), Triple(140.0, 9.3, 0.0), Triple(-4.0, 4.6, 225.0))) {
            val truth = GroundFlowFit.calibration(heading, scale)
            val fit = GroundFlowFit.fit(walk(truth, bearing, 3), w, h)
            assertNotNull("heading $heading", fit)
            assertEquals("heading $heading", 0.0, angleGap(heading, fit!!.calibration.headingDeg), 2.5)
            assertEquals("scale $scale", scale, fit.calibration.lateralPxPerM, scale * 0.05)
        }
    }

    @Test
    fun recoveredMapLandsTapsWithinFivePercentOfTheirDistance() {
        val truth = GroundFlowFit.calibration(-16.0, 6.25)
        val fit = GroundFlowFit.fit(walk(truth, 30.0, 3), w, h)!!
        val a = GroundMap(truth, w.toDouble(), h.toDouble())
        val b = GroundMap(fit.calibration, w.toDouble(), h.toDouble())
        for ((x, y) in listOf(60.0 to 400.0, 270.0 to 500.0, 480.0 to 900.0, 270.0 to 1000.0)) {
            val ga = a.pixelToGround(x, y)!!
            val gb = b.pixelToGround(x, y)!!
            val miss = hypot(ga.eastMeters - gb.eastMeters, ga.northMeters - gb.northMeters)
            assertEquals(0.0, miss, 0.05 * hypot(ga.eastMeters, ga.northMeters))
        }
    }

    @Test
    fun staticScreenGivesNoFit() {
        val still = GrayImage(w, h, ByteArray(w * h) { (it % 7 * 30).toByte() })
        val c = GroundFlowFit.crops(still)
        val moves = (0 until 3).mapNotNull { GroundFlowFit.move(c, c, GroundOffset(4.0, 0.0)) }
        assertNull(GroundFlowFit.fit(moves, w, h))
    }

    /** A PokeMiners loading or warning screen stretched over the phone screen: what the game shows while it is not moving. */
    private fun fixtureScreen(name: String): GrayImage {
        val img = ImageIO.read(javaClass.getResourceAsStream("/pokeminers/$name")!!)
        val px = ByteArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val rgb = img.getRGB(x * img.width / w, y * img.height / h)
                px[y * w + x] = (0.299 * (rgb shr 16 and 0xFF) + 0.587 * (rgb shr 8 and 0xFF) + 0.114 * (rgb and 0xFF)).toInt().toByte()
            }
        }
        return GrayImage(w, h, px)
    }

    @Test
    fun loadingAndWarningScreensGiveNoMove() {
        val names = (1..6).map { "warning_screen_$it.png" } + "loading_0_215_0.png"
        for (name in names) {
            val c = GroundFlowFit.crops(fixtureScreen(name))
            assertNull(name, GroundFlowFit.move(c, c, GroundOffset(4.0, 0.0)))
        }
    }

    private fun fit(
        heading: Double,
        flows: Int = 12,
        median: Double = 1.0,
        scale: Double = 7.0,
    ) = GroundFit(GroundFlowFit.calibration(heading, scale), median, flows)

    @Test
    fun withoutAStoredMapAGoodFitIsKeptAndAWeakOneIsNot() {
        assertEquals(true, GroundFlowFit.replaces(null, fit(-12.0), 2))
        assertEquals(false, GroundFlowFit.replaces(null, fit(-12.0, flows = 4), 2))
        assertEquals(false, GroundFlowFit.replaces(null, fit(-12.0, median = 5.0), 2))
    }

    @Test
    fun aFitThatAgreesWithTheStoredMapReplacesItOnlyPastASmallDrift() {
        val stored = GroundFlowFit.calibration(-12.0, 7.0)
        assertEquals(false, GroundFlowFit.replaces(stored, fit(-11.0, scale = 7.2), 2))
        assertEquals(true, GroundFlowFit.replaces(stored, fit(-3.0, scale = 7.0), 2))
        assertEquals(true, GroundFlowFit.replaces(stored, fit(-12.0, scale = 8.0), 2))
    }

    @Test
    fun aDisagreeingFitReplacesOnlyWhenTheWalkIsLongAndTight() {
        val stored = GroundFlowFit.calibration(-12.0, 7.0)
        assertEquals(false, GroundFlowFit.replaces(stored, fit(60.0), 2))
        assertEquals(false, GroundFlowFit.replaces(stored, fit(60.0, median = 3.0), 4))
        assertEquals(false, GroundFlowFit.replaces(stored, fit(-12.0, scale = 14.0), 2))
        assertEquals(true, GroundFlowFit.replaces(stored, fit(60.0), 3))
    }

    @Test
    fun shortMoveAndSizeChangeAreSkipped() {
        val a = GroundFlowFit.crops(GrayImage(w, h, ByteArray(w * h)))
        val b = GroundFlowFit.crops(GrayImage(w + 2, h, ByteArray((w + 2) * h)))
        assertNull(GroundFlowFit.move(a, a, GroundOffset(0.5, 0.5)))
        assertNull(GroundFlowFit.move(a, b, GroundOffset(4.0, 0.0)))
    }
}
