package com.locationjoystick.core.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Regression tests for GitHub issue #60. The interval-gated single Gaussian draw held the anchor
 * exact between fires, then teleported to a random point and back the very next tick; the first
 * random-walk fix beelined to one distant target per leg, which read as a DVD-logo bounce off the
 * deviation radius (issue #60 follow-up). [PositionJitterCoordinator] redraws its heading every
 * tick instead, so it wanders continuously without a persistent direction.
 */
class PositionJitterCoordinatorTest {
    @Test
    fun `radius zero resets and returns zero offset`() {
        val coordinator = PositionJitterCoordinator()
        // Seed a nonzero offset first via a normal step.
        coordinator.step(5.0, 1.0, 0f, 1.0, Random(1))

        val (north, east) = coordinator.step(0.0, 1.0, 0f, 1.0, Random(1))

        assertEquals(0.0, north, 0.0)
        assertEquals(0.0, east, 0.0)
    }

    @Test
    fun `offset magnitude never exceeds radius across many ticks`() {
        val coordinator = PositionJitterCoordinator()
        val random = Random(42)
        val radius = 10.0
        repeat(2000) {
            val (north, east) = coordinator.step(radius, maxStepMeters = 1.0, bearingDeg = 0f, longitudinalFraction = 1.0, random)
            val magnitude = hypot(north, east)
            assertTrue("Offset magnitude $magnitude exceeded radius $radius", magnitude <= radius + 1e-9)
        }
    }

    @Test
    fun `single step moves by at most maxStepMeters`() {
        val coordinator = PositionJitterCoordinator()
        val random = Random(7)
        var (prevNorth, prevEast) = coordinator.step(10.0, maxStepMeters = 1.0, bearingDeg = 0f, longitudinalFraction = 1.0, random)
        repeat(50) {
            val (north, east) = coordinator.step(10.0, maxStepMeters = 1.0, bearingDeg = 0f, longitudinalFraction = 1.0, random)
            val stepDist = hypot(north - prevNorth, east - prevEast)
            assertTrue("Step distance $stepDist exceeded maxStepMeters", stepDist <= 1.0 + 1e-9)
            prevNorth = north
            prevEast = east
        }
    }

    @Test
    fun `reset zeroes offset`() {
        val coordinator = PositionJitterCoordinator()
        coordinator.step(10.0, maxStepMeters = 5.0, bearingDeg = 0f, longitudinalFraction = 1.0, Random(1))

        coordinator.reset()

        // maxStepMeters = 0 proves the offset itself is exactly zero (no room to step away from it).
        val (north, east) = coordinator.step(10.0, maxStepMeters = 0.0, bearingDeg = 0f, longitudinalFraction = 1.0, Random(2))
        assertEquals(0.0, north, 0.0)
        assertEquals(0.0, east, 0.0)
    }

    @Test
    fun `heading is redrawn every tick instead of beelining to a distant target`() {
        // Large radius, small step: if the coordinator still beelined to one far target (the
        // DVD-logo bug), every step in this run would point the same direction. With a
        // per-tick random heading, consecutive step vectors should disagree in direction well
        // before the disc edge is ever reached.
        val coordinator = PositionJitterCoordinator()
        val random = Random(123)
        val radius = 1000.0
        var prevNorth = 0.0
        var prevEast = 0.0
        var prevDNorth: Double? = null
        var prevDEast: Double? = null
        var sawDirectionChange = false
        repeat(20) {
            val (north, east) = coordinator.step(radius, maxStepMeters = 1.0, bearingDeg = 0f, longitudinalFraction = 1.0, random)
            val dNorth = north - prevNorth
            val dEast = east - prevEast
            if (prevDNorth != null && (dNorth != prevDNorth || dEast != prevDEast)) sawDirectionChange = true
            prevDNorth = dNorth
            prevDEast = dEast
            prevNorth = north
            prevEast = east
        }
        assertTrue("Expected step direction to vary tick-to-tick, not hold a fixed heading", sawDirectionChange)
    }

    @Test
    fun `longitudinalFraction zero keeps offset purely lateral to bearing`() {
        // bearingDeg = 0 (north): with zero longitudinal fraction, the offset must stay purely
        // lateral (east-west), so the north component should never move off zero.
        val coordinator = PositionJitterCoordinator()
        val random = Random(9)
        repeat(200) {
            val (north, _) = coordinator.step(6.0, maxStepMeters = 1.0, bearingDeg = 0f, longitudinalFraction = 0.0, random)
            assertEquals("North component must be zero for pure lateral jitter", 0.0, north, 1e-9)
        }
    }
}
