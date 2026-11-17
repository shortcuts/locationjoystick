package com.locationjoystick.core.location

import android.os.PowerManager
import com.locationjoystick.core.model.ActiveMode
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.MovementState
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
    fun `lock is needed while an engine runs or a manual mode moves`() {
        val moving =
            ActiveMode.entries.map { MovementState.Running(it) } +
                listOf(MockMode.JOYSTICK, MockMode.FOLLOWER).map { MovementState.Manual(it) }
        moving.forEach { assertTrue("$it", needsSpoofingWakeLock(MockLocationState.RUNNING, it, steering = false)) }
        assertFalse(needsSpoofingWakeLock(MockLocationState.RUNNING, MovementState.Manual(MockMode.TELEPORT), steering = false))
    }

    @Test
    fun `a paused engine holds the lock only while the joystick steers`() {
        val paused =
            ActiveMode.entries.map { MovementState.Paused(it) } +
                ActiveMode.entries.map { MovementState.PausePending(MockMode.TELEPORT, it) }
        listOf(MockLocationState.RUNNING, MockLocationState.PAUSED).forEach { state ->
            paused.forEach { movement ->
                assertFalse("$state $movement", needsSpoofingWakeLock(state, movement, steering = false))
                assertTrue("$state $movement", needsSpoofingWakeLock(state, movement, steering = true))
            }
        }
    }

    @Test
    fun `lock is never needed while idle or in error`() {
        val all =
            ActiveMode.entries.flatMap { listOf(MovementState.Running(it), MovementState.Paused(it)) } +
                MockMode.entries.map { MovementState.Manual(it) }
        listOf(MockLocationState.IDLE, MockLocationState.ERROR).forEach { state ->
            all.forEach { movement ->
                listOf(false, true).forEach { steering ->
                    assertFalse("$state $movement $steering", needsSpoofingWakeLock(state, movement, steering))
                }
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
