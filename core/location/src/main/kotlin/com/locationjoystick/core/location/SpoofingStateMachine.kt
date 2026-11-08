package com.locationjoystick.core.location

import android.util.Log
import com.locationjoystick.core.model.MockLocationState
import kotlinx.coroutines.flow.MutableStateFlow

private const val TAG = "SpoofingStateMachine"

internal enum class SpoofingEvent { START, PAUSE, STOP, FAIL, CLEAR_ERROR }

/** Returns the next state, or null when [event] is rejected in [current]. */
internal fun nextSpoofingState(
    current: MockLocationState,
    event: SpoofingEvent,
): MockLocationState? =
    when (current) {
        MockLocationState.IDLE, MockLocationState.RUNNING, MockLocationState.PAUSED ->
            when (event) {
                SpoofingEvent.START -> MockLocationState.RUNNING
                SpoofingEvent.PAUSE -> MockLocationState.PAUSED
                SpoofingEvent.STOP -> MockLocationState.IDLE
                SpoofingEvent.FAIL -> MockLocationState.ERROR
                SpoofingEvent.CLEAR_ERROR -> null
            }
        // A late START or PAUSE must not hide a failed provider; only Stop and retry leave ERROR.
        MockLocationState.ERROR ->
            when (event) {
                SpoofingEvent.START -> null
                SpoofingEvent.PAUSE -> null
                SpoofingEvent.STOP -> MockLocationState.IDLE
                SpoofingEvent.FAIL -> MockLocationState.ERROR
                SpoofingEvent.CLEAR_ERROR -> MockLocationState.IDLE
            }
    }

internal class SpoofingStateMachine(
    private val flow: MutableStateFlow<MockLocationState>,
) {
    fun send(event: SpoofingEvent): Boolean {
        while (true) {
            val current = flow.value
            val next = nextSpoofingState(current, event)
            if (next == null) {
                Log.w(TAG, "ignored $event in $current")
                return false
            }
            if (flow.compareAndSet(current, next)) return true
        }
    }
}
