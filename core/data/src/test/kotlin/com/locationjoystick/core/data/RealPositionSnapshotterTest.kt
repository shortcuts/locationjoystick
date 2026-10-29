package com.locationjoystick.core.data

import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RealPositionSnapshot
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RealPositionSnapshotterTest {
    private val settings = SettingsRepository(FakeAppPreferencesDataSource())
    private val real = mockk<RealLocationRepository>()
    private val snapshotter = RealPositionSnapshotter(settings, real)

    @Test
    fun `setting off stores nothing and never reads a position`() =
        runTest {
            snapshotter.capture()

            assertNull(settings.getRealPositionSnapshot().first())
            verify(exactly = 0) { real.lastKnownRealPosition() }
        }

    @Test
    fun `setting on stores the position with the capture time`() =
        runTest {
            settings.setLastKnownPositionEnabled(true)
            every { real.lastKnownRealPosition() } returns LatLng(10.0, 20.0)
            val before = System.currentTimeMillis()

            snapshotter.capture()

            val stored = settings.getRealPositionSnapshot().first()!!
            assertEquals(LatLng(10.0, 20.0), stored.position)
            assertTrue(stored.takenAtMs >= before)
        }

    @Test
    fun `no real fix keeps the earlier snapshot`() =
        runTest {
            settings.setLastKnownPositionEnabled(true)
            val earlier = RealPositionSnapshot(LatLng(1.0, 2.0), 5L)
            settings.setRealPositionSnapshot(earlier)
            every { real.lastKnownRealPosition() } returns null

            snapshotter.capture()

            assertEquals(earlier, settings.getRealPositionSnapshot().first())
        }
}
