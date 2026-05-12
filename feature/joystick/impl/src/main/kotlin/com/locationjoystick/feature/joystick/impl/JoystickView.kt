package com.locationjoystick.feature.joystick.impl

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import com.locationjoystick.core.overlay.OverlayView
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

data class JoystickInput(
    val angleDegrees: Float,
    val force: Float,
)

@SuppressLint("ClickableViewAccessibility")
class JoystickView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : OverlayView(context, attrs, defStyleAttr) {
        companion object {
            private const val KNOB_RADIUS_FRACTION = 0.25f
            private const val DEADZONE_FRACTION = 0.15f
            private const val OUTER_ALPHA = 80
        }

        var onInputChanged: ((JoystickInput) -> Unit)? = null
        var onReleased: (() -> Unit)? = null
        var shouldResetOnRelease: (() -> Boolean)? = null

        private val outerPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                alpha = OUTER_ALPHA
                style = Paint.Style.FILL
            }

        private val outerBorderPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                alpha = 160
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }

        private val knobPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                alpha = 220
                style = Paint.Style.FILL
            }

        private var centerX = 0f
        private var centerY = 0f
        private var outerRadius = 0f
        private var knobRadius = 0f
        private var deadzoneRadius = 0f

        private var knobOffsetX = 0f
        private var knobOffsetY = 0f

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int,
        ) {
            super.onSizeChanged(w, h, oldw, oldh)
            centerX = w / 2f
            centerY = h / 2f
            outerRadius = min(w, h) / 2f * 0.9f
            knobRadius = outerRadius * KNOB_RADIUS_FRACTION
            deadzoneRadius = outerRadius * DEADZONE_FRACTION
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawCircle(centerX, centerY, outerRadius, outerPaint)
            canvas.drawCircle(centerX, centerY, outerRadius, outerBorderPaint)
            canvas.drawCircle(centerX + knobOffsetX, centerY + knobOffsetY, knobRadius, knobPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - centerX
                    val dy = event.y - centerY
                    val distance = hypot(dx, dy)

                    val clampedDistance = min(distance, outerRadius)
                    val angle = atan2(dy, dx)
                    knobOffsetX = clampedDistance * kotlin.math.cos(angle)
                    knobOffsetY = clampedDistance * kotlin.math.sin(angle)

                    val force =
                        if (clampedDistance <= deadzoneRadius) {
                            0f
                        } else {
                            ((clampedDistance - deadzoneRadius) / (outerRadius - deadzoneRadius)).coerceIn(0f, 1f)
                        }

                    val angleDegrees = ((Math.toDegrees(angle.toDouble()) + 360.0) % 360.0).toFloat()

                    onInputChanged?.invoke(JoystickInput(angleDegrees = angleDegrees, force = force))
                    invalidate()
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (shouldResetOnRelease?.invoke() != false) {
                        knobOffsetX = 0f
                        knobOffsetY = 0f
                        onInputChanged?.invoke(JoystickInput(angleDegrees = 0f, force = 0f))
                    }
                    onReleased?.invoke()
                    invalidate()
                    return true
                }
            }
            return false
        }
    }
