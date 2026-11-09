package com.locationjoystick.feature.joystick.impl

import android.app.Activity
import android.view.WindowManager
import android.widget.FrameLayout
import com.locationjoystick.core.data.LocationRepository
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class JoystickVisibilityTest {
    private lateinit var activity: ActivityController<Activity>
    private lateinit var container: FrameLayout
    private lateinit var service: JoystickOverlayService
    private lateinit var view: JoystickView

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup()
        container = FrameLayout(activity.get())
        activity.get().setContentView(container)
        // Attach a context without running Hilt or starting the GPS service.
        service = Robolectric.buildService(JoystickOverlayService::class.java).get()
        service.locationRepository = LocationRepository()
        view = service.createOverlayView() as JoystickView
        val windowManager =
            mockk<WindowManager> {
                // Android can return from addView before the view is attached. Each test
                // completes that step later through a real Activity window.
                every { addView(any(), any()) } just Runs
                every { removeViewImmediate(any()) } answers { container.removeView(firstArg()) }
            }
        ReflectionHelpers.setField(service, "windowManager", windowManager)
        ReflectionHelpers.setField(service, "overlayView", view)
        ReflectionHelpers.setField(service, "currentParams", WindowManager.LayoutParams())
    }

    @After
    fun tearDown() {
        service.onDestroy()
        activity.pause().stop().destroy()
    }

    @Test
    fun `visibility updates after delayed attachment and resets when hidden`() {
        repeat(2) {
            service.showJoystick()
            assertFalse(service.isVisible.value)

            container.addView(view)

            assertTrue(view.isAttachedToWindow)
            assertTrue(service.isVisible.value)
            assertFalse(service.isLocked.value)

            service.hideJoystick()

            assertFalse(view.isAttachedToWindow)
            assertFalse(service.isVisible.value)
        }
    }

    @Test
    fun `base overlay show and hide commands also publish actual visibility`() {
        service.showOverlay()
        container.addView(view)
        assertTrue(service.isVisible.value)

        service.hideOverlay()

        assertFalse(view.isAttachedToWindow)
        assertFalse(service.isVisible.value)
    }
}
