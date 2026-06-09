package com.locationjoystick.core.data

import com.locationjoystick.core.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CooldownEngineTest {
    // cooldownSecondsForDistance

    @Test
    fun `zero distance returns first tier cooldown`() {
        // Tier 0 has distanceMeters=0.0 and cooldownSeconds=0L
        val result = CooldownEngine.cooldownSecondsForDistance(0.0)
        assertEquals(0L, result)
    }

    @Test
    fun `distance just below second tier returns first tier cooldown`() {
        // Tier 1 starts at 10 m. Distance of 9 m should return tier-0 cooldown (0 s).
        val result = CooldownEngine.cooldownSecondsForDistance(9.9)
        assertEquals(0L, result)
    }

    @Test
    fun `distance exactly at second tier threshold returns that tier cooldown`() {
        // Tier 1: distanceMeters=10.0, cooldownSeconds=3L
        val result = CooldownEngine.cooldownSecondsForDistance(10.0)
        assertEquals(3L, result)
    }

    @Test
    fun `distance above highest tier returns highest tier cooldown`() {
        // Highest tier: distanceMeters=1_000_000.0, cooldownSeconds=7200L
        val result = CooldownEngine.cooldownSecondsForDistance(9_999_999.0)
        assertEquals(7200L, result)
    }

    @Test
    fun `cooldown increases monotonically with distance`() {
        val distances = listOf(0.0, 10.0, 100.0, 500.0, 1_000.0, 5_000.0, 10_000.0, 100_000.0)
        val cooldowns = distances.map { CooldownEngine.cooldownSecondsForDistance(it) }
        for (i in 1 until cooldowns.size) {
            assertTrue("cooldown at index $i should be >= previous", cooldowns[i] >= cooldowns[i - 1])
        }
    }

    // remainingSeconds

    @Test
    fun `remainingSeconds returns 0 when lastTeleportMs is 0`() {
        assertEquals(0L, CooldownEngine.remainingSeconds(0L, 300L))
    }

    @Test
    fun `remainingSeconds returns 0 when cooldownSeconds is 0`() {
        assertEquals(0L, CooldownEngine.remainingSeconds(System.currentTimeMillis(), 0L))
    }

    @Test
    fun `remainingSeconds is positive for very recent teleport with long cooldown`() {
        val result = CooldownEngine.remainingSeconds(System.currentTimeMillis(), 3600L)
        assertTrue(result > 0L)
    }

    @Test
    fun `remainingSeconds does not exceed total cooldown`() {
        val total = 300L
        val result = CooldownEngine.remainingSeconds(System.currentTimeMillis(), total)
        assertTrue(result <= total)
    }

    @Test
    fun `remainingSeconds returns 0 when teleport was long ago`() {
        val longAgo = System.currentTimeMillis() - 1_000_000L // ~16 min ago
        assertEquals(0L, CooldownEngine.remainingSeconds(longAgo, 60L))
    }

    // computeState

    @Test
    fun `computeState returns Ready when lastPosition is null`() {
        val result = CooldownEngine.computeState(
            lastTeleportMs = System.currentTimeMillis(),
            lastPosition = null,
            target = LatLng(0.0, 0.0),
        )
        assertEquals(CooldownState.Ready, result)
    }

    @Test
    fun `computeState returns Ready when lastTeleportMs is 0`() {
        val result = CooldownEngine.computeState(
            lastTeleportMs = 0L,
            lastPosition = LatLng(0.0, 0.0),
            target = LatLng(1.0, 1.0),
        )
        assertEquals(CooldownState.Ready, result)
    }

    @Test
    fun `computeState returns Ready for very short distance (no cooldown tier hit)`() {
        // Distance < 10 m → tier 0 → cooldownSeconds = 0 → Ready
        val result = CooldownEngine.computeState(
            lastTeleportMs = System.currentTimeMillis(),
            lastPosition = LatLng(0.0, 0.0),
            target = LatLng(0.0, 0.00001), // ~1 m
        )
        assertEquals(CooldownState.Ready, result)
    }

    @Test
    fun `computeState returns Cooling for large distance and recent teleport`() {
        val result = CooldownEngine.computeState(
            lastTeleportMs = System.currentTimeMillis(),
            lastPosition = LatLng(0.0, 0.0),
            target = LatLng(10.0, 10.0), // ~1550 km
        )
        assertTrue(result is CooldownState.Cooling)
    }

    @Test
    fun `Cooling state has consistent values`() {
        val result = CooldownEngine.computeState(
            lastTeleportMs = System.currentTimeMillis(),
            lastPosition = LatLng(0.0, 0.0),
            target = LatLng(10.0, 10.0),
        )
        val cooling = result as CooldownState.Cooling
        assertTrue(cooling.remainingSeconds > 0)
        assertTrue(cooling.totalSeconds > 0)
        assertTrue(cooling.remainingSeconds <= cooling.totalSeconds)
        assertTrue(cooling.distanceMeters > 0)
    }

    @Test
    fun `computeState returns Ready after cooldown expires`() {
        // Highest tier is 7200s. Use 3h+ ago to ensure all tiers expired.
        val longAgo = System.currentTimeMillis() - 12_000_000L
        val result = CooldownEngine.computeState(
            lastTeleportMs = longAgo,
            lastPosition = LatLng(0.0, 0.0),
            target = LatLng(10.0, 10.0),
        )
        assertEquals(CooldownState.Ready, result)
    }
}
