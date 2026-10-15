package com.locationjoystick.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class FrameFlowTest {
    // Same procedural scene as scripts/export_flow_golden.py: smooth value noise, a static HUD checkerboard and a ring.
    private companion object {
        const val LATTICE = 10

        /** Integer-hash value 0..255 at lattice point (i, j); same hash as scripts/export_flow_golden.py. */
        fun lattice(
            i: Int,
            j: Int,
        ): Double {
            var h = ((i.toLong() * 73856093L) xor (j.toLong() * 19349663L)) and 0xFFFFFFFFL
            h = (h * 2654435761L) and 0xFFFFFFFFL
            return ((h shr 16) and 0xFFL).toDouble()
        }

        fun world(
            x: Int,
            y: Int,
        ): Int {
            val i = x / LATTICE
            val j = y / LATTICE
            var fx = (x - i * LATTICE).toDouble() / LATTICE
            var fy = (y - j * LATTICE).toDouble() / LATTICE
            fx = fx * fx * (3 - 2 * fx)
            fy = fy * fy * (3 - 2 * fy)
            val top = lattice(i, j) * (1 - fx) + lattice(i + 1, j) * fx
            val bot = lattice(i, j + 1) * (1 - fx) + lattice(i + 1, j + 1) * fx
            val v = 128.0 + 0.9 * ((top * (1 - fy) + bot * fy) - 127.5)
            return Math.rint(v).coerceIn(0.0, 255.0).toInt()
        }
    }

    private class Scene(
        val w: Int,
        val h: Int,
        val feetX: Double,
        val feetY: Double,
    ) {
        private val pad = 90
        private val ww = w + pad
        private val ground = ByteArray(ww * (h + pad))

        init {
            for (y in 0 until h + pad) {
                for (x in 0 until ww) ground[y * ww + x] = world(x, y).toByte()
            }
        }

        /** world(x + dx t, y + dy t) plus the static checkerboard HUD and the ring. */
        fun frame(
            t: Int,
            dx: Int,
            dy: Int,
        ): GrayImage {
            val s = w / 1080.0
            val px = ByteArray(w * h)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    var g = ground[(y + dy * t) * ww + x + dx * t].toInt() and 0xFF
                    if (y >= (900 * s).toInt() && y < (1000 * s).toInt() && x >= (60 * s).toInt() && x < (260 * s).toInt()) {
                        g = if ((x / 4 + y / 4) % 2 == 0) 255 else 0
                    }
                    if (kotlin.math.abs(hypot(x - feetX, y - feetY) - 160.0 * s) <= 3.0) g = 255
                    px[y * w + x] = g.toByte()
                }
            }
            return GrayImage(w, h, px)
        }

        fun leg(
            bearing: Double,
            dx: Int,
            dy: Int,
            frames: Int = 9,
        ): WalkLeg {
            val a = Math.toRadians(bearing)
            return WalkLeg(
                (0 until frames).map { frame(it, dx, dy) },
                List(frames) { true },
                (0 until frames).map { GroundOffset(it * 1.3 * sin(a), it * 1.3 * cos(a)) },
            )
        }
    }

    @Test
    fun matchesBenchGoldenAtReferenceSize() {
        val sc = Scene(1080, 2340, 540.0, 1500.0)
        val moves = FrameFlow.bandMoves(listOf(sc.leg(0.0, 0, 9), sc.leg(90.0, 9, 0)), 540.0, 1500.0, ks = listOf(2, 3))
        val lines =
            javaClass
                .getResourceAsStream("/flow_golden/pairs.txt")!!
                .readBytes()
                .decodeToString()
                .trim()
                .lines()
        assertEquals(lines.size, moves.sumOf { it.bands.size })
        var n = 0
        var pinned = 0
        val weighted = setOf(16, 19) // middle-column bands that overlap the player disc: response is scaled down
        for (m in moves) {
            for ((bi, b) in m.bands.withIndex()) {
                val e = lines[n++].split(" ").map { it.toDouble() }
                assertEquals("dE", e[0], m.d.eastMeters, 1e-3)
                assertEquals("dN", e[1], m.d.northMeters, 1e-3)
                // below 0.2 the fit ignores a flow, so only usable bench flows are pinned (weak ones are noise)
                if (e[4] >= 0.2 && bi !in weighted) {
                    assertEquals("fx band $bi", e[2], b.fx, 1.0)
                    assertEquals("fy band $bi", e[3], b.fy, 1.0)
                    // bands 6 and 9 hold the cleaned HUD: the bench's Telea fill and ours differ, so only their flow is pinned
                    if (bi != 6 && bi != 9) assertEquals("resp band $bi", e[4], b.response, 0.15)
                    pinned++
                }
                assertEquals(e[5], b.cx, 1e-9)
                assertEquals(e[6], b.cy, 1e-9)
            }
        }
        assertTrue("pinned $pinned", pinned >= 100)
    }

    @Test
    fun staticHudAndRingDoNotPullFlowAtAnotherScreenSize() {
        // 720x1600: constants scale by width/1080; the true scroll of 9 px/frame x k frames stays recoverable.
        val w = 720
        val h = 1600
        val fx = 360.0
        val fy = 1500.0 * h / 2340
        val moves = FrameFlow.bandMoves(listOf(Scene(w, h, fx, fy).leg(0.0, 0, 9)), fx, fy, ks = listOf(2))
        assertTrue(moves.isNotEmpty())
        val errors = moves.flatMap { m -> m.bands.filter { it.response >= 0.2 }.map { hypot(it.fx, it.fy + 18.0) } }.sorted()
        // a few weak bands next to the cleaned HUD may be off; the fit's robust weights absorb those
        assertTrue("usable bands ${errors.size}", errors.size >= 100)
        assertTrue("p80 error ${errors[errors.size * 8 / 10]}", errors[errors.size * 8 / 10] < 1.0)
    }

    @Test
    fun bandOverlappingPlayerDiscIsDropped() {
        // disc centred on band 7 (row 2, middle column: centre (360, 616)) covers more than half of it
        val w = 720
        val h = 1600
        val moves = FrameFlow.bandMoves(listOf(Scene(w, h, 360.0, 656.0).leg(0.0, 0, 9)), 360.0, 656.0, ks = listOf(2))
        assertTrue(moves.all { it.bands[7].response == 0.0 })
        assertTrue(moves.any { m -> m.bands[8].response >= 0.2 })
    }

    @Test
    fun cleanLegFillsStaticHudButKeepsMovingGround() {
        val legs = Scene(1080, 2340, 540.0, 1500.0).leg(0.0, 0, 9)
        val clean = FrameFlow.cleanLeg(legs.frames, 540.0, 1500.0)
        val before = legs.frames[0].pixels
        val after = clean[0].pixels

        // the static checkerboard HUD (pure 0 / 255) is replaced by values derived from its surroundings
        fun extremes(p: ByteArray) =
            (900 until 1000).sumOf { y -> (60 until 260).count { x -> (p[y * 1080 + x].toInt() and 0xFF).let { it == 0 || it == 255 } } }
        assertEquals(20000, extremes(before))
        assertTrue("left ${extremes(after)}", extremes(after) < 1000)
        // rows outside the cleaned range are untouched
        assertEquals(before[100 * 1080 + 100], after[100 * 1080 + 100])
    }

    @Test
    fun shortMovesAndStoppedFramesAreSkipped() {
        val a = Scene(540, 1170, 270.0, 750.0).leg(0.0, 0, 9, frames = 3)
        val still = WalkLeg(a.frames, listOf(true, false, true), a.offsets)
        // k = 2 is the only pair with both ends moving; its move is 2.6 m (>= 2 m)
        assertEquals(1, FrameFlow.bandMoves(listOf(still), 270.0, 750.0, ks = listOf(1, 2)).size)
        val tiny = WalkLeg(a.frames, a.moving, a.offsets.map { GroundOffset(it.eastMeters * 0.1, it.northMeters * 0.1) })
        assertEquals(0, FrameFlow.bandMoves(listOf(tiny), 270.0, 750.0, ks = listOf(2)).size)
    }
}
