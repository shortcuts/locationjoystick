package com.locationjoystick.feature.widget.impl

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import com.locationjoystick.core.overlay.OverlayServiceHelper
import com.locationjoystick.feature.joystick.impl.JoystickOverlayService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WidgetServiceBinderTest {
    @Test
    fun `old joystick service cannot write widget flows after unbind`() =
        runTest {
            val scope = TestScope(StandardTestDispatcher(testScheduler))
            val connection = slot<ServiceConnection>()
            val context = mockk<Context>(relaxed = true)
            every { context.bindService(any<Intent>(), capture(connection), any<Int>()) } returns true
            val visible = MutableStateFlow(false)
            val locked = MutableStateFlow(false)
            val binder = WidgetServiceBinder(context, scope, mockk<OverlayServiceHelper>(relaxed = true), visible, locked)
            val oldVisible = MutableStateFlow(false)
            val oldLocked = MutableStateFlow(false)
            val service = mockk<JoystickOverlayService>()
            every { service.isVisible } returns oldVisible
            every { service.isLocked } returns oldLocked
            val localBinder = mockk<JoystickOverlayService.LocalBinder>()
            every { localBinder.getService() } returns service

            binder.bindJoystick()
            connection.captured.onServiceConnected(mockk(relaxed = true), localBinder)
            scope.testScheduler.advanceUntilIdle()
            binder.unbindJoystick()
            oldVisible.value = true
            oldLocked.value = true
            scope.testScheduler.advanceUntilIdle()

            assertFalse(visible.value)
            assertFalse(locked.value)
            verify { context.unbindService(connection.captured) }
        }
}
