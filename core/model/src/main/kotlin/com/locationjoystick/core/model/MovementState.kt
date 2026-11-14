package com.locationjoystick.core.model

/** The modes an engine drives. The enum keeps TELEPORT, JOYSTICK and FOLLOWER out of [MovementState.Running] and [MovementState.Paused]. */
enum class ActiveMode(
    val mockMode: MockMode,
) {
    WALK_TO(MockMode.WALK_TO),
    ROAMING(MockMode.ROAMING),
    ROUTE_REPLAY(MockMode.ROUTE_REPLAY),
}

/** Movement mode plus pause state, so "paused with no active mode" cannot be built. */
sealed interface MovementState {
    val mode: MockMode

    /** TELEPORT, JOYSTICK and FOLLOWER: nothing to pause. */
    data class Manual(
        override val mode: MockMode,
    ) : MovementState

    data class Running(
        val active: ActiveMode,
    ) : MovementState {
        override val mode: MockMode get() = active.mockMode
    }

    data class Paused(
        val active: ActiveMode,
    ) : MovementState {
        override val mode: MockMode get() = active.mockMode
    }
}

val MovementState.isActive: Boolean get() = this !is MovementState.Manual

val MovementState.isPaused: Boolean get() = this is MovementState.Paused

fun MovementState.isActive(mode: MockMode): Boolean = isActive && this.mode == mode

fun MovementState.isPaused(mode: MockMode): Boolean = this is MovementState.Paused && this.mode == mode

/** The state after entering [mode]. Legal from every state; pause resets. */
fun enterMode(mode: MockMode): MovementState =
    ActiveMode.entries.firstOrNull { it.mockMode == mode }?.let(MovementState::Running) ?: MovementState.Manual(mode)

/** Null when the state cannot pause. Replay pauses through MockLocationState.PAUSED, never here. */
fun MovementState.pausedOrNull(): MovementState? =
    if (this is MovementState.Running) {
        when (active) {
            ActiveMode.WALK_TO, ActiveMode.ROAMING -> MovementState.Paused(active)
            ActiveMode.ROUTE_REPLAY -> null
        }
    } else {
        null
    }

/** Null when the state is not paused. */
fun MovementState.resumedOrNull(): MovementState? =
    if (this is MovementState.Paused) {
        when (active) {
            ActiveMode.WALK_TO, ActiveMode.ROAMING -> MovementState.Running(active)
            ActiveMode.ROUTE_REPLAY -> null
        }
    } else {
        null
    }
