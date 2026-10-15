package com.locationjoystick.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class FrameFlowTest {
    @Test
    fun matchesBenchGoldenAtReferenceSize() {
        val sc = SyntheticWalkScene(1080, 2340, 540.0, 1500.0)
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
        val moves = FrameFlow.bandMoves(listOf(SyntheticWalkScene(w, h, fx, fy).leg(0.0, 0, 9)), fx, fy, ks = listOf(2))
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
        val moves = FrameFlow.bandMoves(listOf(SyntheticWalkScene(w, h, 360.0, 656.0).leg(0.0, 0, 9)), 360.0, 656.0, ks = listOf(2))
        assertTrue(moves.all { it.bands[7].response == 0.0 })
        assertTrue(moves.any { m -> m.bands[8].response >= 0.2 })
    }

    @Test
    fun cleanLegFillsStaticHudButKeepsMovingGround() {
        val legs = SyntheticWalkScene(1080, 2340, 540.0, 1500.0).leg(0.0, 0, 9)
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
        val a = SyntheticWalkScene(540, 1170, 270.0, 750.0).leg(0.0, 0, 9, frames = 3)
        val still = WalkLeg(a.frames, listOf(true, false, true), a.offsets)
        // k = 2 is the only pair with both ends moving; its move is 2.6 m (>= 2 m)
        assertEquals(1, FrameFlow.bandMoves(listOf(still), 270.0, 750.0, ks = listOf(1, 2)).size)
        val tiny = WalkLeg(a.frames, a.moving, a.offsets.map { GroundOffset(it.eastMeters * 0.1, it.northMeters * 0.1) })
        assertEquals(0, FrameFlow.bandMoves(listOf(tiny), 270.0, 750.0, ks = listOf(2)).size)
    }
}
