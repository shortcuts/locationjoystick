package com.locationjoystick.core.location

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.ElevationRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression test for GitHub issue #51: the anchor used to sit at the flat default and only
 * approach a fetched elevation gradually (0.5 m/tick), so a spoof session starting far from the
 * default altitude took minutes to report the real value.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AltitudeAnchorCoordinatorTest {
    private val elevationRepository = mockk<ElevationRepository>()
    private val settingsRepository =
        mockk<SettingsRepository> {
            every { getBaseAltitudeOverride() } returns flowOf(null)
        }
    private val coordinator =
        AltitudeAnchorCoordinator(
            elevationRepository = elevationRepository,
            settingsRepository = settingsRepository,
            locationRepository = LocationRepository(),
        )

    @Test
    fun `instant fetch on start jumps anchor directly instead of leaving it to converge`() =
        TestScope(StandardTestDispatcher()).runTest {
            coEvery { elevationRepository.fetchElevationMeters(1.0, 2.0) } returns 300.0

            coordinator.maybeFetchElevation(
                this,
                nowMs = 0L,
                lat = 1.0,
                lon = 2.0,
                realElevationEnabled = true,
                instant = true,
                force = true,
            )
            advanceUntilIdle()

            // stepConverge(0) proves the anchor already sits at the target — no step was needed.
            val anchor = coordinator.stepConverge(maxStep = 0.0)
            assertEquals(300.0, anchor, 0.5)
        }

    @Test
    fun `force bypasses the fetch-interval throttle for a teleport right after startup`() =
        TestScope(StandardTestDispatcher()).runTest {
            coEvery { elevationRepository.fetchElevationMeters(1.0, 2.0) } returns 300.0
            coEvery { elevationRepository.fetchElevationMeters(3.0, 4.0) } returns 500.0

            // First fetch (e.g. startSpoofing) sets lastElevationFetchMs = 0.
            coordinator.maybeFetchElevation(this, nowMs = 0L, lat = 1.0, lon = 2.0, realElevationEnabled = true, force = true)
            advanceUntilIdle()

            // Teleport a moment later, well inside the normal 60s throttle window.
            coordinator.maybeFetchElevation(
                this,
                nowMs = 1_000L,
                lat = 3.0,
                lon = 4.0,
                realElevationEnabled = true,
                instant = true,
                force = true,
            )
            advanceUntilIdle()

            val anchor = coordinator.stepConverge(maxStep = 0.0)
            assertEquals(500.0, anchor, 0.5)
        }

    @Test
    fun `periodic fetch mid-session still converges gradually`() =
        TestScope(StandardTestDispatcher()).runTest {
            coEvery { elevationRepository.fetchElevationMeters(1.0, 2.0) } returns 300.0

            coordinator.maybeFetchElevation(this, nowMs = 100_000L, lat = 1.0, lon = 2.0, realElevationEnabled = true)
            advanceUntilIdle()

            val anchor = coordinator.stepConverge(maxStep = 0.5)
            assertEquals(AppConstants.RealismConstants.DEFAULT_ALTITUDE_METERS + 0.5, anchor, 0.5)
        }
}
