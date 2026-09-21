package com.locationjoystick.core.data

import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.testing.FakePreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CaptureCoordinatesRepositoryTest {
    private lateinit var fakeDataStore: FakePreferencesDataStore
    private lateinit var repository: CaptureCoordinatesRepository

    private val mushroom = LatLng(36.977695, 128.363905)
    private val flower = LatLng(36.982194, 128.370129)

    @Before
    fun setUp() {
        fakeDataStore = FakePreferencesDataStore()
        repository = CaptureCoordinatesRepository(fakeDataStore)
    }

    @Test
    fun `capture defaults off with empty points`() =
        runTest {
            assertFalse(repository.captureModeEnabled.first())
            assertFalse(repository.captureEnabled.first())
            assertFalse(repository.jumpEnabled.first())
            assertEquals(emptyList<LatLng>(), repository.points.first())
            assertNull(repository.previousBrowserPackage.first())
            assertFalse(repository.helperOpen.first())
        }

    @Test
    fun `setCaptureEnabled round-trips`() =
        runTest {
            repository.setCaptureEnabled(true)
            assertTrue(repository.captureEnabled.first())
            repository.setCaptureEnabled(false)
            assertFalse(repository.captureEnabled.first())
        }

    @Test
    fun `capture mode round-trips independently`() =
        runTest {
            repository.setCaptureModeEnabled(true)
            assertTrue(repository.captureModeEnabled.first())
            assertFalse(repository.captureEnabled.first())
            repository.setCaptureModeEnabled(false)
            assertFalse(repository.captureModeEnabled.first())
        }

    @Test
    fun `legacy enabled action keeps capture mode on until explicitly changed`() =
        runTest {
            repository.setCaptureEnabled(true)
            assertTrue(repository.captureModeEnabled.first())
            repository.setCaptureModeEnabled(false)
            assertFalse(repository.captureModeEnabled.first())
            assertTrue(repository.captureEnabled.first())
        }

    @Test
    fun `resetSetup turns capture off, flags reset, keeps points and previous browser`() =
        runTest {
            assertFalse(repository.setupReset.first())
            repository.setCaptureModeEnabled(true)
            repository.setCaptureEnabled(true)
            repository.setJumpEnabled(true)
            repository.appendPoint(mushroom)
            repository.setPreviousBrowserPackage("org.mozilla.firefox")

            repository.resetSetup()

            assertFalse(repository.captureModeEnabled.first())
            assertFalse(repository.captureEnabled.first())
            assertFalse(repository.jumpEnabled.first())
            assertTrue(repository.setupReset.first())
            assertEquals(listOf(mushroom), repository.points.first())
            assertEquals("org.mozilla.firefox", repository.previousBrowserPackage.first())

            repository.clearSetupReset()
            assertFalse(repository.setupReset.first())
        }

    @Test
    fun `setJumpEnabled round-trips`() =
        runTest {
            repository.setJumpEnabled(true)
            assertTrue(repository.jumpEnabled.first())
            repository.setJumpEnabled(false)
            assertFalse(repository.jumpEnabled.first())
        }

    @Test
    fun `appendPoint adds and skips exact last duplicate`() =
        runTest {
            repository.appendPoint(mushroom)
            assertEquals(listOf(mushroom), repository.points.first())
            repository.appendPoint(mushroom)
            assertEquals(listOf(mushroom), repository.points.first())
            repository.appendPoint(flower)
            assertEquals(listOf(mushroom, flower), repository.points.first())
        }

    @Test
    fun `clearPoints empties the list`() =
        runTest {
            repository.appendPoint(mushroom)
            repository.clearPoints()
            assertEquals(emptyList<LatLng>(), repository.points.first())
        }

    @Test
    fun `removeLast drops the tail point`() =
        runTest {
            repository.appendPoint(mushroom)
            repository.appendPoint(flower)
            repository.removeLast()
            assertEquals(listOf(mushroom), repository.points.first())
            repository.removeLast()
            assertEquals(emptyList<LatLng>(), repository.points.first())
            repository.removeLast()
            assertEquals(emptyList<LatLng>(), repository.points.first())
        }

    @Test
    fun `previous browser package round-trips`() =
        runTest {
            repository.setPreviousBrowserPackage("com.android.chrome")
            assertEquals("com.android.chrome", repository.previousBrowserPackage.first())
        }

    @Test
    fun `helperOpen round-trips until closed`() =
        runTest {
            repository.setHelperOpen(true)
            assertTrue(repository.helperOpen.first())
            repository.setHelperOpen(false)
            assertFalse(repository.helperOpen.first())
        }
}
