package com.locationjoystick.core.location

import android.content.Context
import android.content.Intent
import android.util.Log
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.MockLocationServiceStarter
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.data.TeleportUseCase
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class ExternalTeleportHandlerTest {
    private val context = mockk<Context>(relaxed = true)
    private val teleport = mockk<TeleportUseCase>(relaxed = true)
    private val location = mockk<LocationRepository>()
    private val settings = mockk<SettingsRepository>(relaxed = true)
    private val starter = mockk<MockLocationServiceStarter>(relaxed = true)
    private val intent = mockk<Intent>()
    private val state = MutableStateFlow(MockLocationState.RUNNING)
    private val point = LatLng(48.8584, 2.2945)
    private val handler = ExternalTeleportHandler(context, teleport, location, settings, starter)

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
        mockkObject(MockLocationIntentBuilder)
        every { MockLocationIntentBuilder.startSpoofing(context, point.latitude, point.longitude) } returns intent
        every { location.mockLocationState } returns state
        every { settings.getHideTeleportFeatures() } returns flowOf(false)
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `running teleports and does not start the service`() =
        runTest {
            handler.handle(point.latitude, point.longitude)
            coVerify { teleport.execute(point) }
            verify(exactly = 0) { starter.start(any()) }
        }

    @Test
    fun `idle starts the service and persists the point`() =
        runTest {
            state.value = MockLocationState.IDLE
            handler.handle(point.latitude, point.longitude)
            verify { starter.start(intent) }
            coVerify { settings.setLastLocation(point) }
            coVerify(exactly = 0) { teleport.execute(any()) }
        }

    @Test
    fun `invalid coordinates do nothing`() =
        runTest {
            handler.handle(null, point.longitude)
            coVerify(exactly = 0) { teleport.execute(any()) }
            verify(exactly = 0) { starter.start(any()) }
            coVerify(exactly = 0) { settings.setLastLocation(any()) }
        }
}
