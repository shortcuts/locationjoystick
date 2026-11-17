package com.locationjoystick.core.location

import android.os.PowerManager
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpoofingWakeLockTest {
    @Test
    fun `acquire returns held wakelock on success`() {
        val wakeLock = mockk<PowerManager.WakeLock>(relaxed = true)
        val powerManager =
            mockk<PowerManager> {
                every { newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, any()) } returns wakeLock
            }

        val result = acquireSpoofingWakeLock(powerManager)

        assertNotNull(result)
        verify { wakeLock.acquire() }
    }

    @Test
    fun `acquire returns null when PowerManager throws`() {
        val powerManager =
            mockk<PowerManager> {
                every { newWakeLock(any(), any()) } throws RuntimeException("boom")
            }

        val result = acquireSpoofingWakeLock(powerManager)

        assertNull(result)
    }

    @Test
    fun `release releases a held wakelock`() {
        val wakeLock =
            mockk<PowerManager.WakeLock>(relaxed = true) {
                every { isHeld } returns true
            }

        releaseSpoofingWakeLock(wakeLock)

        verify { wakeLock.release() }
    }

    @Test
    fun `release is a no-op when wakelock is not held`() {
        val wakeLock =
            mockk<PowerManager.WakeLock>(relaxed = true) {
                every { isHeld } returns false
            }

        releaseSpoofingWakeLock(wakeLock)

        verify(exactly = 0) { wakeLock.release() }
    }

    @Test
    fun `release is a no-op when wakelock is null`() {
        releaseSpoofingWakeLock(null)
    }

    @Test
    fun `lock is needed only while running and moving`() {
        val moving = listOf(MockMode.JOYSTICK, MockMode.ROUTE_REPLAY, MockMode.ROAMING, MockMode.WALK_TO, MockMode.FOLLOWER)
        moving.forEach { assertTrue("$it", needsSpoofingWakeLock(MockLocationState.RUNNING, it)) }
        assertFalse(needsSpoofingWakeLock(MockLocationState.RUNNING, MockMode.TELEPORT))
        MockMode.entries.forEach { mode ->
            listOf(MockLocationState.IDLE, MockLocationState.PAUSED, MockLocationState.ERROR).forEach { state ->
                assertFalse("$state $mode", needsSpoofingWakeLock(state, mode))
            }
        }
    }

    @Test
    fun `holder acquiring twice keeps one held lock`() {
        val wakeLock = mockk<PowerManager.WakeLock>(relaxed = true) { every { isHeld } returns true }
        val pm = mockk<PowerManager> { every { newWakeLock(any(), any()) } returns wakeLock }
        val holder = SpoofingWakeLockHolder { pm }

        holder.acquire()
        holder.acquire()

        verify(exactly = 1) { pm.newWakeLock(any(), any()) }
    }
}
