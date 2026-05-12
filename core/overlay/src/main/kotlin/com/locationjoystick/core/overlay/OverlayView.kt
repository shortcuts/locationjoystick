package com.locationjoystick.core.overlay

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

abstract class OverlayView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : View(context, attrs, defStyleAttr) {
        abstract override fun onTouchEvent(event: MotionEvent): Boolean

        fun clampToScreen(
            x: Int,
            y: Int,
        ): Pair<Int, Int> {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = wm.currentWindowMetrics
            val screenWidth = metrics.bounds.width()
            val screenHeight = metrics.bounds.height()
            val clampedX = x.coerceIn(0, (screenWidth - width).coerceAtLeast(0))
            val clampedY = y.coerceIn(0, (screenHeight - height).coerceAtLeast(0))
            return Pair(clampedX, clampedY)
        }

        fun isWithinBounds(
            x: Int,
            y: Int,
        ): Boolean = x >= 0 && y >= 0 && x <= width && y <= height
    }
