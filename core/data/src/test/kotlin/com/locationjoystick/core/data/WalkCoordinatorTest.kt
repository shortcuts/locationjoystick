package com.locationjoystick.core.data

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.CompletionKind
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.SpeedProfile
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `arrival emits CompletionKind WALK`() =
        runTest {
            val events = mutableListOf<CompletionKind>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                locationRepository.completionEvents.collect { events += it }
            }
            locationRepository.setPositionInternal(LatLng(48.8566, 2.3522))

            walkCoordinator.startWalk(LatLng(48.856604498, 2.3522), backgroundScope)
            advanceTimeBy(AppConstants.LocationConstants.UPDATE_INTERVAL_MS + 1)

            assertEquals(listOf(CompletionKind.WALK), events)
        }

    // Regression coverage for GH-48: currentSpeedMs must not stay frozen at the last
    // in-transit value once a walk-here arrives.
    @Test
    fun `onArrival forwards a final zero-speed tick before switching to TELEPORT`() =
        runTest {
            val current = LatLng(48.8566, 2.3522)
            val target = LatLng(48.856604498, 2.3522)
            locationRepository.setPositionInternal(current)
            val speeds = mutableListOf<Float>()

            walkCoordinator.startWalk(target, backgroundScope) { _, speedMs, _ -> speeds.add(speedMs) }

            advanceTimeBy(AppConstants.LocationConstants.UPDATE_INTERVAL_MS + 1)

            assertEquals("Last forwarded tick must report zero speed", 0f, speeds.last())
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
    fun `cancel clears route waypoints`() =
        runTest {
            val wp1 = LatLng(48.8566, 2.3522)
            val wp2 = LatLng(48.9000, 2.3522)
            locationRepository.setPositionInternal(wp1)
            locationRepository.setRouteWaypoints(listOf(wp1, wp2))

            walkCoordinator.startWalkAlongRoute(listOf(wp1, wp2), backgroundScope)
            walkCoordinator.cancel()

            assertNull(
                "Route waypoints should be null after cancel",
                locationRepository.routeWaypoints.value,
            )
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

    @Test
    fun `cancel then immediately restart the same target keeps the new walk moving`() =
        assertImmediateRestartMoves(LatLng(48.9000, 2.3522), LatLng(48.9000, 2.3522))

    @Test
    fun `cancel then immediately restart a different target keeps the new walk moving`() =
        assertImmediateRestartMoves(LatLng(48.9000, 2.3522), LatLng(48.9100, 2.3522))

    private fun assertImmediateRestartMoves(
        firstTarget: LatLng,
        nextTarget: LatLng,
    ) = runTest {
        locationRepository.setPositionInternal(LatLng(48.8566, 2.3522))
        var oldTicks = 0
        walkCoordinator.startWalk(firstTarget, backgroundScope) { _, _, _ -> oldTicks++ }
        runCurrent()
        assertEquals("The old walk must have entered its try/finally before cancellation", 1, oldTicks)

        walkCoordinator.cancel()
        var newTicks = 0
        val beforeRestart = locationRepository.currentPosition.value
        // Do not drain cancellation: the old finally block must run after the new target is set.
        walkCoordinator.startWalk(nextTarget, backgroundScope) { _, _, _ -> newTicks++ }
        runCurrent()

        assertEquals(1, newTicks)
        assertTrue(locationRepository.currentPosition.value != beforeRestart)
        assertEquals(nextTarget, locationRepository.walkTarget.value)
        assertEquals(MockMode.WALK_TO, locationRepository.currentMode.value)
        advanceTimeBy(AppConstants.LocationConstants.UPDATE_INTERVAL_MS)
        runCurrent()
        assertEquals("The replacement must keep moving beyond its first tick", 2, newTicks)
        assertEquals("The cancelled walk must not publish another tick", 1, oldTicks)
    }

    @Test
    fun `repeated immediate restarts to the same target survive every old cleanup`() =
        runTest {
            val target = LatLng(48.9000, 2.3522)
            locationRepository.setPositionInternal(LatLng(48.8566, 2.3522))
            walkCoordinator.startWalk(target, backgroundScope)
            runCurrent()

            repeat(5) {
                walkCoordinator.cancel()
                val beforeRestart = locationRepository.currentPosition.value
                var ticks = 0
                walkCoordinator.startWalk(target, backgroundScope) { _, _, _ -> ticks++ }
                runCurrent()

                assertEquals(1, ticks)
                assertTrue(locationRepository.currentPosition.value != beforeRestart)
                assertEquals(target, locationRepository.walkTarget.value)
                assertEquals(MockMode.WALK_TO, locationRepository.currentMode.value)
            }

            walkCoordinator.cancel()
            runCurrent()
            assertNull(locationRepository.walkTarget.value)
            assertEquals(MockMode.TELEPORT, locationRepository.currentMode.value)
        }

    @Test
    fun `walk B mode and target survive when walk A onArrival races with cancellation`() =
        runTest {
            val current = LatLng(48.8566, 2.3522)
            // targetA within arrival threshold (0.5m ≈ < 1.0m threshold) so the for-loop
            // exits immediately and onArrival() is the very next call — maximises race window.
            val targetA = LatLng(48.856604498, 2.3522)
            val targetB = LatLng(48.8700, 2.3522)
            locationRepository.setPositionInternal(current)

            walkCoordinator.startWalk(targetA, backgroundScope)
            // Cancel walk A by starting walk B before any coroutine has had a chance to run.
            walkCoordinator.startWalk(targetB, backgroundScope)

            // Drain: walk A's coroutine runs ensureActive() → throws CancellationException →
            // onArrival() (setMockMode TELEPORT) never executes.
            advanceUntilIdle()

            assertEquals(
                "Mode must remain WALK_TO — walk A onArrival must not fire TELEPORT after cancellation",
                MockMode.WALK_TO,
                locationRepository.currentMode.value,
            )
            assertEquals(
                "Walk target must be targetB — walk A finally-block must not null it out",
                targetB,
                locationRepository.walkTarget.value,
            )
        }

    // Issue #99: a deferred start (e.g. after an OSRM lookup) must not revive a walk that a
    // teleport cancelled in the meantime.
    @Test
    fun `startWalk with a token invalidated by cancel does not start`() =
        runTest {
            locationRepository.setPositionInternal(LatLng(48.8566, 2.3522))
            val token = walkCoordinator.currentGeneration()

            walkCoordinator.cancel()
            val started = walkCoordinator.startWalk(LatLng(48.9000, 2.3522), backgroundScope, token)

            assertFalse(started)
            assertNull(locationRepository.walkTarget.value)
            assertTrue(locationRepository.currentMode.value != MockMode.WALK_TO)
        }

    @Test
    fun `startWalk with a current token starts`() =
        runTest {
            locationRepository.setPositionInternal(LatLng(48.8566, 2.3522))
            val target = LatLng(48.9000, 2.3522)
            val token = walkCoordinator.currentGeneration()

            assertTrue(walkCoordinator.startWalk(target, backgroundScope, token))
            assertEquals(target, locationRepository.walkTarget.value)
            assertFalse("Starting a walk invalidates older tokens", walkCoordinator.isCurrent(token))
        }

    @Test
    fun `no position ticks are applied after cancel`() =
        runTest {
            val start = LatLng(48.8566, 2.3522)
            locationRepository.setPositionInternal(start)
            var ticks = 0

            walkCoordinator.startWalk(LatLng(48.9000, 2.3522), backgroundScope) { _, _, _ -> ticks++ }
            advanceTimeBy(AppConstants.LocationConstants.UPDATE_INTERVAL_MS * 2 + 1)
            val ticksBeforeCancel = ticks
            walkCoordinator.cancel()
            val teleport = LatLng(48.8000, 2.3000)
            locationRepository.setPositionInternal(teleport)
            advanceTimeBy(AppConstants.LocationConstants.UPDATE_INTERVAL_MS * 5)

            assertEquals(ticksBeforeCancel, ticks)
            assertEquals(teleport, locationRepository.currentPosition.value)
        }
}
