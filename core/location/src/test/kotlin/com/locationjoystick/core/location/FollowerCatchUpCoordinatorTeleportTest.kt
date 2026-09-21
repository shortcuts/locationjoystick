package com.locationjoystick.core.location

import com.locationjoystick.core.model.MockLocationState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowerCatchUpCoordinatorTeleportTest {
    @Test
    fun `first observed seq is only a baseline`() {
        val c = FollowerCatchUpCoordinator()
        assertFalse(c.observeTeleportSeq(5L))
        assertFalse(c.observeTeleportSeq(5L))
    }

    @Test
    fun `changed seq in either direction is a teleport`() {
        val c = FollowerCatchUpCoordinator()
        c.observeTeleportSeq(1L)
        assertTrue(c.observeTeleportSeq(2L))
        assertTrue(c.observeTeleportSeq(0L))
    }

    @Test
    fun `clear re-baselines the seq`() {
        val c = FollowerCatchUpCoordinator()
        c.observeTeleportSeq(1L)
        c.clear()
        assertFalse(c.observeTeleportSeq(9L))
    }

    @Test
    fun `pausedByLeader is consumed once after a pause`() {
        val c = FollowerCatchUpCoordinator()
        assertFalse(c.consumePausedByLeader())
        c.handleLeaderActiveUpdate(leaderActive = true, currentState = MockLocationState.IDLE)
        c.handleLeaderActiveUpdate(leaderActive = false, currentState = MockLocationState.RUNNING)
        assertTrue(c.consumePausedByLeader())
        assertFalse(c.consumePausedByLeader())
    }

    @Test
    fun `clear resets pausedByLeader`() {
        val c = FollowerCatchUpCoordinator()
        c.handleLeaderActiveUpdate(leaderActive = true, currentState = MockLocationState.IDLE)
        c.handleLeaderActiveUpdate(leaderActive = false, currentState = MockLocationState.RUNNING)
        c.clear()
        assertFalse(c.consumePausedByLeader())
    }

    @Test
    fun `snap only when leader teleported and following and teleport not hidden`() {
        assertTrue(shouldSnapToLeader(true, true, false))
        assertFalse(shouldSnapToLeader(false, true, false))
        assertFalse(shouldSnapToLeader(true, false, false))
        assertFalse(shouldSnapToLeader(true, true, true))
    }
}
