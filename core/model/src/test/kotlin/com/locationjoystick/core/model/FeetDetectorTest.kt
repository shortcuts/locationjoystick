package com.locationjoystick.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.sin

class FeetDetectorTest {
    // Legs under 3 frames have no gradient, so they drop out, as in the solver.
    private fun detect(
        legs: List<List<GrayImage>>,
        w: Int,
        h: Int,
    ): FeetPixel? = FeetDetector.detectFromGradients(legs.mapNotNull { FeetDetector.legGradient(it) }, w, h)

    // Smooth scrolling world (gradient stays under the edge threshold) plus an optional static
    // high-contrast figure above feetFrac, like the bench's _scrolling_legs.
    private fun legs(
        w: Int,
        h: Int,
        feetFrac: Double,
        figure: Boolean = true,
        frames: Int = 4,
        ringFrac: Double? = null,
    ): List<List<GrayImage>> =
        listOf(0 to 9, 9 to 0).map { (dx, dy) ->
            (0 until frames).map { t ->
                val p =
                    FloatArray(w * h) { i ->
                        (128 + 30 * sin(0.03 * (i % w + dx * t) + 0.02 * (i / w + dy * t))).toFloat()
                    }
                if (figure) {
                    val fx = (0.5 * w).toInt()
                    val fy = (feetFrac * h).toInt()

                    fun rect(
                        hw: Int,
                        top: Int,
                        v: Float,
                    ) = (fy - top until fy).forEach { y -> (fx - hw..fx + hw).forEach { x -> p[y * w + x] = v } }
                    rect((0.02 * w).toInt(), (0.05 * h).toInt(), 255f)
                    rect((0.01 * w).toInt(), (0.04 * h).toInt(), 0f)
                }
                if (ringFrac != null) {
                    val ry = (ringFrac * h).toInt()
                    val fx = (0.5 * w).toInt()
                    (ry until ry + (0.01 * h).toInt()).forEach { y ->
                        (fx - (0.05 * w).toInt()..fx + (0.05 * w).toInt()).forEach { x ->
                            p[
                                y *
                                    w +
                                    x,
                            ] =
                                255f
                        }
                    }
                }
                GrayImage(w, h, ByteArray(w * h) { p[it].toInt().toByte() })
            }
        }

    @Test
    fun matchesBenchGolden() {
        val dir = "/feet_golden/"
        val raw = javaClass.getResourceAsStream(dir + "legs.raw")!!.readBytes()
        val (ex, ey) =
            javaClass
                .getResourceAsStream(dir + "expected.txt")!!
                .readBytes()
                .decodeToString()
                .trim()
                .split(" ")
                .map { it.toDouble() }
        val w = 120
        val h = 260
        val frames = raw.size / (w * h)
        val all = (0 until frames).map { f -> GrayImage(w, h, raw.copyOfRange(f * w * h, (f + 1) * w * h)) }
        val got = detect(all.chunked(frames / 2), w, h)!!
        // Tolerance 1.0 px: cv2 and Kotlin differ only in float rounding.
        assertEquals(ex, got.x, 1.0)
        assertEquals(ey, got.y, 1.0)
    }

    @Test
    fun findsFigureAtTwoScreenSizes() {
        for ((w, h) in listOf(1080 to 2340, 720 to 1600)) {
            val p = detect(legs(w, h, 0.66), w, h)
            assertNotNull(p)
            assertEquals(0.5 * w, p!!.x, 0.01 * w)
            assertEquals(0.66 * h, p.y, 0.01 * h)
        }
    }

    @Test
    fun ringMergedIntoFigureRunFallsBackToPrior() {
        // A static bar 0.04h under the feet joins the figure's sharp run; the detected row (~0.70h) is outside
        // the prior tolerance, so the feet row falls back to the prior (~0.64h), not the merged row.
        for ((w, h) in listOf(1080 to 2400, 720 to 1600)) {
            val p = detect(legs(w, h, 0.66, ringFrac = 0.70), w, h)
            assertNotNull(p)
            assertEquals(0.66 * h, p!!.y, 0.025 * h)
        }
    }

    @Test
    fun nullWithoutFigureOrWithTooFewFrames() {
        assertNull(detect(legs(720, 1600, 0.66, figure = false), 720, 1600))
        assertNull(detect(legs(720, 1600, 0.66, frames = 2), 720, 1600))
    }

    @Test(expected = IllegalArgumentException::class)
    fun mismatchedGradientSizeIsRejected() {
        FeetDetector.detectFromGradients(listOf(FloatArray(10 * 20), FloatArray(10 * 19)), 10, 20)
    }

    @Test
    fun toReferenceScalesToReferenceFrame() {
        val (x, y) = FeetPixel(360.0, 800.0).toReference(720, 1600)
        assertEquals(540.0, x, 1e-9)
        assertEquals(1170.0, y, 1e-9)
    }

    @Test
    fun fallbackIsTheCentredPriorRowAtAnySize() {
        for ((w, h) in listOf(1080 to 2400, 720 to 1600)) {
            val p = FeetDetector.fallback(w, h)
            assertEquals(0.5 * w, p.x, 1e-9)
            assertEquals(0.6415 * h, p.y, 1e-9)
        }
    }
}
