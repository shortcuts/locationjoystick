package com.locationjoystick.feature.joystick.impl

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.compose.ui.graphics.toArgb
import com.locationjoystick.core.designsystem.LjAccent
import com.locationjoystick.core.overlay.OverlayView
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

private fun Double.toDegrees(): Double = Math.toDegrees(this)

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

            /** Fraction of view width/height used for the drag handle hit area (top-left corner). */
            private const val DRAG_HANDLE_FRACTION = 0.28f
        }

        var onInputChanged: ((JoystickInput) -> Unit)? = null
        var onReleased: (() -> Unit)? = null
        var shouldResetOnRelease: (() -> Boolean)? = null

        /** When true, outer circle turns accent colour and knob stays at last position. */
        var isLocked: Boolean = false
            set(value) {
                val wasLocked = field
                field = value
                // Transitioning locked → unlocked: snap knob back to centre
                if (wasLocked && !value) {
                    knobOffsetX = 0f
                    knobOffsetY = 0f
                    onInputChanged?.invoke(JoystickInput(angleDegrees = 0f, force = 0f))
                }
                updateLockedAppearance()
                invalidate()
            }

        /**
         * Called with raw screen deltas (dx, dy) while the user drags the handle.
         * The service should call [updateOverlayPosition] accordingly.
         */
        var onDragHandleMoved: ((rawX: Float, rawY: Float) -> Unit)? = null
        var onDragHandleDown: ((rawX: Float, rawY: Float) -> Unit)? = null

        private val accentArgb = LjAccent.toArgb()

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

        private fun updateLockedAppearance() {
            if (isLocked) {
                outerPaint.color = accentArgb
                outerPaint.alpha = 180
                outerBorderPaint.color = accentArgb
                outerBorderPaint.alpha = 220
            } else {
                outerPaint.color = Color.WHITE
                outerPaint.alpha = OUTER_ALPHA
                outerBorderPaint.color = Color.WHITE
                outerBorderPaint.alpha = 160
            }
        }

        private val knobPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                alpha = 220
                style = Paint.Style.FILL
            }

        /** Background circle for the drag handle. */
        private val handleBgPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                alpha = 180
                style = Paint.Style.FILL
            }

        /** Grip lines drawn inside the drag handle. */
        private val handleLinePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.DKGRAY
                alpha = 200
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
                strokeCap = Paint.Cap.ROUND
            }

        private var centerX = 0f
        private var centerY = 0f
        private var outerRadius = 0f
        private var knobRadius = 0f
        private var deadzoneRadius = 0f

        /** Centre of the drag handle icon. */
        private var handleCx = 0f
        private var handleCy = 0f
        private var handleRadius = 0f
        private var dragHandleHitRect = RectF()

        private var knobOffsetX = 0f
        private var knobOffsetY = 0f

        /** Which pointer is controlling the drag handle (-1 = none). */
        private var dragPointerId = -1

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

            // Drag handle sits at top-left; radius is ~13% of the view width
            handleRadius = w * 0.13f
            handleCx = handleRadius * 1.1f
            handleCy = handleRadius * 1.1f
            dragHandleHitRect = RectF(0f, 0f, w * DRAG_HANDLE_FRACTION, h * DRAG_HANDLE_FRACTION)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            // Joystick
            canvas.drawCircle(centerX, centerY, outerRadius, outerPaint)
            canvas.drawCircle(centerX, centerY, outerRadius, outerBorderPaint)
            canvas.drawCircle(centerX + knobOffsetX, centerY + knobOffsetY, knobRadius, knobPaint)

            // Drag handle background
            canvas.drawCircle(handleCx, handleCy, handleRadius, handleBgPaint)

            // Three grip lines centred in the handle
            val lineHalfLen = handleRadius * 0.55f
            val spacing = handleRadius * 0.28f
            for (i in -1..1) {
                val ly = handleCy + i * spacing
                canvas.drawLine(handleCx - lineHalfLen, ly, handleCx + lineHalfLen, ly, handleLinePaint)
            }
        }

        private fun isDragTouch(x: Float, y: Float): Boolean =
            hypot(x - centerX, y - centerY) > outerRadius

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val pointerIndex = event.actionIndex
            val pointerId = event.getPointerId(pointerIndex)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val x = event.x
                    val y = event.y
                    if (isDragTouch(x, y)) {
                        dragPointerId = pointerId
                        onDragHandleDown?.invoke(event.rawX, event.rawY)
                        return true
                    }
                    return handleJoystickDown(event)
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    val x = event.getX(pointerIndex)
                    val y = event.getY(pointerIndex)
                    if (dragPointerId == -1 && isDragTouch(x, y)) {
                        dragPointerId = pointerId
                        onDragHandleDown?.invoke(event.getRawX(pointerIndex), event.getRawY(pointerIndex))
                        return true
                    }
                    return false
                }

                MotionEvent.ACTION_MOVE -> {
                    var consumed = false
                    // Handle drag pointer
                    if (dragPointerId != -1) {
                        val idx = event.findPointerIndex(dragPointerId)
                        if (idx != -1) {
                            onDragHandleMoved?.invoke(event.getRawX(idx), event.getRawY(idx))
                            consumed = true
                        }
                    }
                    // Handle joystick pointer (pointer 0 if not taken by drag)
                    consumed = handleJoystickMove(event) || consumed
                    return consumed
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    if (pointerId == dragPointerId) {
                        dragPointerId = -1
                        return true
                    }
                    return false
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragPointerId = -1
                    return handleJoystickUp()
                }
            }
            return false
        }

        private fun handleJoystickDown(event: MotionEvent): Boolean = handleJoystickXY(event.x, event.y)

        private fun handleJoystickMove(event: MotionEvent): Boolean {
            // Use pointer 0 for joystick (if that pointer isn't the drag handle)
            val joystickPointerIdx =
                (0 until event.pointerCount).firstOrNull {
                    event.getPointerId(it) != dragPointerId
                } ?: return false
            return handleJoystickXY(event.getX(joystickPointerIdx), event.getY(joystickPointerIdx))
        }

        private fun handleJoystickXY(
            x: Float,
            y: Float,
        ): Boolean {
            val dx = x - centerX
            val dy = y - centerY
            val distance = hypot(dx, dy)

            val clampedDistance = min(distance, outerRadius)
            // Clamp visual position so knob stays inside outer circle boundary.
            val visualDistance = min(distance, outerRadius - knobRadius)
            val angle = atan2(dy, dx)
            knobOffsetX = visualDistance * kotlin.math.cos(angle)
            knobOffsetY = visualDistance * kotlin.math.sin(angle)

            val force =
                if (clampedDistance <= deadzoneRadius) {
                    0f
                } else {
                    ((clampedDistance - deadzoneRadius) / (outerRadius - deadzoneRadius)).coerceIn(0f, 1f)
                }

            val angleDegrees = ((angle.toDouble().toDegrees() + 360.0) % 360.0).toFloat()
            onInputChanged?.invoke(JoystickInput(angleDegrees = angleDegrees, force = force))
            invalidate()
            return true
        }

        private fun handleJoystickUp(): Boolean {
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
