package com.locationjoystick.feature.widget.impl

import android.view.Gravity
import android.view.WindowManager

internal fun pasteOverlayWidth(): Int = WindowManager.LayoutParams.MATCH_PARENT

internal fun pasteOverlayHeight(): Int = WindowManager.LayoutParams.WRAP_CONTENT

internal fun pasteOverlayGravity(): Int = Gravity.BOTTOM or Gravity.START

/**
 * Expanded: focusable so the coordinates field can take IME / clipboard paste.
 * Minimized: not focusable so the app underneath (Maps, browser) can be copied from.
 * Touches outside the sheet always pass through to the window below.
 */
internal fun pasteOverlayWindowFlags(minimized: Boolean): Int {
    val outsideTouchesPassThrough = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
    return if (minimized) {
        outsideTouchesPassThrough or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    } else {
        outsideTouchesPassThrough
    }
}
