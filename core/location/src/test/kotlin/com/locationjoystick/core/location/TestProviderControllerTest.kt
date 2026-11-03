package com.locationjoystick.core.location

import android.location.LocationManager
import android.location.provider.ProviderProperties
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TestProviderControllerTest {
    private val locationManager = mockk<LocationManager>(relaxed = true)
    private var errors = 0
    private val fix = LocationFix(1.0, 2.0, 3.0, 0f, 0f, false, 5f, 3f, 0f, 1f, null, null)

    @Before
    fun stubBuilder() {
        mockkConstructor(ProviderProperties.Builder::class)
        every { anyConstructed<ProviderProperties.Builder>().build() } returns mockk(relaxed = true)
    }

    @After
    fun unstubBuilder() = unmockkConstructor(ProviderProperties.Builder::class)

    private fun controller(sdkInt: Int) = TestProviderController(locationManager, { errors++ }, sdkInt)

    @Test
    fun `api 34 registers enables and feeds gps network and fused`() {
        val provider = controller(34)

        provider.setup()
        provider.applyFix(fix, 1L)

        for (name in listOf("gps", "network", "fused")) {
            verify(exactly = 1) { locationManager.addTestProvider(name, any<ProviderProperties>()) }
            verify(exactly = 1) { locationManager.setTestProviderEnabled(name, true) }
            verify(exactly = 1) { locationManager.setTestProviderLocation(name, any()) }
        }
    }

    @Test
    fun `api 30 leaves fused alone`() {
        val provider = controller(30)

        provider.setup()
        provider.applyFix(fix, 1L)
        provider.remove()

        verify(exactly = 0) { locationManager.setTestProviderEnabled("fused", any()) }
        verify(exactly = 0) { locationManager.removeTestProvider("fused") }
        verify(exactly = 1) { locationManager.setTestProviderLocation("network", any()) }
    }

    @Test
    fun `remove clears every provider even when the first removal throws`() {
        val provider = controller(34)
        provider.setup()
        every { locationManager.removeTestProvider("gps") } throws IllegalArgumentException()
        clearMocks(locationManager, answers = false, recordedCalls = true)

        provider.remove()

        verify(exactly = 1) { locationManager.removeTestProvider("network") }
        verify(exactly = 1) { locationManager.removeTestProvider("fused") }
    }

    @Test
    fun `extra provider refusal skips it without an error`() {
        every { locationManager.addTestProvider("network", any<ProviderProperties>()) } throws IllegalArgumentException()
        val provider = controller(34)

        provider.setup()
        provider.applyFix(fix, 1L)

        assertEquals(0, errors)
        verify(exactly = 1) { locationManager.setTestProviderLocation("gps", any()) }
        verify(exactly = 0) { locationManager.setTestProviderLocation("network", any()) }
    }

    @Test
    fun `gps refusal reports the error`() {
        every { locationManager.addTestProvider("gps", any<ProviderProperties>()) } throws IllegalArgumentException()

        controller(34).setup()

        assertEquals(1, errors)
    }
}
