package com.locationjoystick.core.location

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode

/** Decision for the IDLE/ERROR branch of [MockLocationService.observeLocationState]. */
internal enum class IdleOrErrorLoopAction {
    /** Leave the update loop (and test provider) running untouched. */
    KEEP_ALIVE,

    /** Cancel the update loop and remove the test provider. */
    TEAR_DOWN,

    /** Nothing to do (no active loop to cancel). */
    NO_OP,
}

/** Decision for the PAUSED branch of [MockLocationService.observeLocationState]. */
internal enum class PausedLoopAction {
    /** Start the update loop (it was not already running). */
    START_UP,

    /** Leave the update loop (and test provider) running untouched. */
    KEEP_ALIVE,
}

/** Decision for the RUNNING branch of [MockLocationService.observeLocationState]. */
internal enum class RunningLoopAction {
    /** Route replay drives its own ticks — stop the idle loop (e.g. left alive from a pause). */
    STOP_IDLE_LOOP_FOR_REPLAY,

    /** Idle loop isn't running yet — set up the test provider and start it. */
    START_IDLE_LOOP,

    /** Idle loop already running, or replay owns ticking — nothing to do. */
    NO_OP,
}

/**
 * Pure decision for the IDLE/ERROR branch of [MockLocationService.observeLocationState].
 *
 * A group-sync leader must never have its test provider torn down by an *incidental* IDLE
 * transition (e.g. a walk or route replay completing naturally) — only an explicit
 * `stopSpoofing()` call (which removes the provider itself before this collector observes the
 * state change) should end broadcasting to followers. ERROR is always torn down: it represents
 * a genuine unrecoverable failure, not a natural completion, so group sync gets no exception.
 */
internal fun computeIdleOrErrorLoopAction(
    state: MockLocationState,
    leaderSharingEnabled: Boolean,
    hasActiveUpdateJob: Boolean,
): IdleOrErrorLoopAction =
    when {
        state == MockLocationState.IDLE && leaderSharingEnabled -> IdleOrErrorLoopAction.KEEP_ALIVE
        hasActiveUpdateJob -> IdleOrErrorLoopAction.TEAR_DOWN
        else -> IdleOrErrorLoopAction.NO_OP
    }

/**
 * Pure decision for the PAUSED branch of [MockLocationService.observeLocationState].
 *
 * A paused route replay must keep pushing its frozen position to the test provider every tick —
 * otherwise the mock fix goes stale and some location consumers (e.g. fused location on certain
 * OEM/Android builds) fall back to a real location source until ticks resume. This mirrors what
 * was previously only done for a group-sync leader (see docs/features/group-sync.md) but applies
 * to every paused replay, group sync or not.
 */
internal fun computePausedLoopAction(hasActiveUpdateJob: Boolean): PausedLoopAction =
    if (hasActiveUpdateJob) PausedLoopAction.KEEP_ALIVE else PausedLoopAction.START_UP

/**
 * Pure decision for the RUNNING branch of [MockLocationService.observeLocationState].
 *
 * Route replay drives its own position ticks via [MockLocationService]'s `pushLocationUpdate`
 * callback wired through `ReplayOrchestrator` — it must never be raced by the generic idle
 * update loop. A RUNNING transition while already in `ROUTE_REPLAY` mode (e.g. a redundant
 * transition from something unrelated re-observing the same state) must only ever stop the idle
 * loop, never start it — regardless of [hasActiveUpdateJob].
 */
internal fun computeRunningLoopAction(
    mode: MockMode,
    hasActiveUpdateJob: Boolean,
): RunningLoopAction =
    when {
        mode == MockMode.ROUTE_REPLAY -> RunningLoopAction.STOP_IDLE_LOOP_FOR_REPLAY
        !hasActiveUpdateJob -> RunningLoopAction.START_IDLE_LOOP
        else -> RunningLoopAction.NO_OP
    }

