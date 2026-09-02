package com.locationjoystick.core.location

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
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
    fun `PAUSED with no active job starts the loop`() {
        val action = computePausedLoopAction(hasActiveUpdateJob = false)
        assertEquals(PausedLoopAction.START_UP, action)
    }

    @Test
    fun `PAUSED with an active job keeps it alive`() {
        val action = computePausedLoopAction(hasActiveUpdateJob = true)
        assertEquals(PausedLoopAction.KEEP_ALIVE, action)
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

    @Test
    fun `widget overlay starts while running and not hidden`() {
        val action = computeWidgetOverlayAction(state = MockLocationState.RUNNING, hideWidgetOverlay = false)
        assertEquals(WidgetOverlayAction.START, action)
    }

    @Test
    fun `widget overlay stops while running and hidden`() {
        val action = computeWidgetOverlayAction(state = MockLocationState.RUNNING, hideWidgetOverlay = true)
        assertEquals(WidgetOverlayAction.STOP, action)
    }

    @Test
    fun `widget overlay stops while paused and hidden`() {
        val action = computeWidgetOverlayAction(state = MockLocationState.PAUSED, hideWidgetOverlay = true)
        assertEquals(WidgetOverlayAction.STOP, action)
    }

    @Test
    fun `widget overlay is a no-op while idle regardless of the hide setting`() {
        assertEquals(
            WidgetOverlayAction.NO_OP,
            computeWidgetOverlayAction(state = MockLocationState.IDLE, hideWidgetOverlay = false),
        )
        assertEquals(
            WidgetOverlayAction.NO_OP,
            computeWidgetOverlayAction(state = MockLocationState.IDLE, hideWidgetOverlay = true),
        )
    }

    @Test
    fun `widget overlay is a no-op while in error regardless of the hide setting`() {
        assertEquals(
            WidgetOverlayAction.NO_OP,
            computeWidgetOverlayAction(state = MockLocationState.ERROR, hideWidgetOverlay = false),
        )
        assertEquals(
            WidgetOverlayAction.NO_OP,
            computeWidgetOverlayAction(state = MockLocationState.ERROR, hideWidgetOverlay = true),
        )
    }

    @Test
    fun `TELEPORT uses idle radius isotropically regardless of speed or bearing`() {
        val req =
            resolveJitterStepRequest(MockMode.TELEPORT, speedMs = 0f, bearingDeg = 200f, idleRadiusMeters = 1.0, movingRadiusMeters = 5.0)
        assertEquals(1.0, req.radiusMeters, 0.0)
        assertEquals(0f, req.bearingDeg)
        assertEquals(1.0, req.longitudinalFraction, 0.0)
    }

    @Test
    fun `idle FOLLOWER uses idle radius isotropically`() {
        val req =
            resolveJitterStepRequest(MockMode.FOLLOWER, speedMs = 0f, bearingDeg = 90f, idleRadiusMeters = 2.0, movingRadiusMeters = 5.0)
        assertEquals(2.0, req.radiusMeters, 0.0)
        assertEquals(0f, req.bearingDeg)
        assertEquals(1.0, req.longitudinalFraction, 0.0)
    }

    @Test
    fun `moving FOLLOWER uses moving radius squished along bearing`() {
        val req =
            resolveJitterStepRequest(MockMode.FOLLOWER, speedMs = 1.5f, bearingDeg = 90f, idleRadiusMeters = 2.0, movingRadiusMeters = 5.0)
        assertEquals(5.0, req.radiusMeters, 0.0)
        assertEquals(90f, req.bearingDeg)
        assertEquals(AppConstants.JitterConstants.LONGITUDINAL_JITTER_FRACTION, req.longitudinalFraction, 0.0)
    }

    @Test
    fun `stationary JOYSTICK uses moving radius isotropically`() {
        val req =
            resolveJitterStepRequest(MockMode.JOYSTICK, speedMs = 0f, bearingDeg = 45f, idleRadiusMeters = 2.0, movingRadiusMeters = 5.0)
        assertEquals(5.0, req.radiusMeters, 0.0)
        assertEquals(0f, req.bearingDeg)
        assertEquals(1.0, req.longitudinalFraction, 0.0)
    }

    @Test
    fun `moving JOYSTICK uses moving radius squished along bearing`() {
        val req =
            resolveJitterStepRequest(MockMode.JOYSTICK, speedMs = 1.5f, bearingDeg = 45f, idleRadiusMeters = 2.0, movingRadiusMeters = 5.0)
        assertEquals(5.0, req.radiusMeters, 0.0)
        assertEquals(45f, req.bearingDeg)
        assertEquals(AppConstants.JitterConstants.LONGITUDINAL_JITTER_FRACTION, req.longitudinalFraction, 0.0)
    }

    @Test
    fun `moving ROUTE_REPLAY uses moving radius squished along bearing`() {
        val req =
            resolveJitterStepRequest(
                MockMode.ROUTE_REPLAY,
                speedMs = 2.0f,
                bearingDeg = 10f,
                idleRadiusMeters = 2.0,
                movingRadiusMeters = 5.0,
            )
        assertEquals(5.0, req.radiusMeters, 0.0)
        assertEquals(10f, req.bearingDeg)
        assertEquals(AppConstants.JitterConstants.LONGITUDINAL_JITTER_FRACTION, req.longitudinalFraction, 0.0)
    }
}
