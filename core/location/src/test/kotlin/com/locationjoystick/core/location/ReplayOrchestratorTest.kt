package com.locationjoystick.core.location

import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RoamingRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.WalkToEngine
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.routing.RouteReplayEngine
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReplayOrchestratorTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val locationRepository = LocationRepository()
    private val routeRepository: RouteRepository = mockk(relaxed = true)
    private val roamingRepository: RoamingRepository = mockk(relaxed = true)
    private val routeReplayEngine: RouteReplayEngine = mockk(relaxed = true)
    private val walkToEngine: WalkToEngine = mockk(relaxed = true)

    private val stateChanges = mutableListOf<MockLocationState>()
    private var startUpdateLoopCalled = false

    private lateinit var orchestrator: ReplayOrchestrator

    @Before
    fun setup() {
        stateChanges.clear()
        startUpdateLoopCalled = false
        orchestrator =
            ReplayOrchestrator(
                locationRepository = locationRepository,
                routeRepository = routeRepository,
                roamingRepository = roamingRepository,
                routeReplayEngine = routeReplayEngine,
                walkToEngine = walkToEngine,
                scope = kotlinx.coroutines.CoroutineScope(dispatcher),
                onStateChange = { stateChanges.add(it) },
                onPositionChange = { _, _ -> },
                onSpeedChange = { },
                pushLocationUpdate = { },
                startUpdateLoop = { startUpdateLoopCalled = true },
            )
    }

    @Test
    fun handlePause_pausesEngine_and_emitsState() {
        orchestrator.handlePause()

        verify { routeReplayEngine.pause() }
        assertEquals(MockLocationState.PAUSED, stateChanges.last())
        assertEquals(MockLocationState.PAUSED, locationRepository.mockLocationState.value)
    }

    @Test
    fun handleResume_emitsRunningState_and_startsEngine() {
        orchestrator.handleResume(1.4)

        verify { routeReplayEngine.resume(any(), any()) }
        assertTrue(stateChanges.contains(MockLocationState.RUNNING))
    }

    @Test
    fun handleStop_whenRunning_callsStartUpdateLoop() =
        runTest {
            locationRepository.startSpoofing()
            assertEquals(MockLocationState.RUNNING, locationRepository.mockLocationState.value)

            orchestrator.handleStop()

            assertTrue(startUpdateLoopCalled)
        }

    @Test
    fun handleStop_whenPaused_doesNotCallStartUpdateLoop() =
        runTest {
            locationRepository.pauseSpoofing()
            assertEquals(MockLocationState.PAUSED, locationRepository.mockLocationState.value)

            orchestrator.handleStop()

            assertFalse(startUpdateLoopCalled)
        }

    @Test
    fun handleStop_clearsModeToTeleport() =
        runTest {
            orchestrator.handleStop()

            assertEquals(MockMode.TELEPORT, locationRepository.currentMode.value)
        }

    @Test
    fun handleStop_stopsEngine() =
        runTest {
            orchestrator.handleStop()

            coVerify { routeReplayEngine.stop() }
        }

    @Test
    fun handleCancel_whenRunning_callsStartUpdateLoop() =
        runTest {
            locationRepository.startSpoofing()

            orchestrator.handleCancel()

            assertTrue(startUpdateLoopCalled)
        }

    @Test
    fun handleCancel_whenIdle_doesNotCallStartUpdateLoop() =
        runTest {
            assertEquals(MockLocationState.IDLE, locationRepository.mockLocationState.value)

            orchestrator.handleCancel()

            assertFalse(startUpdateLoopCalled)
        }

    @Test
    fun handleCancel_clearsModeToTeleport() =
        runTest {
            orchestrator.handleCancel()

            assertEquals(MockMode.TELEPORT, locationRepository.currentMode.value)
        }
}