/**
 * Whether [MockLocationService.updatePositionWithVector] should write this tick.
 *
 * Joystick and walk-to self-report their mode and own the tick. A playing route or
 * running roam owns the tick instead. A paused route or paused roam yields so the
 * joystick can steer without stealing [MockMode].
 */
internal fun shouldApplyUpdatePositionWithVector(
    mode: MockMode,
    mockLocationState: MockLocationState,
    isRoamingPaused: Boolean = false,
): Boolean =
    when (mode) {
        MockMode.JOYSTICK, MockMode.WALK_TO -> true
        MockMode.ROUTE_REPLAY -> mockLocationState == MockLocationState.PAUSED
        MockMode.ROAMING -> isRoamingPaused
        else -> false
    }

/**
 * Whether [MockLocationService] should push a test-provider fix immediately for this
 * [ACTION_UPDATE_POSITION][com.locationjoystick.core.common.constants.AppConstants.ServiceConstants.ACTION_UPDATE_POSITION].
 *
 * Walk/joystick ticks (`speedMs > 0`) ride the 1 Hz loop. A teleport (`speedMs == 0`) must
 * push now so Maps and other consumers do not wait up to a second. Follower owns GPS.
 */
internal fun shouldPushImmediateLocationUpdate(
    speedMs: Float,
    mode: MockMode,
): Boolean = speedMs <= 0f && mode != MockMode.FOLLOWER

/** Decision for a follower's reaction to a leader position update, in [MockLocationService.enterFollowerMode]. */
internal enum class FollowerActiveAction {
    /** Leader is active and the follower isn't spoofing yet — snap straight to its position. */
    BOOTSTRAP,

    /** Leader stopped spoofing and the follower was mirroring it — pause without tearing down the service. */
    PAUSE,

    /** Nothing to do. */
    NO_OP,
}

/**
 * Pure decision for whether a follower should start, pause, or ignore a leader position update.
 *
 * [spoofingStarted] doubles as "has the follower already bootstrapped for the current active
 * streak" — it's reset on [PAUSE] so the next [BOOTSTRAP] can fire again once the leader resumes.
 */
internal fun computeFollowerActiveAction(
    leaderActive: Boolean,
    spoofingStarted: Boolean,
    currentState: MockLocationState,
): FollowerActiveAction =
    when {
        leaderActive && !spoofingStarted && currentState != MockLocationState.RUNNING -> FollowerActiveAction.BOOTSTRAP
        !leaderActive && spoofingStarted && currentState == MockLocationState.RUNNING -> FollowerActiveAction.PAUSE
        else -> FollowerActiveAction.NO_OP
    }

/** Decision for the "Hide floating widget" live-toggle collector in [MockLocationService.observeLocationState]. */
internal enum class WidgetOverlayAction {
    /** Start (or leave running) the widget overlay service. */
    START,

    /** Stop the widget overlay service. */
    STOP,

    /** Not currently spoofing — leave the overlay service untouched either way. */
    NO_OP,
}

/** Which overlays to tear down when spoofing goes IDLE or ERROR. */
internal enum class IdleOverlayStopAction {
    /** Stop both the joystick overlay and the floating widget. */
    STOP_JOYSTICK_AND_WIDGET,

    /** Park: mock GPS is off, but the widget stays so the user can Start again. */
    STOP_JOYSTICK_ONLY,
}

/**
 * Pure decision for which overlays to tear down when spoofing goes IDLE/ERROR.
 *
 * [keepWidgetOverlay] is set by a widget long-press Pause: the mock test provider still stops,
 * the joystick overlay still stops, and the widget FAB stays on screen.
 */
internal fun computeIdleOverlayStopAction(keepWidgetOverlay: Boolean): IdleOverlayStopAction =
    if (keepWidgetOverlay) IdleOverlayStopAction.STOP_JOYSTICK_ONLY else IdleOverlayStopAction.STOP_JOYSTICK_AND_WIDGET

