package com.locationjoystick.core.location

import com.locationjoystick.core.model.MockLocationState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the stateful compareAndSet gating in [FollowerCatchUpCoordinator.handleLeaderActiveUpdate]
 * — the pure decision table itself is covered by [LocationLoopActionTest]'s
 * `computeFollowerActiveAction` cases; this test covers the gate that gives each transition
 * exactly one BOOTSTRAP/PAUSE per active/inactive streak.
 */
class FollowerBootstrapActionTest {
    @Test
    fun `bootstraps once then no-ops while the leader stays active`() {
        val coordinator = FollowerCatchUpCoordinator()

        val first = coordinator.handleLeaderActiveUpdate(leaderActive = true, currentState = MockLocationState.IDLE)
        val second = coordinator.handleLeaderActiveUpdate(leaderActive = true, currentState = MockLocationState.RUNNING)

        assertEquals(FollowerBootstrapAction.BOOTSTRAP, first)
        assertEquals(FollowerBootstrapAction.NONE, second)
    }

    @Test
    fun `pauses once then no-ops while the leader stays inactive`() {
        val coordinator = FollowerCatchUpCoordinator()
        coordinator.handleLeaderActiveUpdate(leaderActive = true, currentState = MockLocationState.IDLE)

        val first = coordinator.handleLeaderActiveUpdate(leaderActive = false, currentState = MockLocationState.RUNNING)
        val second = coordinator.handleLeaderActiveUpdate(leaderActive = false, currentState = MockLocationState.IDLE)

        assertEquals(FollowerBootstrapAction.PAUSE, first)
        assertEquals(FollowerBootstrapAction.NONE, second)
    }

    @Test
    fun `bootstraps again after a pause-resume cycle`() {
        val coordinator = FollowerCatchUpCoordinator()
        coordinator.handleLeaderActiveUpdate(leaderActive = true, currentState = MockLocationState.IDLE)
        coordinator.handleLeaderActiveUpdate(leaderActive = false, currentState = MockLocationState.RUNNING)

        val action = coordinator.handleLeaderActiveUpdate(leaderActive = true, currentState = MockLocationState.IDLE)

        assertEquals(FollowerBootstrapAction.BOOTSTRAP, action)
    }

    @Test
    fun `resetBootstrapState allows an immediate re-bootstrap`() {
        val coordinator = FollowerCatchUpCoordinator()
        coordinator.handleLeaderActiveUpdate(leaderActive = true, currentState = MockLocationState.IDLE)

        coordinator.resetBootstrapState()
        val action = coordinator.handleLeaderActiveUpdate(leaderActive = true, currentState = MockLocationState.IDLE)

        assertEquals(FollowerBootstrapAction.BOOTSTRAP, action)
    }
}
