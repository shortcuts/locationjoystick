package com.locationjoystick.core.location

import android.location.LocationManager
import android.os.PowerManager
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.model.MockLocationState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceErrorPropagationTest {
    private val wakeLock = mockk<PowerManager.WakeLock>(relaxed = true) { every { isHeld } returns true }
    private val powerManager =
        mockk<PowerManager> { every { newWakeLock(any(), any()) } returns wakeLock }
    private val locationManager = mockk<LocationManager>(relaxed = true)

    private fun newService(): MockLocationService =
        MockLocationService().apply {
            locationRepository = LocationRepository()
            locationManager = this@ServiceErrorPropagationTest.locationManager
            powerManagerOverride = powerManager
        }

    private val fix =
        LocationFix(1.0, 2.0, 3.0, 0f, 0f, false, 5f, 3f, 0f, 1f, null, null)

    @Test
    fun `addTestProvider SecurityException reaches the repository and releases the wake lock`() {
        every { locationManager.addTestProvider(any(), any<android.location.provider.ProviderProperties>()) } throws SecurityException()
        every { locationManager.addTestProvider(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } throws
            SecurityException()
        val service = newService()
        service.acquireWakeLock()

        service.setupTestProvider()

        assertEquals(MockLocationState.ERROR, service.state.value)
        assertEquals(MockLocationState.ERROR, service.locationRepository.mockLocationState.value)
        verify(exactly = 1) { wakeLock.release() }
    }

    @Test
    fun `revoked mock app on a tick reaches ERROR and a retry re-registers`() {
        val service = newService()
        service.setupTestProvider()
        every { locationManager.setTestProviderLocation(any(), any()) } throws SecurityException()

        service.applyFixOrFail(fix, 1L)

        assertEquals(MockLocationState.ERROR, service.state.value)
        assertEquals(MockLocationState.ERROR, service.locationRepository.mockLocationState.value)
        service.setupTestProvider()
        verify(exactly = 2) { locationManager.setTestProviderEnabled(any(), true) }
    }
}
