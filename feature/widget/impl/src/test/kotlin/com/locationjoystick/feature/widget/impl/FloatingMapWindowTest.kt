package com.locationjoystick.feature.widget.impl

import android.view.WindowManager
import com.locationjoystick.core.model.FloatingMapRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingMapWindowTest {
    @Test
    fun `compact map uses part of a phone screen`() {
        assertEquals(886, compactFloatingMapWidth(screenWidthPx = 1080, density = 3f))
        assertEquals(1392, compactFloatingMapHeight(screenHeightPx = 2400, density = 3f))
    }

    @Test
    fun `touches outside map window pass through`() {
        assertTrue(floatingMapWindowFlags() and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0)
    }

    private val screenW = 1080
    private val screenH = 2400
    private val density = 3f

    @Test
    fun `default rect uses compact size at the fixed offset`() {
        assertEquals(FloatingMapRect(36, 144, 886, 1392), defaultFloatingMapRect(screenW, screenH, density))
    }

    @Test
    fun `clamp enforces 160 dp minimum and keeps the window on screen`() {
        val clamped = clampFloatingMapRect(FloatingMapRect(1000, 2390, 10, 10), screenW, screenH, density)
        assertEquals(FloatingMapRect(600, 1920, 480, 480), clamped)
    }

    @Test
    fun `clamp caps size at the screen`() {
        val clamped = clampFloatingMapRect(FloatingMapRect(50, 50, 5000, 9000), screenW, screenH, density)
        assertEquals(FloatingMapRect(0, 0, screenW, screenH), clamped)
    }

    @Test
    fun `clamp pulls a landscape window back on screen after rotating to portrait`() {
        val clamped = clampFloatingMapRect(FloatingMapRect(1500, 300, 800, 600), screenW, screenH, density)
        assertEquals(FloatingMapRect(280, 300, 800, 600), clamped)
    }

    @Test
    fun `resize moves right and top while left and bottom stay`() {
        val start = FloatingMapRect(100, 500, 600, 900)
        val resized = resizeFloatingMapRect(start, 800f, 300f, grabDx = 0f, grabDy = 0f, screenW, screenH, density)
        assertEquals(FloatingMapRect(100, 300, 700, 1100), resized)
    }

    @Test
    fun `resize keeps the grab offset so the corner does not jump`() {
        val start = FloatingMapRect(100, 500, 600, 900)
        val resized = resizeFloatingMapRect(start, 690f, 510f, grabDx = 10f, grabDy = -10f, screenW, screenH, density)
        assertEquals(FloatingMapRect(100, 500, 600, 900), resized)
    }

    @Test
    fun `resize below the minimum clamps to 160 dp`() {
        val start = FloatingMapRect(100, 500, 600, 900)
        val resized = resizeFloatingMapRect(start, 0f, 5000f, grabDx = 0f, grabDy = 0f, screenW, screenH, density)
        assertEquals(FloatingMapRect(100, 920, 480, 480), resized)
    }

    @Test
    fun `resize past the screen edge clamps to the screen`() {
        val start = FloatingMapRect(100, 500, 600, 900)
        val resized = resizeFloatingMapRect(start, 5000f, -500f, grabDx = 0f, grabDy = 0f, screenW, screenH, density)
        assertEquals(FloatingMapRect(100, 0, 980, 1400), resized)
    }
}
