package com.locationjoystick.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LeaderActivityTest {
    private val progress = RouteProgress(3, 8)
    private val target = LatLng(1.0, 2.0)

    @Test
    fun `manual movement has no activity`() {
        assertNull(leaderActivityOf(MovementState.Manual(MockMode.JOYSTICK), progress, target))
        assertNull(leaderActivityOf(MovementState.Manual(MockMode.TELEPORT), progress, target))
    }

    @Test
    fun `route carries progress and pause`() {
        assertEquals(
            LeaderActivity.Route(false, progress),
            leaderActivityOf(MovementState.Running(ActiveMode.ROUTE_REPLAY), progress, target),
        )
        assertEquals(
            LeaderActivity.Route(true, progress),
            leaderActivityOf(MovementState.Paused(ActiveMode.ROUTE_REPLAY), progress, target),
        )
    }

    @Test
    fun `roaming carries pause`() {
        assertEquals(LeaderActivity.Roaming(false), leaderActivityOf(MovementState.Running(ActiveMode.ROAMING), null, null))
        assertEquals(LeaderActivity.Roaming(true), leaderActivityOf(MovementState.Paused(ActiveMode.ROAMING), null, null))
    }

    @Test
    fun `walk carries target and pause`() {
        assertEquals(LeaderActivity.Walking(false, target), leaderActivityOf(MovementState.Running(ActiveMode.WALK_TO), null, target))
        assertEquals(LeaderActivity.Walking(true, null), leaderActivityOf(MovementState.Paused(ActiveMode.WALK_TO), null, null))
    }

    @Test
    fun `activity is visible only in follower mode`() {
        val activity = LeaderActivity.Roaming(false)
        assertEquals(activity, visibleLeaderActivity(activity, MockMode.FOLLOWER))
        MockMode.entries.filter { it != MockMode.FOLLOWER }.forEach { assertNull(visibleLeaderActivity(activity, it)) }
        assertNull(visibleLeaderActivity(null, MockMode.FOLLOWER))
    }
}
