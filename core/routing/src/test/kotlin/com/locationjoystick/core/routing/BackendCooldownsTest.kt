package com.locationjoystick.core.routing

import android.content.SharedPreferences
import com.locationjoystick.core.common.constants.AppConstants
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendCooldownsTest {
    private var now = 1_000L
    private val cooldowns = BackendCooldowns(nowMs = { now })
    private val foot = "https://routing.example.org/routed-foot"
    private val bike = "https://routing.example.org/routed-bike"

    @Test
    fun `429 cools the whole host using Retry-After`() {
        cooldowns.recordRateLimited(foot, 10_000L)
        assertTrue(cooldowns.isCoolingDown(foot))
        assertTrue(cooldowns.isCoolingDown(bike))
        now += 10_000L
        assertFalse(cooldowns.isCoolingDown(foot))
    }

    @Test
    fun `429 without Retry-After uses default`() {
        cooldowns.recordRateLimited(foot, null)
        now += AppConstants.OsrmConstants.COOLDOWN_RATE_LIMITED_MS - 1
        assertTrue(cooldowns.isCoolingDown(foot))
    }

    @Test
    fun `5xx cools only that base url`() {
        cooldowns.recordServerError(foot)
        assertTrue(cooldowns.isCoolingDown(foot))
        assertFalse(cooldowns.isCoolingDown(bike))
    }

    @Test
    fun `cooldown is capped`() {
        cooldowns.recordRateLimited(foot, 10 * 3_600_000L)
        now += AppConstants.OsrmConstants.COOLDOWN_MAX_MS
        assertFalse(cooldowns.isCoolingDown(foot))
    }

    @Test
    fun `persisted future entry is honored and record writes through`() {
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putLong(any(), any()) } returns editor
        val prefs = mockk<SharedPreferences>()
        every { prefs.all } returns mapOf("routing.example.org" to now + 5_000L, "old.example.org" to now - 1)
        every { prefs.edit() } returns editor
        val c = BackendCooldowns(prefs) { now }
        assertTrue(c.isCoolingDown(foot))
        assertFalse(c.isCoolingDown("https://old.example.org/x"))
        c.recordServerError(bike)
        verify { editor.putLong(bike, now + AppConstants.OsrmConstants.COOLDOWN_SERVER_ERROR_MS) }
    }
}
