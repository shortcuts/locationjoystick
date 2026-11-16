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

    /** A pause requested while the road fetch for [pending] still runs, so its mode is not set yet. [mode] stays [manual]. */
    data class PausePending(
        val manual: MockMode,
        val pending: ActiveMode,
    ) : MovementState {
        override val mode: MockMode get() = manual
    }
}

val MovementState.isActive: Boolean get() = this is MovementState.Running || this is MovementState.Paused

val MovementState.isPaused: Boolean get() = this is MovementState.Paused || this is MovementState.PausePending

fun MovementState.isActive(mode: MockMode): Boolean = isActive && this.mode == mode

fun MovementState.isPaused(mode: MockMode): Boolean =
    when (this) {
        is MovementState.Paused -> this.mode == mode
        is MovementState.PausePending -> pending.mockMode == mode
        else -> false
    }

/** True while a replay is paused, or paused before its mode is set. */
val MovementState.pausesReplay: Boolean
    get() = isPaused(MockMode.ROUTE_REPLAY)

/** The state after entering [mode]. Legal from every state; pause resets. */
fun enterMode(mode: MockMode): MovementState =
    ActiveMode.entries.firstOrNull { it.mockMode == mode }?.let(MovementState::Running) ?: MovementState.Manual(mode)

/** Like [enterMode], but a pause pending for [mode] carries over as [MovementState.Paused]. */
fun MovementState.afterEntering(mode: MockMode): MovementState =
    if (this is MovementState.PausePending && pending.mockMode == mode) MovementState.Paused(pending) else enterMode(mode)

/** Null when the state is not running an active mode. */
fun MovementState.pausedOrNull(): MovementState? = (this as? MovementState.Running)?.let { MovementState.Paused(it.active) }

/** Null when the state is not paused. A pending pause drops back to its manual mode. */
fun MovementState.resumedOrNull(): MovementState? =
    when (this) {
        is MovementState.Paused -> MovementState.Running(active)
        is MovementState.PausePending -> MovementState.Manual(manual)
        else -> null
    }
