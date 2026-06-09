package com.locationjoystick.core.data

import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.RoamingConfig
import com.locationjoystick.core.routing.RoamingEngine
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RoamingRepositoryTest {
    private lateinit var fakeRoamingEngine: RoamingEngine
    private lateinit var fakeLocationRepository: LocationRepository
    private lateinit var repository: RoamingRepository

    @Before
    fun setUp() {
        fakeRoamingEngine = mockk(relaxed = true)
        fakeLocationRepository = LocationRepository()
        repository = RoamingRepository(fakeRoamingEngine, fakeLocationRepository)
    }

    // isRoaming

    @Test
    fun `isRoaming emits false initially`() =
        runTest {
            assertFalse(repository.isRoaming.first())
        }

    @Test
    fun `isRoaming emits true after startRoaming`() =
        runTest {
            val config = createDefaultConfig()
            repository.startRoaming(config, speedMs = 1.4)

            assertTrue(repository.isRoaming.first())

            repository.stopRoaming()
        }

    @Test
    fun `isRoaming emits false after stopRoaming`() =
        runTest {
            val config = createDefaultConfig()
            repository.startRoaming(config, speedMs = 1.4)
            repository.stopRoaming()

            assertFalse(repository.isRoaming.first())
        }

    // startRoaming

    @Test
    fun `startRoaming sets MockMode to ROAMING`() =
        runTest {
            val config = createDefaultConfig()
            repository.startRoaming(config, speedMs = 1.4)

            assertEquals(MockMode.ROAMING, fakeLocationRepository.currentMode.first())

            repository.stopRoaming()
        }

    @Test
    fun `startRoaming delegates to roamingEngine`() =
        runTest {
            val config = createDefaultConfig()
            repository.startRoaming(config, speedMs = 1.4)

            verify {
                fakeRoamingEngine.startRoaming(
                    config = config,
                    speedMs = 1.4,
                    onRouteUpdate = any(),
                    onComplete = any(),
                    onPositionUpdate = any(),
                )
            }

            repository.stopRoaming()
        }

    @Test
    fun `startRoaming cancels previous active job`() =
        runTest {
            val config = createDefaultConfig()
            repository.startRoaming(config, speedMs = 1.4)
            repository.startRoaming(config, speedMs = 2.0)

            verify(exactly = 2) {
                fakeRoamingEngine.startRoaming(any(), any(), any(), any(), any())
            }

            repository.stopRoaming()
        }

    @Test
    fun `startRoaming onPositionUpdate calls setPositionInternal`() =
        runTest {
            val config = createDefaultConfig()
            var capturedCallback: ((LatLng) -> Unit)? = null
            every {
                fakeRoamingEngine.startRoaming(any(), any(), any(), any(), any())
            } answers {
                capturedCallback = arg(4)
                Job()
            }

            repository.startRoaming(config, speedMs = 1.4)

            val testPosition = LatLng(48.8566, 2.3522)
            capturedCallback?.invoke(testPosition)

            assertEquals(testPosition, fakeLocationRepository.currentPosition.first())

            repository.stopRoaming()
        }

    // stopRoaming

    @Test
    fun `stopRoaming calls roamingEngine stopRoaming`() =
        runTest {
            val config = createDefaultConfig()
            repository.startRoaming(config, speedMs = 1.4)
            repository.stopRoaming()

            coVerifyOrder {
                fakeRoamingEngine.startRoaming(any(), any(), any(), any(), any())
                fakeRoamingEngine.stopRoaming()
            }
        }

    @Test
    fun `stopRoaming sets MockMode to TELEPORT`() =
        runTest {
            val config = createDefaultConfig()
            repository.startRoaming(config, speedMs = 1.4)
            repository.stopRoaming()

            assertEquals(MockMode.TELEPORT, fakeLocationRepository.currentMode.first())
        }

    @Test
    fun `stopRoaming without startRoaming does not throw`() =
        runTest {
            repository.stopRoaming()
        }

    // onComplete callback

    @Test
    fun `roaming completion sets isRoaming to false and MockMode to TELEPORT`() =
        runTest {
            val config = createDefaultConfig()

            var capturedOnComplete: (() -> Unit)? = null
            every {
                fakeRoamingEngine.startRoaming(any(), any(), any(), any(), any())
            } answers {
                capturedOnComplete = arg(3)
                Job()
            }

            repository.startRoaming(config, speedMs = 1.4)

            assertTrue(repository.isRoaming.first())
            assertEquals(MockMode.ROAMING, fakeLocationRepository.currentMode.first())

            capturedOnComplete?.invoke()

            assertFalse(repository.isRoaming.first())
            assertEquals(MockMode.TELEPORT, fakeLocationRepository.currentMode.first())
        }

    @Test
    fun `completion callback fires when loop exits naturally`() =
        runTest {
            val config = createDefaultConfig()

            var capturedOnComplete: (() -> Unit)? = null
            every {
                fakeRoamingEngine.startRoaming(any(), any(), any(), any(), any())
            } answers {
                capturedOnComplete = arg(3)
                Job()
            }

            repository.startRoaming(config, speedMs = 1.4)

            // Simulate natural completion (remainingMeters <= 0 path)
            capturedOnComplete?.invoke()

            assertFalse(repository.isRoaming.first())
            assertFalse(repository.isRoamingPaused.first())
            assertEquals(MockMode.TELEPORT, fakeLocationRepository.currentMode.first())
        }

    // pauseRoaming / resumeRoaming

    @Test
    fun `pauseRoaming sets isRoamingPaused to true`() =
        runTest {
            repository.pauseRoaming()
            assertTrue(repository.isRoamingPaused.first())
        }

    @Test
    fun `resumeRoaming sets isRoamingPaused to false after pause`() =
        runTest {
            repository.pauseRoaming()
            repository.resumeRoaming()
            assertFalse(repository.isRoamingPaused.first())
        }

    @Test
    fun `pauseRoaming delegates to roamingEngine`() =
        runTest {
            repository.pauseRoaming()
            verify { fakeRoamingEngine.pauseRoaming() }
        }

    @Test
    fun `resumeRoaming delegates to roamingEngine`() =
        runTest {
            repository.resumeRoaming()
            verify { fakeRoamingEngine.resumeRoaming() }
        }

    // resetOnServiceDestroy

    @Test
    fun `resetOnServiceDestroy resets isRoaming and isRoamingPaused`() =
        runTest {
            val config = createDefaultConfig()
            repository.startRoaming(config, speedMs = 1.4)
            repository.pauseRoaming()

            repository.resetOnServiceDestroy()

            assertFalse(repository.isRoaming.first())
            assertFalse(repository.isRoamingPaused.first())
        }

    @Test
    fun `resetOnServiceDestroy sets MockMode to TELEPORT`() =
        runTest {
            val config = createDefaultConfig()
            repository.startRoaming(config, speedMs = 1.4)
            repository.resetOnServiceDestroy()

            assertEquals(MockMode.TELEPORT, fakeLocationRepository.currentMode.first())
        }

    // generatePreviewRoute

    @Test
    fun `generatePreviewRoute delegates to engine and returns result`() =
        runTest {
            val center = LatLng(48.8566, 2.3522)
            val destination = LatLng(48.86, 2.36)
            val expected = listOf(center, destination)
            every { fakeRoamingEngine.randomPointInRadius(center, 500.0) } returns destination
            coEvery { fakeRoamingEngine.previewRoute(center, destination, false, "walk") } returns expected

            val result = repository.generatePreviewRoute(center, 500.0, false, "walk")

            assertEquals(expected, result)
        }

    private fun createDefaultConfig(): RoamingConfig =
        RoamingConfig(
            centerPosition = LatLng(48.8566, 2.3522),
            radiusMeters = 500.0,
            distanceMeters = 1000.0,
            useRoadSnapping = false,
            speedProfileId = "walk",
            returnToInitialLocation = false,
        )
}
