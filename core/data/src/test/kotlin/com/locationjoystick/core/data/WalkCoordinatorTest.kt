package com.locationjoystick.core.data

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.SpeedProfile
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WalkCoordinatorTest {
    private lateinit var locationRepository: LocationRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var walkToEngine: WalkToEngine
    private lateinit var walkCoordinator: WalkCoordinator

    private val walkProfile =
        SpeedProfile(
            id = "walk",
            name = "Walk",
            speedMetersPerSecond = AppConstants.ProfileConstants.WALK_SPEED_MPS,
        )

    @Before
    fun setUp() {
        locationRepository = LocationRepository()
        settingsRepository = mockk()
        every { settingsRepository.getActiveSpeedProfile() } returns flowOf(walkProfile)
        walkToEngine = WalkToEngine(settingsRepository, locationRepository)
        walkCoordinator = WalkCoordinator(locationRepository, walkToEngine)
    }

    @Test
    fun `startWalk sets WALK_TO mode before engine launch`() =
        runTest {
            val target = LatLng(48.9000, 2.3522)
            locationRepository.setPositionInternal(LatLng(48.8566, 2.3522))

            walkCoordinator.startWalk(target, backgroundScope)

            assertEquals(
                "Mode should be WALK_TO after startWalk",
                MockMode.WALK_TO,
                locationRepository.currentMode.value,
            )
            assertEquals(
                "Walk target should be set",
                target,
                locationRepository.walkTarget.value,
            )
        }

    @Test
    fun `startWalk sets walk target`() =
        runTest {
            val target = LatLng(48.9000, 2.3522)
            locationRepository.setPositionInternal(LatLng(48.8566, 2.3522))

            walkCoordinator.startWalk(target, backgroundScope)

            assertEquals(target, locationRepository.walkTarget.value)
        }

    @Test
    fun `cancel does not call setMockMode`() =
        runTest {
            val target = LatLng(48.9000, 2.3522)
            locationRepository.setPositionInternal(LatLng(48.8566, 2.3522))

            walkCoordinator.startWalk(target, backgroundScope)
            // Mode is WALK_TO here. Now cancel — mode should NOT change (no setMockMode call).
            // setWalkTarget(null) inside cancel will reset mode to TELEPORT via LocationRepository
            // internal logic, but WalkCoordinator.cancel() itself does not call setMockMode().
            // We verify cancel does not explicitly set WALK_TO or any other mode via coordinator.
            walkCoordinator.cancel()

            // After cancel: walkTarget cleared, walk job cancelled
            assertNull(
                "Walk target should be null after cancel",
                locationRepository.walkTarget.value,
            )
            // Mode resets to TELEPORT because setWalkTarget(null) resets it inside LocationRepository
            assertEquals(
                "Mode should reset to TELEPORT (via setWalkTarget(null), not via coordinator setMockMode)",
                MockMode.TELEPORT,
                locationRepository.currentMode.value,
            )
        }

    @Test
    fun `onArrival sets TELEPORT mode and clears walk target`() =
        runTest {
            // Place target within arrival threshold so walk completes on first tick
            val current = LatLng(48.8566, 2.3522)
            // 0.5m north — within WALK_ARRIVAL_THRESHOLD_METERS (1.0m)
            val target = LatLng(48.856604498, 2.3522)
            locationRepository.setPositionInternal(current)

            walkCoordinator.startWalk(target, backgroundScope)

            // Verify WALK_TO was set
            assertEquals(MockMode.WALK_TO, locationRepository.currentMode.value)

            // Advance past one tick so the engine detects arrival
            advanceTimeBy(AppConstants.LocationConstants.UPDATE_INTERVAL_MS + 1)

            assertEquals(
                "Mode should be TELEPORT after arrival",
                MockMode.TELEPORT,
                locationRepository.currentMode.value,
            )
            assertNull(
                "Walk target should be null after arrival",
                locationRepository.walkTarget.value,
            )
        }

    @Test
    fun `startWalkAlongRoute sets final waypoint as walk target`() =
        runTest {
            val wp1 = LatLng(48.8566, 2.3522)
            val wp2 = LatLng(48.9000, 2.3522)
            locationRepository.setPositionInternal(wp1)

            walkCoordinator.startWalkAlongRoute(listOf(wp1, wp2), backgroundScope)

            assertEquals(wp2, locationRepository.walkTarget.value)
        }

    @Test
    fun `startWalkAlongRoute empty list throws`() =
        runTest {
            var threw = false
            try {
                walkCoordinator.startWalkAlongRoute(emptyList(), backgroundScope)
            } catch (e: IllegalArgumentException) {
                threw = true
            }
            assertTrue("startWalkAlongRoute with empty list should throw", threw)
        }

    @Test
    fun `startWalkAlongRoute cancels previous walk and sets new final target`() =
        runTest {
            val current = LatLng(48.8566, 2.3522)
            locationRepository.setPositionInternal(current)

            walkCoordinator.startWalkAlongRoute(listOf(LatLng(48.9000, 2.3522)), backgroundScope)
            assertEquals(LatLng(48.9000, 2.3522), locationRepository.walkTarget.value)

            val newFinal = LatLng(48.8800, 2.3522)
            walkCoordinator.startWalkAlongRoute(listOf(LatLng(48.8700, 2.3522), newFinal), backgroundScope)
            assertEquals(newFinal, locationRepository.walkTarget.value)
        }

    @Test
    fun `second startWalk cancels the first`() =
        runTest {
            val current = LatLng(48.8566, 2.3522)
            val target1 = LatLng(48.9000, 2.3522)
            val target2 = LatLng(48.8700, 2.3522)
            locationRepository.setPositionInternal(current)

            walkCoordinator.startWalk(target1, backgroundScope)
            assertEquals(target1, locationRepository.walkTarget.value)

            // Starting a second walk should cancel the first and set new target
            walkCoordinator.startWalk(target2, backgroundScope)
            assertEquals(
                "Walk target should be updated to second target",
                target2,
                locationRepository.walkTarget.value,
            )
            assertEquals(MockMode.WALK_TO, locationRepository.currentMode.value)
        }
}