/** Why overlays are being torn down — the IDLE collector vs an explicit full Stop. */
internal enum class OverlayStopTrigger {
    /** [MockLocationService] observed IDLE/ERROR. StateFlow will not re-emit if already IDLE. */
    STATE_IDLE,

    /** User tapped Stop (widget, notification, or app). Always close the widget. */
    FULL_STOP,
}

/**
 * Full Stop always removes the widget, even right after Pause (state is already IDLE, so
 * [computeIdleOverlayStopAction] is not observed again).
 */
internal fun computeOverlayStopAction(
    trigger: OverlayStopTrigger,
    keepWidgetOverlay: Boolean,
): IdleOverlayStopAction =
    when (trigger) {
        OverlayStopTrigger.FULL_STOP -> IdleOverlayStopAction.STOP_JOYSTICK_AND_WIDGET
        OverlayStopTrigger.STATE_IDLE -> computeIdleOverlayStopAction(keepWidgetOverlay)
    }

/** Decision for [MockLocationService.onStartCommand] when the OS restarts the service with a null intent. */
internal enum class StickyNullIntentAction {
    /** Widget Pause: stay idle, keep (or restart) the widget, do not resume mock GPS. */
    KEEP_PARKED,

    /** Default START_STICKY path: restore group sync or the remembered location. */
    RESUME_SESSION,
}

/**
 * Pure decision for a START_STICKY null-intent restart.
 *
 * Pause persists [keepWidgetOnIdle] so a process kill while parked does not silently resume
 * spoofing (the default remembered-location path).
 */
internal fun computeStickyNullIntentAction(keepWidgetOnIdle: Boolean): StickyNullIntentAction =
    if (keepWidgetOnIdle) StickyNullIntentAction.KEEP_PARKED else StickyNullIntentAction.RESUME_SESSION

/**
 * Pure decision for whether the widget overlay service should be started or stopped when the
 * "Hide floating widget" setting changes mid-session (not just at the RUNNING transition).
 */
internal fun computeWidgetOverlayAction(
    state: MockLocationState,
    hideWidgetOverlay: Boolean,
): WidgetOverlayAction =
    when {
        state == MockLocationState.IDLE || state == MockLocationState.ERROR -> WidgetOverlayAction.NO_OP
        hideWidgetOverlay -> WidgetOverlayAction.STOP
        else -> WidgetOverlayAction.START
    }

/** Which radius/orientation [PositionJitterCoordinator.step] should use for one tick. */
internal data class JitterStepRequest(
    val radiusMeters: Double,
    val bearingDeg: Float,
    val longitudinalFraction: Double,
)

/**
 * Pure decision for which jitter radius/orientation applies this tick, used by
 * [MockLocationService.captureSnapshot]. TELEPORT and an idle FOLLOWER (mirroring the leader's
 * placed-target position) use [idleRadiusMeters], isotropically. Every other mode uses
 * [movingRadiusMeters] — isotropically while stationary, squished along [bearingDeg] while moving
 * (mirrors [gaussianLatLonOffsetLateral]'s existing anisotropy).
 */
internal fun resolveJitterStepRequest(
    mode: MockMode,
    speedMs: Float,
    bearingDeg: Float,
    idleRadiusMeters: Double,
    movingRadiusMeters: Double,
): JitterStepRequest =
    when {
        mode == MockMode.TELEPORT || (mode == MockMode.FOLLOWER && speedMs == 0f) ->
            JitterStepRequest(idleRadiusMeters, bearingDeg = 0f, longitudinalFraction = 1.0)

        speedMs > 0f ->
            JitterStepRequest(
                movingRadiusMeters,
                bearingDeg,
                AppConstants.JitterConstants.LONGITUDINAL_JITTER_FRACTION,
            )

        else -> JitterStepRequest(movingRadiusMeters, bearingDeg = 0f, longitudinalFraction = 1.0)
    }
