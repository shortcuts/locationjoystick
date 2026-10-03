package com.locationjoystick.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LiquidLabelMotionTest {
    @Test
    fun `drop stays circular through separation then expands immediately`() {
        for (step in 0..420) {
            assertEquals(0f, liquidLabelFrame(step / 1000f).expansion, 0f)
        }
        val separated = liquidLabelFrame(0.42f)
        assertEquals(1f, separated.separation, 0f)
        assertEquals(1f, separated.retraction, 0f)
        assertTrue(liquidLabelFrame(0.421f).expansion > 0f)
    }

    @Test
    fun `neck pinches before tips retract and text waits for expansion`() {
        val pinching = liquidLabelFrame(0.28f)
        assertTrue(pinching.pinch in 0.01f..0.99f)
        assertEquals(0f, pinching.retraction, 0f)
        val retracting = liquidLabelFrame(0.37f)
        assertEquals(1f, retracting.pinch, 0f)
        assertTrue(retracting.retraction in 0.01f..0.99f)
        assertTrue(abs(retracting.wobble) > 0f)
        assertEquals(0f, liquidLabelFrame(0.67f).textAlpha, 0f)
        assertTrue(liquidLabelFrame(0.7f).textAlpha > 0f)
    }

    @Test
    fun `every phase is continuous at separation handoff`() {
        val before = liquidLabelFrame(0.41999f)
        val after = liquidLabelFrame(0.42001f)
        assertEquals(before.separation, after.separation, 0.001f)
        assertEquals(before.expansion, after.expansion, 0.001f)
        assertEquals(before.retraction, after.retraction, 0.001f)
        assertEquals(before.wobble, after.wobble, 0.001f)
    }

    @Test
    fun `reverse timeline closes text and label before rejoining source`() {
        val frames = (1000 downTo 0).map { liquidLabelFrame(it / 1000f) }
        frames.zipWithNext().forEach { (a, b) ->
            assertTrue(b.textAlpha <= a.textAlpha)
            assertTrue(b.expansion <= a.expansion)
            assertTrue(b.separation <= a.separation)
            assertTrue(b.expansion == 0f || b.separation == 1f)
        }
        assertEquals(0f, frames.last().separation, 0f)
        assertEquals(0f, frames.last().wobble, 0f)
    }

    @Test
    fun `endpoints clamp and settle without recoil`() {
        assertEquals(liquidLabelFrame(0f), liquidLabelFrame(-1f))
        assertEquals(liquidLabelFrame(1f), liquidLabelFrame(2f))
        val finished = liquidLabelFrame(1f)
        assertEquals(1f, finished.expansion, 0f)
        assertEquals(1f, finished.textAlpha, 0f)
        assertEquals(0f, finished.wobble, 0f)
    }
}
