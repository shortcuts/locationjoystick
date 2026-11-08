package com.locationjoystick.core.location

import com.locationjoystick.core.model.MockLocationState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpoofingStateMachineTest {
    private val idle = MockLocationState.IDLE
    private val running = MockLocationState.RUNNING
    private val paused = MockLocationState.PAUSED
    private val error = MockLocationState.ERROR

    @Test
    fun `IDLE start goes to RUNNING`() = assertEquals(running, nextSpoofingState(idle, SpoofingEvent.START))

    @Test
    fun `IDLE pause goes to PAUSED`() = assertEquals(paused, nextSpoofingState(idle, SpoofingEvent.PAUSE))

    @Test
    fun `IDLE stop stays IDLE`() = assertEquals(idle, nextSpoofingState(idle, SpoofingEvent.STOP))

    @Test
    fun `IDLE fail goes to ERROR`() = assertEquals(error, nextSpoofingState(idle, SpoofingEvent.FAIL))

    @Test
    fun `RUNNING start stays RUNNING`() = assertEquals(running, nextSpoofingState(running, SpoofingEvent.START))

    @Test
    fun `RUNNING pause goes to PAUSED`() = assertEquals(paused, nextSpoofingState(running, SpoofingEvent.PAUSE))

    @Test
    fun `RUNNING stop goes to IDLE`() = assertEquals(idle, nextSpoofingState(running, SpoofingEvent.STOP))

    @Test
    fun `RUNNING fail goes to ERROR`() = assertEquals(error, nextSpoofingState(running, SpoofingEvent.FAIL))

    @Test
    fun `PAUSED start goes to RUNNING`() = assertEquals(running, nextSpoofingState(paused, SpoofingEvent.START))

    @Test
    fun `PAUSED pause stays PAUSED`() = assertEquals(paused, nextSpoofingState(paused, SpoofingEvent.PAUSE))

    @Test
    fun `PAUSED stop goes to IDLE`() = assertEquals(idle, nextSpoofingState(paused, SpoofingEvent.STOP))

    @Test
    fun `PAUSED fail goes to ERROR`() = assertEquals(error, nextSpoofingState(paused, SpoofingEvent.FAIL))

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
    fun `PAUSED clear error is rejected`() = assertNull(nextSpoofingState(paused, SpoofingEvent.CLEAR_ERROR))

    @Test
    fun `ERROR start is rejected`() = assertNull(nextSpoofingState(error, SpoofingEvent.START))

    @Test
    fun `ERROR pause is rejected`() = assertNull(nextSpoofingState(error, SpoofingEvent.PAUSE))

    @Test
    fun `send returns false and keeps ERROR for a late start`() {
        val flow = MutableStateFlow(error)
        val accepted = SpoofingStateMachine(flow).send(SpoofingEvent.START)

        assertFalse(accepted)
        assertEquals(error, flow.value)
    }

    @Test
    fun `send keeps ERROR for a late pause`() {
        val flow = MutableStateFlow(error)
        val accepted = SpoofingStateMachine(flow).send(SpoofingEvent.PAUSE)

        assertFalse(accepted)
        assertEquals(error, flow.value)
    }

    @Test
    fun `send retry path reaches RUNNING through IDLE`() {
        val flow = MutableStateFlow(error)
        val machine = SpoofingStateMachine(flow)

        assertTrue(machine.send(SpoofingEvent.CLEAR_ERROR))
        assertEquals(idle, flow.value)
        assertTrue(machine.send(SpoofingEvent.START))
        assertEquals(running, flow.value)
    }
}
