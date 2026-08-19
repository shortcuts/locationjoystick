package com.locationjoystick.core.location

import com.locationjoystick.core.model.MockLocationState

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
