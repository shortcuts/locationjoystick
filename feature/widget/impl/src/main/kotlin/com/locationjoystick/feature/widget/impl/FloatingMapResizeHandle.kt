package com.locationjoystick.feature.widget.impl

import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalView
import kotlin.math.hypot

private class ResizeGestureState {
    var downX = 0f
    var downY = 0f
    var lastX = 0f
    var lastY = 0f
    var movedPastSlop = false
    var resizing = false
}

/**
 * Tap calls [onTap]; long press then drag calls [onResizeStart], [onResize] and [onResizeEnd] with raw screen
 * coordinates. Raw coordinates stay valid while the window resizes under the finger; local deltas would not.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun Modifier.floatingMapResizeGesture(
    resizable: Boolean,
    onTap: () -> Unit,
    onResizeStart: (rawX: Float, rawY: Float) -> Unit,
    onResize: (rawX: Float, rawY: Float) -> Unit,
    onResizeEnd: () -> Unit,
): Modifier {
    val view = LocalView.current
    val slop = remember(view) { ViewConfiguration.get(view.context).scaledTouchSlop }
    val state = remember { ResizeGestureState() }
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnResizeStart by rememberUpdatedState(onResizeStart)
    val currentOnResize by rememberUpdatedState(onResize)
    val currentOnResizeEnd by rememberUpdatedState(onResizeEnd)
    val longPress =
        remember(view, state) {
            Runnable {
                state.resizing = true
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                currentOnResizeStart(state.lastX, state.lastY)
            }
        }
    return pointerInteropFilter { event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                state.downX = event.rawX
                state.downY = event.rawY
                state.lastX = event.rawX
                state.lastY = event.rawY
                state.movedPastSlop = false
                state.resizing = false
                if (resizable) view.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                state.lastX = event.rawX
                state.lastY = event.rawY
                if (state.resizing) {
                    currentOnResize(event.rawX, event.rawY)
                } else if (hypot(event.rawX - state.downX, event.rawY - state.downY) > slop) {
                    state.movedPastSlop = true
                    view.removeCallbacks(longPress)
                }
            }
            MotionEvent.ACTION_UP -> {
                view.removeCallbacks(longPress)
                if (state.resizing) {
                    currentOnResizeEnd()
                } else if (!state.movedPastSlop) {
                    currentOnTap()
                }
                state.resizing = false
            }
            MotionEvent.ACTION_CANCEL -> {
                view.removeCallbacks(longPress)
                if (state.resizing) currentOnResizeEnd()
                state.resizing = false
            }
        }
        true
    }
}
