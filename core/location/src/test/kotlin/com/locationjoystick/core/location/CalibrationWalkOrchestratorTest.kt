package com.locationjoystick.core.location

import android.content.Context
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.haversineDistance
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.CalibrationWalkMath
import com.locationjoystick.core.model.FeetPixel
import com.locationjoystick.core.model.GrayImage
import com.locationjoystick.core.model.GroundCalibration
import com.locationjoystick.core.model.GroundFit
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.SolveResult
import com.locationjoystick.core.model.bearingTo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class CalibrationWalkOrchestratorTest {
    @get:Rule val tmp = TemporaryFolder()

    private val start = LatLng(48.8566, 2.3522)
    private lateinit var locationRepository: LocationRepository
    private lateinit var mapController: MapController
    private lateinit var source: CompassHeadingSource
    private lateinit var settings: SettingsRepository
    private lateinit var context: Context
    private val walks = mutableListOf<Pair<LatLng, Double?>>()
    private val spoolDir get() = java.io.File(tmp.root, "tap_calibration")

    private val okFit = GroundFit(GroundCalibration(-12.0, 7.0, 6.0, -0.002, 540.0, 1490.0), 0.7, 120)

    private fun frame(
        w: Int = 32,
        h: Int = 64,
    ) = GrayImage(w, h, ByteArray(w * h))

    @Before
    fun setUp() {
        locationRepository = LocationRepository()
        locationRepository.startSpoofing()
        locationRepository.setPositionInternal(start)
        locationRepository.setMockMode(MockMode.TELEPORT)
        mapController = mockk(relaxed = true)
        source = mockk()
        every { source.isAvailable } returns true
        coEvery { source.captureGrayFrame() } answers { frame() }
        settings = mockk(relaxed = true)
        context = mockk()
        every { context.cacheDir } returns tmp.root
    }

    private enum class World { ARRIVES, STALLS, CANCELLED_EARLY, JUMPS }

    /** Fakes the walk engine: each walkTo sets the target and moves at 0.5556 m/s in virtual time. */
    private fun TestScope.fakeWorld(world: World = World.ARRIVES) {
        every { mapController.walkTo(any(), any()) } answers {
            val target = firstArg<LatLng>()
            walks += target to secondArg<Double?>()
            locationRepository.setWalkTarget(target)
            backgroundScope.launch {
                val from = locationRepository.currentPosition.value!!
                val total = haversineDistance(from, target)
                var walked = 0.0
                while (walked < total) {
                    delay(1000)
                    when (world) {
                        World.STALLS -> continue
                        World.CANCELLED_EARLY -> {
                            locationRepository.setWalkTarget(null)
                            return@launch
                        }
                        World.JUMPS -> {
                            locationRepository.updatePosition(LatLng(from.latitude + 0.001, from.longitude))
                            continue
                        }
                        World.ARRIVES -> Unit
                    }
                    walked = minOf(total, walked + AppConstants.ProfileConstants.WALK_SPEED_MPS)
                    val f = walked / total
                    locationRepository.updatePosition(
                        LatLng(
                            from.latitude + (target.latitude - from.latitude) * f,
                            from.longitude + (target.longitude - from.longitude) * f,
                        ),
                    )
                }
                locationRepository.setWalkTarget(null)
            }
            Unit
        }
    }

    private fun TestScope.orchestrator(solve: SolveResult = SolveResult.Ok(okFit, FeetPixel(16.0, 40.0))) =
        CalibrationWalkOrchestrator(mapController, locationRepository, source, settings, context, backgroundScope as CoroutineScope).also {
            it.nowMs = { currentTime }
            it.solve = { _, _, _, _ -> solve }
        }

    private suspend fun CalibrationWalkOrchestrator.terminal() =
        state.first { it is CalibrationState.Done || it is CalibrationState.Failed || it is CalibrationState.Idle && walks.isNotEmpty() }

    private fun assertNothingStored() {
        coVerify(exactly = 0) { settings.setGroundCalibration(any()) }
        assertFalse(spoolDir.exists())
    }

    @Test
    fun `walks eight 40 m legs at walking speed and stores the fit for the frame size`() =
        runTest {
            fakeWorld()
            val o = orchestrator()
            assertTrue(o.start())

            val done = o.terminal() as CalibrationState.Done

            assertEquals(8, walks.size)
            assertTrue(walks.all { it.second == AppConstants.ProfileConstants.WALK_SPEED_MPS })
            var from = start
            for ((i, w) in walks.withIndex()) {
                assertEquals(40.0, haversineDistance(from, w.first), 0.2)
                assertEquals(CalibrationWalkMath.BEARINGS_DEG[i], from.bearingTo(w.first), 0.5)
                from = w.first
            }
            assertEquals(32, done.result.screenWidth)
            assertEquals(64, done.result.screenHeight)
            assertEquals(okFit.calibration, done.result.calibration)
            coVerify(exactly = 1) { settings.setGroundCalibration(done.result) }
            assertFalse("spool is emptied", spoolDir.exists())
        }

    @Test
    fun `not ready outside joystick or teleport mode`() =
        runTest {
            locationRepository.setMockMode(MockMode.ROUTE_REPLAY)
            val o = orchestrator()

            assertFalse(o.start())

            assertEquals(CalibrationState.Failed(CalibrationFailure.NOT_READY), o.state.value)
            verify(exactly = 0) { mapController.walkTo(any(), any()) }
        }

    @Test
    fun `service unavailable when the bridge is unbound or the disclosure is not accepted`() =
        runTest {
            for (unbound in listOf(true, false)) {
                every { source.isAvailable } returns !unbound
                coEvery { source.captureGrayFrame() } returns null
                val o = orchestrator()
                assertTrue(o.start())
                assertEquals(CalibrationState.Failed(CalibrationFailure.SERVICE_UNAVAILABLE), o.terminal())
            }
            verify(exactly = 0) { mapController.walkTo(any(), any()) }
            verify(atLeast = 1) { mapController.stopWalk() }
            assertNothingStored()
        }

    @Test
    fun `a leg that never arrives times out`() =
        runTest {
            fakeWorld(World.STALLS)
            val o = orchestrator()
            o.start()

            assertEquals(CalibrationState.Failed(CalibrationFailure.TIMEOUT), o.terminal())
            verify { mapController.stopWalk() }
            assertNothingStored()
        }

    @Test
    fun `a position jump is a speed abort`() =
        runTest {
            fakeWorld(World.JUMPS)
            val o = orchestrator()
            o.start()

            assertEquals(CalibrationState.Failed(CalibrationFailure.SPEED_ABORT), o.terminal())
            assertNothingStored()
        }

    @Test
    fun `a walk cleared far from its target is interrupted`() =
        runTest {
            fakeWorld(World.CANCELLED_EARLY)
            val o = orchestrator()
            o.start()

            assertEquals(CalibrationState.Failed(CalibrationFailure.INTERRUPTED), o.terminal())
            assertNothingStored()
        }

    @Test
    fun `a screenshot of another size aborts the run`() =
        runTest {
            fakeWorld()
            var n = 0
            coEvery { source.captureGrayFrame() } answers { if (n++ < 3) frame() else frame(64, 32) }
            val o = orchestrator()
            o.start()

            assertEquals(CalibrationState.Failed(CalibrationFailure.FRAME_SIZE_CHANGED), o.terminal())
            assertNothingStored()
        }

    @Test
    fun `solver failures map to their reasons and store nothing`() =
        runTest {
            val cases =
                mapOf(
                    SolveResult.FeetNotFound to CalibrationFailure.FEET_NOT_FOUND,
                    SolveResult.TooFewFlows to CalibrationFailure.TOO_FEW_FLOWS,
                    SolveResult.ImplausibleFit to CalibrationFailure.IMPLAUSIBLE_FIT,
                )
            for ((result, reason) in cases) {
                locationRepository.setPositionInternal(start)
                fakeWorld()
                val o = orchestrator(result)
                o.start()
                assertEquals(CalibrationState.Failed(reason), o.terminal())
            }
            assertNothingStored()
        }

    @Test
    fun `cancel stops the walk and returns to idle`() =
        runTest {
            fakeWorld()
            val o = orchestrator()
            o.start()
            delay(10_000)

            o.cancel()

            assertEquals(CalibrationState.Idle, o.state.value)
            verify { mapController.stopWalk() }
            // an in-flight frame write finishes on a real IO thread; cleanup runs after it
            withContext(Dispatchers.Default) {
                val deadline = System.currentTimeMillis() + 5_000
                while (spoolDir.exists() && System.currentTimeMillis() < deadline) Thread.sleep(10)
            }
            assertNothingStored()
        }

    @Test
    fun `second start while running is refused`() =
        runTest {
            fakeWorld()
            val o = orchestrator()
            assertTrue(o.start())
            assertFalse(o.start())
            assertTrue(o.state.value is CalibrationState.Walking)
            o.cancel()
        }
}
