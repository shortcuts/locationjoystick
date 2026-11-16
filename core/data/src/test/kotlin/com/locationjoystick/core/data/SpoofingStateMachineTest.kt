package com.locationjoystick.core.data

import com.locationjoystick.core.model.MockLocationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpoofingStateMachineTest {
    private val idle = MockLocationState.IDLE
    private val running = MockLocationState.RUNNING
    private val error = MockLocationState.ERROR

    @Test
    fun `IDLE start goes to RUNNING`() = assertEquals(running, nextSpoofingState(idle, SpoofingEvent.START))

    @Test
    fun `IDLE stop stays IDLE`() = assertEquals(idle, nextSpoofingState(idle, SpoofingEvent.STOP))

    @Test
    fun `IDLE fail goes to ERROR`() = assertEquals(error, nextSpoofingState(idle, SpoofingEvent.FAIL))

    @Test
    fun `RUNNING start stays RUNNING`() = assertEquals(running, nextSpoofingState(running, SpoofingEvent.START))

    @Test
    fun `RUNNING stop goes to IDLE`() = assertEquals(idle, nextSpoofingState(running, SpoofingEvent.STOP))

    @Test
    fun `RUNNING fail goes to ERROR`() = assertEquals(error, nextSpoofingState(running, SpoofingEvent.FAIL))

    @Test
    fun `ERROR stop goes to IDLE`() = assertEquals(idle, nextSpoofingState(error, SpoofingEvent.STOP))

    @Test
    fun `ERROR fail stays ERROR`() = assertEquals(error, nextSpoofingState(error, SpoofingEvent.FAIL))

    @Test
    fun `ERROR clear error goes to IDLE before a retry`() = assertEquals(idle, nextSpoofingState(error, SpoofingEvent.CLEAR_ERROR))

    @Test
    fun `IDLE clear error is rejected`() = assertNull(nextSpoofingState(idle, SpoofingEvent.CLEAR_ERROR))

    @Test
    fun `RUNNING clear error is rejected`() = assertNull(nextSpoofingState(running, SpoofingEvent.CLEAR_ERROR))

    @Test
    fun `ERROR start is rejected`() = assertNull(nextSpoofingState(error, SpoofingEvent.START))

    @Test
    fun `send returns false and keeps ERROR for a late start`() {
        val machine = SpoofingStateMachine(error)

        assertFalse(machine.send(SpoofingEvent.START))
        assertEquals(error, machine.state.value)
    }

    @Test
    fun `send retry path reaches RUNNING through IDLE`() {
        val machine = SpoofingStateMachine(error)

        assertTrue(machine.send(SpoofingEvent.CLEAR_ERROR))
        assertEquals(idle, machine.state.value)
        assertTrue(machine.send(SpoofingEvent.START))
        assertEquals(running, machine.state.value)
    }
}
