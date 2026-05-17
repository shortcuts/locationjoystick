package com.locationjoystick.core.data

import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.RoamingConfig
import com.locationjoystick.core.routing.RoamingEngine
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
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
                fakeRoamingEngine.startRoaming(any(), any(), any())
            }

            repository.stopRoaming()
        }

    @Test
    fun `startRoaming onPositionUpdate calls setPositionInternal`() =
        runTest {
            val config = createDefaultConfig()
            var capturedCallback: ((LatLng) -> Unit)? = null
            every {
                fakeRoamingEngine.startRoaming(any(), any(), any())
            } answers {
                capturedCallback = thirdArg()
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
                fakeRoamingEngine.startRoaming(any(), any(), any())
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

    // invokeOnCompletion callback

    @Test
    fun `roaming completion sets isRoaming to false and MockMode to TELEPORT`() =
        runTest {
            val config = createDefaultConfig()

            val job = Job()
            every {
                fakeRoamingEngine.startRoaming(any(), any(), any())
            } returns job

            repository.startRoaming(config, speedMs = 1.4)

            assertTrue(repository.isRoaming.first())
            assertEquals(MockMode.ROAMING, fakeLocationRepository.currentMode.first())

            job.complete()

            assertFalse(repository.isRoaming.first())
            assertEquals(MockMode.TELEPORT, fakeLocationRepository.currentMode.first())
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
