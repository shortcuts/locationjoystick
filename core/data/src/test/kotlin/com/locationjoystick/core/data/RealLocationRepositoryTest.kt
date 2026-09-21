package com.locationjoystick.core.data

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import com.locationjoystick.core.model.LatLng
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Unit tests run with Build.VERSION.SDK_INT == 0, so the isFromMockProvider branch is the one exercised.
class RealLocationRepositoryTest {
    private val lm = mockk<LocationManager>()

    private fun repository(permission: Int = PackageManager.PERMISSION_GRANTED): RealLocationRepository {
        val context =
            mockk<Context> {
                every { checkSelfPermission(any()) } returns permission
                every { getSystemService(LocationManager::class.java) } returns lm
            }
        return RealLocationRepository(context)
    }

    private fun fix(
        lat: Double,
        lon: Double,
        time: Long,
        mock: Boolean = false,
    ): Location =
        mockk {
            every { latitude } returns lat
            every { longitude } returns lon
            every { this@mockk.time } returns time
            every { isFromMockProvider } returns mock
        }

    @Test
    fun `returns null and never queries LocationManager without fine location permission`() {
        val result = repository(PackageManager.PERMISSION_DENIED).lastKnownRealPosition()

        assertNull(result)
        verify(exactly = 0) { lm.getLastKnownLocation(any()) }
    }

    @Test
    fun `excludes a mock-provider fix`() {
        every { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) } returns fix(1.0, 2.0, 10L, mock = true)
        every { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } returns null

        assertNull(repository().lastKnownRealPosition())
    }

    @Test
    fun `returns the newest of the real fixes`() {
        every { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) } returns fix(1.0, 2.0, 10L)
        every { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } returns fix(3.0, 4.0, 20L)

        assertEquals(LatLng(3.0, 4.0), repository().lastKnownRealPosition())
    }

    @Test
    fun `skips a provider that throws and still uses the other`() {
        every { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) } throws SecurityException("boom")
        every { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } returns fix(10.0, 20.0, 1L)

        assertEquals(LatLng(10.0, 20.0), repository().lastKnownRealPosition())
    }

    @Test
    fun `returns null when no provider has a fix`() {
        every { lm.getLastKnownLocation(any()) } returns null

        assertNull(repository().lastKnownRealPosition())
    }
}
