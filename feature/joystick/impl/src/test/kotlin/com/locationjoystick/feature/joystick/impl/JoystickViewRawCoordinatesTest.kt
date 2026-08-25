package com.locationjoystick.feature.joystick.impl

import android.os.SystemClock
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class JoystickViewRawCoordinatesTest {
    private fun newLaidOutView(): JoystickView {
        val view = JoystickView(RuntimeEnvironment.getApplication())
        view.layout(0, 0, 300, 300)
        return view
    }

    private fun drag(
        view: JoystickView,
        moveX: Float,
        moveY: Float,
    ): Pair<Float, Float> {
        var captured: Pair<Float, Float>? = null
        view.onDragHandleMoved = { x, y -> captured = Pair(x, y) }

        val downTime = SystemClock.uptimeMillis()
        // (10f, 10f) is outside the joystick circle (center (150,150), radius 135) —
        // isDragTouch() requires that to register as a drag rather than a joystick move.
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 10f, 10f, 0)
        view.dispatchTouchEvent(down)
        down.recycle()

        val move = MotionEvent.obtain(downTime, downTime + 16, MotionEvent.ACTION_MOVE, moveX, moveY, 0)
        view.dispatchTouchEvent(move)
        move.recycle()

        return requireNotNull(captured) { "onDragHandleMoved was not invoked" }
    }

    @Test
    @Config(sdk = [29])
    fun `rawX rawY on API 29+ use MotionEvent getRawX getRawY`() {
        val view = newLaidOutView()
        val (x, y) = drag(view, moveX = 50f, moveY = 60f)
        // Unattached view: getLocationOnScreen() is (0,0), so both the native getRawX/getRawY
        // path (API 29+) and the manual offset-based fallback below API 29 must agree with the
        // event's own local coordinates here. This test's ceiling: it cannot exercise a real
        // non-zero screen offset (that needs an attached window) — it pins that both code paths
        // choose the correct pointer index and don't crash across the SDK boundary, not the
        // offset arithmetic itself.
        assertEquals(50f, x, 0f)
        assertEquals(60f, y, 0f)
    }

    @Test
    @Config(sdk = [28])
    fun `rawX rawY below API 29 derive from getLocationOnScreen plus local coordinates`() {
        val view = newLaidOutView()
        val (x, y) = drag(view, moveX = 50f, moveY = 60f)
        assertEquals(50f, x, 0f)
        assertEquals(60f, y, 0f)
    }
}
