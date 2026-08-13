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
        assertEquals(IdleOrErrorLoopAction.KEEP_ALIVE, action)
    }

    @Test
    fun `IDLE with leader sharing keeps loop alive even with no active job`() {
        val action =
            computeIdleOrErrorLoopAction(
                state = MockLocationState.IDLE,
                leaderSharingEnabled = true,
                hasActiveUpdateJob = false,
            )
        assertEquals(IdleOrErrorLoopAction.KEEP_ALIVE, action)
    }

    @Test
    fun `IDLE without leader sharing tears down when a job is active`() {
        val action =
            computeIdleOrErrorLoopAction(
                state = MockLocationState.IDLE,
                leaderSharingEnabled = false,
                hasActiveUpdateJob = true,
            )
        assertEquals(IdleOrErrorLoopAction.TEAR_DOWN, action)
    }

    @Test
    fun `IDLE without leader sharing and no active job is a no-op`() {
        val action =
            computeIdleOrErrorLoopAction(
                state = MockLocationState.IDLE,
                leaderSharingEnabled = false,
                hasActiveUpdateJob = false,
            )
        assertEquals(IdleOrErrorLoopAction.NO_OP, action)
    }

    @Test
    fun `ERROR always tears down when a job is active, even with leader sharing enabled`() {
        val action =
            computeIdleOrErrorLoopAction(
                state = MockLocationState.ERROR,
                leaderSharingEnabled = true,
                hasActiveUpdateJob = true,
            )
        assertEquals(IdleOrErrorLoopAction.TEAR_DOWN, action)
    }

    @Test
    fun `ERROR without an active job is a no-op regardless of leader sharing`() {
        val action =
            computeIdleOrErrorLoopAction(
                state = MockLocationState.ERROR,
                leaderSharingEnabled = true,
                hasActiveUpdateJob = false,
            )
        assertEquals(IdleOrErrorLoopAction.NO_OP, action)
    }

    @Test
    fun `PAUSED with leader sharing and no active job starts the loop`() {
        val action = computePausedLoopAction(leaderSharingEnabled = true, hasActiveUpdateJob = false)
        assertEquals(PausedLoopAction.START_UP, action)
    }

    @Test
    fun `PAUSED with leader sharing and an active job keeps it alive`() {
        val action = computePausedLoopAction(leaderSharingEnabled = true, hasActiveUpdateJob = true)
        assertEquals(PausedLoopAction.KEEP_ALIVE, action)
    }

    @Test
    fun `PAUSED without leader sharing tears down an active job`() {
        val action = computePausedLoopAction(leaderSharingEnabled = false, hasActiveUpdateJob = true)
        assertEquals(PausedLoopAction.TEAR_DOWN, action)
    }

    @Test
    fun `PAUSED without leader sharing and no active job is a no-op`() {
        val action = computePausedLoopAction(leaderSharingEnabled = false, hasActiveUpdateJob = false)
        assertEquals(PausedLoopAction.NO_OP, action)
    }

    @Test
    fun `follower not yet spoofing bootstraps when leader is active`() {
        val action =
            computeFollowerActiveAction(
                leaderActive = true,
                spoofingStarted = false,
                currentState = MockLocationState.IDLE,
            )
        assertEquals(FollowerActiveAction.BOOTSTRAP, action)
    }

    @Test
    fun `follower already running does not re-bootstrap while leader stays active`() {
        val action =
            computeFollowerActiveAction(
                leaderActive = true,
                spoofingStarted = true,
                currentState = MockLocationState.RUNNING,
            )
        assertEquals(FollowerActiveAction.NO_OP, action)
    }

    @Test
    fun `spoofing follower pauses when leader goes inactive`() {
        val action =
            computeFollowerActiveAction(
                leaderActive = false,
                spoofingStarted = true,
                currentState = MockLocationState.RUNNING,
            )
        assertEquals(FollowerActiveAction.PAUSE, action)
    }

    @Test
    fun `already-paused follower is a no-op while leader stays inactive`() {
        val action =
            computeFollowerActiveAction(
                leaderActive = false,
                spoofingStarted = false,
                currentState = MockLocationState.IDLE,
            )
        assertEquals(FollowerActiveAction.NO_OP, action)
    }

    @Test
    fun `leader active but follower not yet running and never bootstrapped is a bootstrap`() {
        val action =
            computeFollowerActiveAction(
                leaderActive = true,
                spoofingStarted = false,
                currentState = MockLocationState.PAUSED,
            )
        assertEquals(FollowerActiveAction.BOOTSTRAP, action)
    }
}
