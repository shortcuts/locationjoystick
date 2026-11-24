package com.locationjoystick.feature.widget.impl

import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

/**
 * Drag from the first touch calls [onResizeStart], [onResize] and [onResizeEnd] with raw screen coordinates.
 * Raw coordinates stay valid while the window resizes under the finger; local deltas would not.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun Modifier.floatingMapResizeGesture(
    onResizeStart: (rawX: Float, rawY: Float) -> Unit,
    onResize: (rawX: Float, rawY: Float) -> Unit,
    onResizeEnd: () -> Unit,
): Modifier {
    val currentOnResizeStart by rememberUpdatedState(onResizeStart)
    val currentOnResize by rememberUpdatedState(onResize)
    val currentOnResizeEnd by rememberUpdatedState(onResizeEnd)
    return semantics(mergeDescendants = true) { role = Role.Button }.pointerInteropFilter { event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> currentOnResizeStart(event.rawX, event.rawY)
            MotionEvent.ACTION_MOVE -> currentOnResize(event.rawX, event.rawY)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> currentOnResizeEnd()
        }
        true
    }
}
