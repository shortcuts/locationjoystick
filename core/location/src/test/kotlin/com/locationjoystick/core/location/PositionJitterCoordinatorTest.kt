package com.locationjoystick.core.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Regression test for GitHub issue #60: the old interval-gated single Gaussian draw held the
 * anchor exact between fires, then teleported to a random point and back the very next tick.
 * [PositionJitterCoordinator] replaces that with a bounded continuous random walk.
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
    fun `single step moves by at most maxStepMeters toward the target`() {
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
    fun `reaching the target snaps exactly then draws a new one`() {
        val coordinator = PositionJitterCoordinator()
        // maxStepMeters larger than radius guarantees the target is reached in one step every time.
        val (north1, east1) = coordinator.step(2.0, maxStepMeters = 100.0, bearingDeg = 0f, longitudinalFraction = 1.0, Random(1))
        val (north2, east2) = coordinator.step(2.0, maxStepMeters = 100.0, bearingDeg = 0f, longitudinalFraction = 1.0, Random(2))

        // Both offsets are always exactly at whatever target was drawn (huge maxStep => snap immediately).
        assertTrue("First offset should be within radius", hypot(north1, east1) <= 2.0 + 1e-9)
        assertTrue("Second offset should be within radius", hypot(north2, east2) <= 2.0 + 1e-9)
        assertTrue(
            "A fresh target should usually differ from the previous one",
            north1 != north2 || east1 != east2,
        )
    }

    @Test
    fun `reset zeroes offset and target`() {
        val coordinator = PositionJitterCoordinator()
        coordinator.step(10.0, maxStepMeters = 100.0, bearingDeg = 0f, longitudinalFraction = 1.0, Random(1))

        coordinator.reset()

        // maxStepMeters = 0 proves the offset itself is exactly zero (no room to step toward a leftover target).
        val (north, east) = coordinator.step(10.0, maxStepMeters = 0.0, bearingDeg = 0f, longitudinalFraction = 1.0, Random(2))
        assertEquals(0.0, north, 0.0)
        assertEquals(0.0, east, 0.0)
    }

    @Test
    fun `randomTargetInDisc isotropic case stays within radius`() {
        val radius = 5.0
        (1..500).forEach { seed ->
            val (north, east) = randomTargetInDisc(radius, bearingDeg = 0f, longitudinalFraction = 1.0, random = Random(seed))
            val magnitude = hypot(north, east)
            assertTrue("Magnitude $magnitude should be <= radius $radius", magnitude <= radius + 1e-9)
        }
    }

    @Test
    fun `randomTargetInDisc with longitudinalFraction 0 has no along-bearing component`() {
        // bearingDeg = 0 (north): with zero longitudinal fraction, the point is purely lateral (east-west),
        // so the north component must be zero.
        (1..50).forEach { seed ->
            val (north, _) = randomTargetInDisc(6.0, bearingDeg = 0f, longitudinalFraction = 0.0, random = Random(seed))
            assertEquals("North component must be zero for pure lateral jitter (seed $seed)", 0.0, north, 1e-9)
        }
    }
}
