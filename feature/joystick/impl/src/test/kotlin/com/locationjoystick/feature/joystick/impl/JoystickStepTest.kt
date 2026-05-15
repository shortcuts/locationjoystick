package com.locationjoystick.feature.joystick.impl

import com.locationjoystick.core.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for computeJoystickStep().
 *
 * Screen coordinate system: 0°=east, CCW, Y-axis inverted (positive Y = down = south).
 * Finger pushed UP → angleDegrees = 270° (= north bearing after conversion).
 * Finger pushed RIGHT → angleDegrees = 0° (= east bearing).
 */
class JoystickStepTest {
    private val origin = LatLng(48.8566, 2.3522)
    private val speedMs = 1.0
    private val stepSeconds = 0.1

    // angleDegrees=270 → finger up (north in screen coords) → bearing 0° → latitude increases
    @Test
    fun `north input moves latitude up`() {
        val step = computeJoystickStep(origin, angleDegrees = 270f, force = 1f, speedMs = speedMs, stepSeconds = stepSeconds)
        val expectedDistance = speedMs * stepSeconds // 0.1 m
        val expectedDLat = expectedDistance / 111320.0
        assertEquals(origin.latitude + expectedDLat, step.latitude, 1e-9)
        assertEquals(origin.longitude, step.longitude, 1e-6)
    }

    @Test
    fun `zero force returns same position`() {
        val step = computeJoystickStep(origin, angleDegrees = 270f, force = 0f, speedMs = speedMs, stepSeconds = stepSeconds)
        assertEquals(origin.latitude, step.latitude, 1e-12)
        assertEquals(origin.longitude, step.longitude, 1e-12)
    }

    @Test
    fun `10 ticks at walk speed advance expected distance northward`() {
        val walkSpeedMs = 0.5556 // 2 km/h
        var pos = origin
        repeat(10) {
            pos = computeJoystickStep(pos, angleDegrees = 270f, force = 1f, speedMs = walkSpeedMs, stepSeconds = stepSeconds)
        }
        // 10 × 0.5556 m/s × 0.1 s = 0.5556 m north
        val totalMeters = walkSpeedMs * stepSeconds * 10
        val expectedLat = origin.latitude + totalMeters / 111320.0
        assertEquals(expectedLat, pos.latitude, 1e-8)
        assertEquals(origin.longitude, pos.longitude, 1e-6)
    }

    @Test
    fun `half force halves distance`() {
        val full = computeJoystickStep(origin, angleDegrees = 270f, force = 1f, speedMs = speedMs, stepSeconds = stepSeconds)
        val half = computeJoystickStep(origin, angleDegrees = 270f, force = 0.5f, speedMs = speedMs, stepSeconds = stepSeconds)
        val fullDLat = full.latitude - origin.latitude
        val halfDLat = half.latitude - origin.latitude
        assertEquals(fullDLat / 2.0, halfDLat, 1e-12)
    }

    // angleDegrees=315 → finger up-right → bearing ~45° (NE) → lat and lon both increase
    @Test
    fun `northeast input advances in both axes`() {
        val step = computeJoystickStep(origin, angleDegrees = 315f, force = 1f, speedMs = speedMs, stepSeconds = stepSeconds)
        assertTrue("Expected northward movement", step.latitude > origin.latitude)
        assertTrue("Expected eastward movement", step.longitude > origin.longitude)
    }
}
