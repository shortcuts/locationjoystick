package com.locationjoystick.core.location

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.locationjoystick.core.data.SettingsRepository
import io.mockk.Runs
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MockLocationServiceStarterImplTest {
    private val context = mockk<Context>(relaxed = true)
    private val settings = mockk<SettingsRepository>(relaxed = true)
    private val intent = mockk<Intent>()
    private val starter = MockLocationServiceStarterImpl(context, settings, TestScope(UnconfinedTestDispatcher()))

    @Before
    fun setUp() {
        mockkStatic(ContextCompat::class)
        mockkStatic("com.locationjoystick.core.location.MockLocationNotificationKt")
        every { ContextCompat.startForegroundService(any(), any()) } just Runs
        every { createMockLocationNotificationChannels(any()) } just Runs
        every { postMockLocationPermissionErrorNotification(any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkStatic(ContextCompat::class)
        unmockkStatic("com.locationjoystick.core.location.MockLocationNotificationKt")
    }

    private fun grant(
        fine: Boolean,
        coarse: Boolean,
    ) {
        fun result(granted: Boolean) = if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
        every { ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) } returns result(fine)
        every { ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) } returns result(coarse)
    }

    @Test
    fun `missing permission posts error and resets onboarding without starting service`() {
        grant(fine = false, coarse = false)

        starter.start(intent)

        verify(exactly = 0) { ContextCompat.startForegroundService(any(), any()) }
        verify(exactly = 1) { postMockLocationPermissionErrorNotification(context) }
        coVerify(exactly = 1) { settings.setOnboardingComplete(false) }
    }

    @Test
    fun `coarse permission alone starts the service`() {
        grant(fine = false, coarse = true)

        starter.start(intent)

        verify(exactly = 1) { ContextCompat.startForegroundService(context, intent) }
        verify(exactly = 0) { postMockLocationPermissionErrorNotification(any()) }
    }
}
