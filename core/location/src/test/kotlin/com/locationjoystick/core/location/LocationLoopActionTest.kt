package com.locationjoystick.core.location

import com.locationjoystick.core.model.MockLocationState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression coverage for the group-sync leader bug: a natural IDLE transition (walk/replay
 * completion) must not tear down the test provider while the device is broadcasting as a
 * group-sync leader, but ERROR must always tear down regardless of leader state.
 */
class LocationLoopActionTest {
    @Test
    fun `IDLE with leader sharing keeps loop alive even with an active job`() {
        val action =
            computeIdleOrErrorLoopAction(
                state = MockLocationState.IDLE,
                leaderSharingEnabled = true,
                hasActiveUpdateJob = true,
            )
        assertEquals(LocationLoopAction.KEEP_ALIVE, action)
    }

    @Test
    fun `IDLE with leader sharing keeps loop alive even with no active job`() {
        val action =
            computeIdleOrErrorLoopAction(
                state = MockLocationState.IDLE,
                leaderSharingEnabled = true,
                hasActiveUpdateJob = false,
            )
        assertEquals(LocationLoopAction.KEEP_ALIVE, action)
    }

    @Test
    fun `IDLE without leader sharing tears down when a job is active`() {
        val action =
            computeIdleOrErrorLoopAction(
                state = MockLocationState.IDLE,
                leaderSharingEnabled = false,
                hasActiveUpdateJob = true,
            )
        assertEquals(LocationLoopAction.TEAR_DOWN, action)
    }

    @Test
    fun `IDLE without leader sharing and no active job is a no-op`() {
        val action =
            computeIdleOrErrorLoopAction(
                state = MockLocationState.IDLE,
                leaderSharingEnabled = false,
                hasActiveUpdateJob = false,
            )
        assertEquals(LocationLoopAction.NO_OP, action)
    }

    @Test
    fun `ERROR always tears down when a job is active, even with leader sharing enabled`() {
        val action =
            computeIdleOrErrorLoopAction(
                state = MockLocationState.ERROR,
                leaderSharingEnabled = true,
                hasActiveUpdateJob = true,
            )
        assertEquals(LocationLoopAction.TEAR_DOWN, action)
    }

    @Test
    fun `ERROR without an active job is a no-op regardless of leader sharing`() {
        val action =
            computeIdleOrErrorLoopAction(
                state = MockLocationState.ERROR,
                leaderSharingEnabled = true,
                hasActiveUpdateJob = false,
            )
        assertEquals(LocationLoopAction.NO_OP, action)
    }
}
