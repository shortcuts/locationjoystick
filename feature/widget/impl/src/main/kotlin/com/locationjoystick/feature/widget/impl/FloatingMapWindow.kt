package com.locationjoystick.feature.widget.impl

import android.view.WindowManager
import kotlin.math.roundToInt

internal fun compactFloatingMapWidth(
    screenWidthPx: Int,
    density: Float,
): Int =
    minOf((screenWidthPx * 0.82f).roundToInt(), (420f * density).roundToInt())
        .coerceAtLeast(minOf(screenWidthPx, (280f * density).roundToInt()))

internal fun compactFloatingMapHeight(
    screenHeightPx: Int,
    density: Float,
): Int =
    minOf((screenHeightPx * 0.58f).roundToInt(), (560f * density).roundToInt())
        .coerceAtLeast(minOf(screenHeightPx, (360f * density).roundToInt()))

internal fun floatingMapWindowFlags(): Int = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
