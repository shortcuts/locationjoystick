package com.locationjoystick.core.model

/** Movement mode plus pause state, so "paused with no active mode" cannot be built. */
sealed interface MovementState {
    val mode: MockMode

    /** TELEPORT, JOYSTICK and FOLLOWER: nothing to pause. */
    data class Manual(
        override val mode: MockMode,
    ) : MovementState

    /** WALK_TO, ROAMING and ROUTE_REPLAY. */
    data class Active(
        override val mode: MockMode,
        val paused: Boolean,
    ) : MovementState
}

val MovementState.isActive: Boolean get() = this is MovementState.Active

val MovementState.isPaused: Boolean get() = this is MovementState.Active && paused

fun MovementState.isActive(mode: MockMode): Boolean = this is MovementState.Active && this.mode == mode

fun MovementState.isPaused(mode: MockMode): Boolean = this is MovementState.Active && this.mode == mode && paused

private val ACTIVE_MODES = setOf(MockMode.WALK_TO, MockMode.ROAMING, MockMode.ROUTE_REPLAY)

// Replay pauses through MockLocationState.PAUSED, so only these modes pause here.
private val PAUSABLE_MODES = setOf(MockMode.WALK_TO, MockMode.ROAMING)

/** The state after entering [mode]. Legal from every state; pause resets. */
fun enterMode(mode: MockMode): MovementState =
    if (mode in ACTIVE_MODES) MovementState.Active(mode, paused = false) else MovementState.Manual(mode)

/** Null when the state cannot pause. */
fun MovementState.pausedOrNull(): MovementState? =
    if (this is MovementState.Active && !paused && mode in PAUSABLE_MODES) copy(paused = true) else null

/** Null when the state is not paused. */
fun MovementState.resumedOrNull(): MovementState? =
    if (this is MovementState.Active && paused && mode in PAUSABLE_MODES) copy(paused = false) else null
