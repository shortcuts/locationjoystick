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

    /** Cancel the update loop and remove the test provider. */
    TEAR_DOWN,

    /** Nothing to do (no active loop to cancel). */
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
 * Route replay/roaming/walk-to stop driving ticks while paused, but a group-sync leader must
 * keep broadcasting its (frozen) position so followers don't see a stale/dead feed — the loop
 * is started (if not already running) rather than torn down, mirroring the IDLE/ERROR policy
 * in [computeIdleOrErrorLoopAction].
 */
internal fun computePausedLoopAction(
    leaderSharingEnabled: Boolean,
    hasActiveUpdateJob: Boolean,
): PausedLoopAction =
    when {
        leaderSharingEnabled && !hasActiveUpdateJob -> PausedLoopAction.START_UP
        leaderSharingEnabled -> PausedLoopAction.KEEP_ALIVE
        hasActiveUpdateJob -> PausedLoopAction.TEAR_DOWN
        else -> PausedLoopAction.NO_OP
    }

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
