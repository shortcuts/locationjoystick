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

    // Pins the fix for "route replay silently stalling": a redundant RUNNING transition while
    // ROUTE_REPLAY is active (e.g. a re-post unrelated to the replay reaching observeLocationState
    // again) must never start the idle loop, which would race ReplayOrchestrator's own ticks.
    @Test
    fun `RUNNING in ROUTE_REPLAY stops the idle loop even when no job is active`() {
        val action = computeRunningLoopAction(mode = MockMode.ROUTE_REPLAY, hasActiveUpdateJob = false)
        assertEquals(RunningLoopAction.STOP_IDLE_LOOP_FOR_REPLAY, action)
    }

    @Test
    fun `RUNNING in ROUTE_REPLAY stops the idle loop when a job is active from a prior pause`() {
        val action = computeRunningLoopAction(mode = MockMode.ROUTE_REPLAY, hasActiveUpdateJob = true)
        assertEquals(RunningLoopAction.STOP_IDLE_LOOP_FOR_REPLAY, action)
    }

    @Test
    fun `RUNNING outside ROUTE_REPLAY starts the idle loop when none is active`() {
        val action = computeRunningLoopAction(mode = MockMode.JOYSTICK, hasActiveUpdateJob = false)
        assertEquals(RunningLoopAction.START_IDLE_LOOP, action)
    }

    @Test
    fun `RUNNING outside ROUTE_REPLAY is a no-op when the idle loop is already active`() {
        val action = computeRunningLoopAction(mode = MockMode.JOYSTICK, hasActiveUpdateJob = true)
        assertEquals(RunningLoopAction.NO_OP, action)
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
    fun `idle overlay stop keeps the widget when parked`() {
        assertEquals(IdleOverlayStopAction.STOP_JOYSTICK_ONLY, computeIdleOverlayStopAction(keepWidgetOverlay = true))
    }

    @Test
    fun `idle overlay stop tears down both overlays when not parked`() {
        assertEquals(
            IdleOverlayStopAction.STOP_JOYSTICK_AND_WIDGET,
            computeIdleOverlayStopAction(keepWidgetOverlay = false),
        )
    }

    @Test
    fun `full stop after park still tears down the widget`() {
        assertEquals(
            IdleOverlayStopAction.STOP_JOYSTICK_AND_WIDGET,
            computeOverlayStopAction(OverlayStopTrigger.FULL_STOP, keepWidgetOverlay = true),
        )
    }

    @Test
    fun `idle collector after park keeps the widget`() {
        assertEquals(
            IdleOverlayStopAction.STOP_JOYSTICK_ONLY,
            computeOverlayStopAction(OverlayStopTrigger.STATE_IDLE, keepWidgetOverlay = true),
        )
    }

    @Test
    fun `sticky restart stays parked instead of resuming spoofing`() {
        assertEquals(StickyNullIntentAction.KEEP_PARKED, computeStickyNullIntentAction(keepWidgetOnIdle = true))
    }

    @Test
    fun `sticky restart resumes the session when not parked`() {
        assertEquals(StickyNullIntentAction.RESUME_SESSION, computeStickyNullIntentAction(keepWidgetOnIdle = false))
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

    @Test
    fun `updatePositionWithVector applies for joystick and walk-to`() {
        assertEquals(true, shouldApplyUpdatePositionWithVector(MockMode.JOYSTICK, MockLocationState.RUNNING))
        assertEquals(true, shouldApplyUpdatePositionWithVector(MockMode.WALK_TO, MockLocationState.RUNNING))
    }

    @Test
    fun `updatePositionWithVector is a no-op while route replay is running`() {
        assertEquals(false, shouldApplyUpdatePositionWithVector(MockMode.ROUTE_REPLAY, MockLocationState.RUNNING))
    }

    @Test
    fun `updatePositionWithVector applies while route replay is paused`() {
        assertEquals(true, shouldApplyUpdatePositionWithVector(MockMode.ROUTE_REPLAY, MockLocationState.PAUSED))
    }

    @Test
    fun `updatePositionWithVector applies while roaming is paused`() {
        assertEquals(
            true,
            shouldApplyUpdatePositionWithVector(
                MockMode.ROAMING,
                MockLocationState.RUNNING,
                isRoamingPaused = true,
            ),
        )
    }

    @Test
    fun `updatePositionWithVector is a no-op for running roam follower and teleport`() {
        assertEquals(false, shouldApplyUpdatePositionWithVector(MockMode.ROAMING, MockLocationState.RUNNING))
        assertEquals(false, shouldApplyUpdatePositionWithVector(MockMode.FOLLOWER, MockLocationState.RUNNING))
        assertEquals(false, shouldApplyUpdatePositionWithVector(MockMode.TELEPORT, MockLocationState.RUNNING))
    }

    @Test
    fun `teleport extras push a GPS fix immediately`() {
        assertEquals(true, shouldPushImmediateLocationUpdate(speedMs = 0f, MockMode.TELEPORT))
        assertEquals(true, shouldPushImmediateLocationUpdate(speedMs = 0f, MockMode.WALK_TO))
        assertEquals(true, shouldPushImmediateLocationUpdate(speedMs = 0f, MockMode.ROUTE_REPLAY))
    }

    @Test
    fun `walk and joystick extras do not push immediately`() {
        assertEquals(false, shouldPushImmediateLocationUpdate(speedMs = 1.4f, MockMode.WALK_TO))
        assertEquals(false, shouldPushImmediateLocationUpdate(speedMs = 1.4f, MockMode.JOYSTICK))
    }

    @Test
    fun `follower never gets an immediate teleport push`() {
        assertEquals(false, shouldPushImmediateLocationUpdate(speedMs = 0f, MockMode.FOLLOWER))
    }
}
