package com.locationjoystick.core.location

import android.app.NotificationManager
import android.content.Context
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OverlayNotificationReactorTest {
    private val context = mockk<Context>(relaxed = true)
    private val mode = MutableStateFlow(MockMode.JOYSTICK)
    private val state = MutableStateFlow(MockLocationState.RUNNING)
    private val learning = MutableStateFlow<LearningState>(LearningState.Idle)

    @Before
    fun setUp() {
        mockkStatic("com.locationjoystick.core.location.MockLocationNotificationKt")
        every { buildMockLocationNotification(any(), any(), any(), any(), any()) } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() = unmockkStatic("com.locationjoystick.core.location.MockLocationNotificationKt")

    private fun TestScope.reactor(hide: Boolean): OverlayNotificationReactor {
        val locationRepository =
            mockk<LocationRepository> {
                every { currentMode } returns mode
                every { mockLocationState } returns state
            }
        val settings =
            mockk<SettingsRepository> {
                every { getHideForegroundNotification() } returns flowOf(hide)
                every { getHideWidgetOverlay() } returns flowOf(false)
            }
        return OverlayNotificationReactor(context, mockk<NotificationManager>(relaxed = true), locationRepository, settings, learning)
            .also { it.observe(backgroundScope, MutableStateFlow(MockLocationState.RUNNING)) }
    }

    @Test
    fun `buildCurrent keeps learning progress`() =
        runTest(UnconfinedTestDispatcher()) {
            learning.value = LearningState.Learning(1, 2)
            reactor(hide = true).buildCurrent()
            verify { buildMockLocationNotification(any(), false, false, true, LearningState.Learning(1, 2)) }
        }

    @Test
    fun `buildCurrent keeps replay paused`() =
        runTest(UnconfinedTestDispatcher()) {
            mode.value = MockMode.ROUTE_REPLAY
            state.value = MockLocationState.PAUSED
            reactor(hide = false).buildCurrent()
            verify { buildMockLocationNotification(any(), true, true, false, LearningState.Idle) }
        }
}
