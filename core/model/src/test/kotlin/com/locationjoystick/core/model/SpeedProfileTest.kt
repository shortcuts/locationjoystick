package com.locationjoystick.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedProfileTest {
    @Test
    fun `defaultProfiles returns exactly five profiles`() {
        assertEquals(5, SpeedProfile.defaultProfiles().size)
    }

    @Test
    fun `defaultProfiles contains slow_walk walk run bike drive ids`() {
        val ids = SpeedProfile.defaultProfiles().map { it.id }.toSet()
        assertEquals(setOf("slow_walk", "walk", "run", "bike", "drive"), ids)
    }

    @Test
    fun `defaultProfiles slow walk speed is exactly 0_3 mps`() {
        val slowWalk = SpeedProfile.defaultProfiles().first { it.id == "slow_walk" }
        assertEquals(0.3, slowWalk.speedMetersPerSecond, 0.001)
    }

    @Test
    fun `defaultProfiles walk speed is 10 kmh`() {
        val walk = SpeedProfile.defaultProfiles().first { it.id == "walk" }
        assertEquals(10.0 / 3.6, walk.speedMetersPerSecond, 0.001)
    }

    @Test
    fun `defaultProfiles run speed is 8 kmh`() {
        val run = SpeedProfile.defaultProfiles().first { it.id == "run" }
        assertEquals(2.2222, run.speedMetersPerSecond, 0.001)
    }

    @Test
    fun `defaultProfiles bike speed is 18_5 kmh`() {
        val bike = SpeedProfile.defaultProfiles().first { it.id == "bike" }
        assertEquals(18.5 / 3.6, bike.speedMetersPerSecond, 0.001)
    }

    @Test
    fun `defaultProfiles drive speed is 60 kmh`() {
        val drive = SpeedProfile.defaultProfiles().first { it.id == "drive" }
        assertEquals(60.0 / 3.6, drive.speedMetersPerSecond, 0.001)
    }

    @Test
    fun `defaultProfiles slow walk is slower than walk`() {
        val profiles = SpeedProfile.defaultProfiles().associateBy { it.id }
        assertTrue(
            "slow_walk should be slower than walk",
            profiles["slow_walk"]!!.speedMetersPerSecond < profiles["walk"]!!.speedMetersPerSecond,
        )
    }

    @Test
    fun `defaultProfiles walk is faster than run`() {
        val profiles = SpeedProfile.defaultProfiles().associateBy { it.id }
        assertTrue(
            "walk (10 km/h) should be faster than run (8 km/h)",
            profiles["walk"]!!.speedMetersPerSecond > profiles["run"]!!.speedMetersPerSecond,
        )
    }

    @Test
    fun `defaultProfiles run is slower than bike`() {
        val profiles = SpeedProfile.defaultProfiles().associateBy { it.id }
        assertTrue(
            "run should be slower than bike",
            profiles["run"]!!.speedMetersPerSecond < profiles["bike"]!!.speedMetersPerSecond,
        )
    }

    @Test
    fun `defaultProfiles bike is slower than drive`() {
        val profiles = SpeedProfile.defaultProfiles().associateBy { it.id }
        assertTrue(
            "bike should be slower than drive",
            profiles["bike"]!!.speedMetersPerSecond < profiles["drive"]!!.speedMetersPerSecond,
        )
    }

    @Test
    fun `defaultProfiles all speeds are positive`() {
        SpeedProfile.defaultProfiles().forEach { profile ->
            assertTrue("${profile.id} speed should be positive", profile.speedMetersPerSecond > 0.0)
        }
    }

    @Test
    fun `defaultProfiles all names are non-empty`() {
        SpeedProfile.defaultProfiles().forEach { profile ->
            assertTrue("${profile.id} name should be non-empty", profile.name.isNotEmpty())
        }
    }

    @Test
    fun `defaultProfiles walk is exactly 10 kmh`() {
        val walk = SpeedProfile.defaultProfiles().first { it.id == "walk" }
        assertEquals(10.0 / 3.6, walk.speedMetersPerSecond, 0.001)
    }

    @Test
    fun `defaultProfiles run is exactly 8 kmh`() {
        val run = SpeedProfile.defaultProfiles().first { it.id == "run" }
        assertEquals(8.0 / 3.6, run.speedMetersPerSecond, 0.001)
    }

    @Test
    fun `defaultProfiles bike is exactly 18_5 kmh`() {
        val bike = SpeedProfile.defaultProfiles().first { it.id == "bike" }
        assertEquals(18.5 / 3.6, bike.speedMetersPerSecond, 0.001)
    }
}
