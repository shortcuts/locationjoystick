package com.locationjoystick.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedProfileTest {
    @Test
    fun `defaultProfiles returns exactly three profiles`() {
        assertEquals(3, SpeedProfile.defaultProfiles().size)
    }

    @Test
    fun `defaultProfiles contains walk run bike ids`() {
        val ids = SpeedProfile.defaultProfiles().map { it.id }.toSet()
        assertEquals(setOf("walk", "run", "bike"), ids)
    }

    @Test
    fun `defaultProfiles walk speed is 1_4 mps`() {
        val walk = SpeedProfile.defaultProfiles().first { it.id == "walk" }
        assertEquals(1.4, walk.speedMetersPerSecond, 0.001)
    }

    @Test
    fun `defaultProfiles run speed is 3_0 mps`() {
        val run = SpeedProfile.defaultProfiles().first { it.id == "run" }
        assertEquals(3.0, run.speedMetersPerSecond, 0.001)
    }

    @Test
    fun `defaultProfiles bike speed is 5_5 mps`() {
        val bike = SpeedProfile.defaultProfiles().first { it.id == "bike" }
        assertEquals(5.5, bike.speedMetersPerSecond, 0.001)
    }

    @Test
    fun `defaultProfiles walk is slower than run`() {
        val profiles = SpeedProfile.defaultProfiles().associateBy { it.id }
        assert(profiles["walk"]!!.speedMetersPerSecond < profiles["run"]!!.speedMetersPerSecond)
    }

    @Test
    fun `defaultProfiles run is slower than bike`() {
        val profiles = SpeedProfile.defaultProfiles().associateBy { it.id }
        assert(profiles["run"]!!.speedMetersPerSecond < profiles["bike"]!!.speedMetersPerSecond)
    }
}
