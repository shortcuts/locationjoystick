package com.locationjoystick.feature.joystick.impl

import kotlinx.coroutines.Job

/** Lifecycle of stick input. A job exists only while a drag or a locked movement runs. */
internal sealed interface JoystickInputState {
    data object Idle : JoystickInputState

    data class Dragging(
        val input: JoystickInput,
        val job: Job,
    ) : JoystickInputState

    data object LockedIdle : JoystickInputState

    data class Locked(
        val input: JoystickInput,
        val job: Job,
    ) : JoystickInputState
}

internal val JoystickInputState.isLocked: Boolean
    get() = this is JoystickInputState.LockedIdle || this is JoystickInputState.Locked

internal val JoystickInputState.input: JoystickInput?
    get() =
        when (this) {
            is JoystickInputState.Dragging -> input
            is JoystickInputState.Locked -> input
            else -> null
        }

internal val JoystickInputState.job: Job?
    get() =
        when (this) {
            is JoystickInputState.Dragging -> job
            is JoystickInputState.Locked -> job
            else -> null
        }

/** Touch input. A centered stick (force 0) never starts a job. */
internal fun JoystickInputState.withInput(
    input: JoystickInput,
    startJob: () -> Job,
): JoystickInputState {
    val live = job?.takeIf { it.isActive }
    val nextJob = live ?: if (input.force > 0f) startJob() else null
    return when {
        nextJob == null -> this
        isLocked -> JoystickInputState.Locked(input, nextJob)
        else -> JoystickInputState.Dragging(input, nextJob)
    }
}

/** Null when already locked. */
internal fun JoystickInputState.locked(): JoystickInputState? =
    when (this) {
        JoystickInputState.Idle -> JoystickInputState.LockedIdle
        is JoystickInputState.Dragging -> JoystickInputState.Locked(input, job)
        JoystickInputState.LockedIdle, is JoystickInputState.Locked -> null
    }

/** Null when locked: a locked stick keeps moving after release. */
internal fun JoystickInputState.released(): JoystickInputState? = if (isLocked) null else JoystickInputState.Idle
