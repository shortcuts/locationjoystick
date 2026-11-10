package com.locationjoystick.feature.widget.impl

import android.view.WindowManager
import com.locationjoystick.core.model.FloatingMapRect
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

private const val MIN_FLOATING_MAP_DP = 160f

internal fun defaultFloatingMapRect(
    screenWidthPx: Int,
    screenHeightPx: Int,
    density: Float,
): FloatingMapRect =
    clampFloatingMapRect(
        FloatingMapRect(
            x = (12 * density).toInt(),
            y = (48 * density).toInt(),
            width = compactFloatingMapWidth(screenWidthPx, density),
            height = compactFloatingMapHeight(screenHeightPx, density),
        ),
        screenWidthPx,
        screenHeightPx,
        density,
    )

internal fun clampFloatingMapRect(
    rect: FloatingMapRect,
    screenWidthPx: Int,
    screenHeightPx: Int,
    density: Float,
): FloatingMapRect {
    val min = (MIN_FLOATING_MAP_DP * density).roundToInt()
    val width = rect.width.coerceIn(minOf(min, screenWidthPx), screenWidthPx)
    val height = rect.height.coerceIn(minOf(min, screenHeightPx), screenHeightPx)
    return FloatingMapRect(
        x = rect.x.coerceIn(0, screenWidthPx - width),
        y = rect.y.coerceIn(0, screenHeightPx - height),
        width = width,
        height = height,
    )
}

/** Left and bottom stay fixed; the top-right corner follows the finger, offset by where it grabbed the button. */
internal fun resizeFloatingMapRect(
    start: FloatingMapRect,
    fingerRawX: Float,
    fingerRawY: Float,
    grabDx: Float,
    grabDy: Float,
    screenWidthPx: Int,
    screenHeightPx: Int,
    density: Float,
): FloatingMapRect {
    val min = (MIN_FLOATING_MAP_DP * density).roundToInt()
    val bottom = start.y + start.height
    val height = (bottom - (fingerRawY + grabDy).roundToInt()).coerceIn(minOf(min, bottom), bottom)
    val width = ((fingerRawX + grabDx).roundToInt() - start.x).coerceIn(minOf(min, screenWidthPx - start.x), screenWidthPx - start.x)
    return clampFloatingMapRect(FloatingMapRect(start.x, bottom - height, width, height), screenWidthPx, screenHeightPx, density)
}
