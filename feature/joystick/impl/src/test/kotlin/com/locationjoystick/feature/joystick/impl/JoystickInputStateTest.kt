package com.locationjoystick.feature.joystick.impl

import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class JoystickInputStateTest {
    private val push = JoystickInput(angleDegrees = 10f, force = 1f)
    private val centered = JoystickInput(angleDegrees = 0f, force = 0f)
    private val job: Job = Job()

    @Test
    fun `idle with force starts a drag`() {
        val state = JoystickInputState.Idle.withInput(push) { job }
        assertEquals(JoystickInputState.Dragging(push, job), state)
    }

    @Test
    fun `idle with centered input stays idle and starts no job`() {
        val state = JoystickInputState.Idle.withInput(centered) { error("no job expected") }
        assertSame(JoystickInputState.Idle, state)
    }

    @Test
    fun `dragging keeps its live job and takes the new input`() {
        val state = JoystickInputState.Dragging(push, job).withInput(centered) { error("no job expected") }
        assertEquals(JoystickInputState.Dragging(centered, job), state)
    }

    @Test
    fun `dragging restarts a dead job on force`() {
        val dead = Job().apply { cancel() }
        val state = JoystickInputState.Dragging(push, dead).withInput(push) { job }
        assertEquals(JoystickInputState.Dragging(push, job), state)
    }

    @Test
    fun `locked idle with force starts a locked movement`() {
        val state = JoystickInputState.LockedIdle.withInput(push) { job }
        assertEquals(JoystickInputState.Locked(push, job), state)
    }

    @Test
    fun `locked keeps the lock and its job on new input`() {
        val state = JoystickInputState.Locked(push, job).withInput(centered) { error("no job expected") }
        assertEquals(JoystickInputState.Locked(centered, job), state)
    }

    @Test
    fun `lock moves idle to locked idle and dragging to locked`() {
        assertSame(JoystickInputState.LockedIdle, JoystickInputState.Idle.locked())
        assertEquals(JoystickInputState.Locked(push, job), JoystickInputState.Dragging(push, job).locked())
    }

    @Test
    fun `locking a locked stick is rejected`() {
        assertNull(JoystickInputState.LockedIdle.locked())
        assertNull(JoystickInputState.Locked(push, job).locked())
    }

    @Test
    fun `release returns idle and dragging to idle`() {
        assertSame(JoystickInputState.Idle, JoystickInputState.Idle.released())
        assertSame(JoystickInputState.Idle, JoystickInputState.Dragging(push, job).released())
    }

    @Test
    fun `releasing a locked stick is rejected so movement continues`() {
        assertNull(JoystickInputState.LockedIdle.released())
        assertNull(JoystickInputState.Locked(push, job).released())
    }
}
