package com.locationjoystick.core.location

import android.content.Context
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.haversineDistance
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.BandFlow
import com.locationjoystick.core.model.CalibrationWalkMath
import com.locationjoystick.core.model.CapturedLeg
import com.locationjoystick.core.model.FeetPixel
import com.locationjoystick.core.model.FitHealth
import com.locationjoystick.core.model.GrayImage
import com.locationjoystick.core.model.GroundCalibration
import com.locationjoystick.core.model.GroundFit
import com.locationjoystick.core.model.GroundMap
import com.locationjoystick.core.model.GroundOffset
import com.locationjoystick.core.model.GuidedPath
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.LegMove
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.SolveResult
import com.locationjoystick.core.model.StoredGroundCalibration
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalCoroutinesApi::class)
class AutoFitCoordinatorTest {
    @get:Rule val tmp = TemporaryFolder()

    private val start = LatLng(48.8566, 2.3522)
    private lateinit var locationRepository: LocationRepository
    private lateinit var mapController: MapController
    private lateinit var source: ScreenCaptureSource
    private lateinit var settings: SettingsRepository
    private lateinit var context: Context
    private val walks = mutableListOf<LatLng>()
    private val spoolDir get() = java.io.File(tmp.root, "tap_autofit")
    private val stored = MutableStateFlow<StoredGroundCalibration?>(null)
    private val tapToWalk = MutableStateFlow(true)

    private val cal = GroundCalibration(-12.0, 7.0, 6.0, -0.002, 540.0, 1490.0)
    private val storedFit = StoredGroundCalibration(cal, W, H, 1L, 120, 0.7)

    /** The camera the fake game is currently showing; tests change it to simulate a twist or a zoom. */
    private var camera = cal

    private fun frame() = GrayImage(W, H, ByteArray(W * H))

    @Before
    fun setUp() {
        locationRepository = LocationRepository()
        locationRepository.startSpoofing()
        locationRepository.setPositionInternal(start)
        locationRepository.setMockMode(MockMode.TELEPORT)
        mapController = mockk(relaxed = true)
        source = mockk()
        every { source.isAvailable } returns true
        every { source.gameInFront } returns true
        every { source.captureAllowed } returns tapToWalk
        coEvery { source.captureGrayFrame() } answers { frame() }
        settings = mockk(relaxed = true)
        every { settings.getTapToWalkOverlayEnabled() } returns tapToWalk
        every { settings.getGuidedPath() } returns flowOf(GuidedPath.POKEMON_GO)
        every { settings.getGroundCalibration() } returns stored
        coEvery { settings.setGroundCalibration(any()) } answers { stored.value = firstArg() }
        context = mockk()
        every { context.cacheDir } returns tmp.root
    }

    /** Fakes the walk engine: each walkTo sets the target and moves at the walking speed in virtual time. */
    private fun TestScope.fakeWalks() {
        every { mapController.walkTo(any(), any()) } answers {
            val target = firstArg<LatLng>()
            walks += target
            locationRepository.setWalkTarget(target)
            backgroundScope.launch {
                val from = locationRepository.currentPosition.value!!
                val total = haversineDistance(from, target)
                var walked = 0.0
                while (walked < total) {
                    delay(1000)
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

    /** Band flows the current [camera] would show for each frame pair of the leg, at the test screen size. */
    private fun fakeMeasure(
        leg: CapturedLeg,
        @Suppress("UNUSED_PARAMETER") feetX: Double,
        @Suppress("UNUSED_PARAMETER") feetY: Double,
        w: Int,
        h: Int,
    ): List<LegMove> {
        val map = GroundMap(camera, GROUND_W, GROUND_H)
        val sx = w / GROUND_W
        val sy = h / GROUND_H
        val lat0 = leg.positions[0].latitude
        val offsets = leg.positions.map { CalibrationWalkMath.offset(leg.positions[0], it, lat0) }
        return (0 until leg.positions.size - 4).map { i ->
            val d = GroundOffset(offsets[i + 4].eastMeters - offsets[i].eastMeters, offsets[i + 4].northMeters - offsets[i].northMeters)
            val bands =
                listOf(600.0, 800.0, 1000.0, 1200.0, 1350.0).flatMap { y ->
                    listOf(250.0, 850.0).map { x ->
                        val g = map.pixelToGround(x, y)!!
                        val (qx, qy) = map.groundToPixel(g.eastMeters - d.eastMeters, g.northMeters - d.northMeters)
                        BandFlow((qx - x) * sx, (qy - y) * sy, 0.9, x * sx, y * sy)
                    }
                }
            LegMove(d, bands)
        }
    }

    private fun TestScope.coordinator(
        fullFit: SolveResult = SolveResult.Ok(GroundFit(cal, 0.7, 120), FeetPixel(16.0, 40.0)),
        serviceScope: CoroutineScope = backgroundScope,
        appScope: CoroutineScope = backgroundScope,
    ) = AutoFitCoordinator(mapController, locationRepository, source, settings, context, appScope).also {
        it.nowMs = { currentTime }
        it.analysisDispatcher = UnconfinedTestDispatcher(testScheduler)
        it.ioDispatcher = UnconfinedTestDispatcher(testScheduler) // file writes in virtual time, so ticks stay 3 s apart
        it.freeBytes = { Long.MAX_VALUE } // the real disk may sit under the free-space floor
        it.measure = { leg, fx, fy, w, h -> fakeMeasure(leg, fx, fy, w, h) }
        it.fullFit = { _, _, _, _ -> fullFit }
        it.observe(serviceScope)
    }

    /** A user walk of [meters] along [bearing] from the current position, then idle ticks so the leg closes and is analysed. */
    private suspend fun TestScope.walkLeg(
        bearing: Double,
        meters: Double = 30.0,
    ) {
        val from = locationRepository.currentPosition.value!!
        mapController.walkTo(CalibrationWalkMath.commandedTarget(from, bearing, meters), null)
        delay((meters / AppConstants.ProfileConstants.WALK_SPEED_MPS * 1000).toLong() + 15_000)
    }

    @Test
    fun `a camera change turns the fit suspect and the next two bearings re-fit it`() =
        runTest {
            fakeWalks()
            stored.value = storedFit
            val refit = GroundCalibration(18.0, 11.0, 9.0, -0.003, 540.0, 1490.0)
            val c = coordinator(SolveResult.Ok(GroundFit(refit, 0.8, 130), FeetPixel(16.0, 40.0)))
            delay(1)

            walkLeg(0.0) // camera unchanged: nothing to repair, nothing kept
            assertEquals(FitHealth.Valid, c.health.value)
            assertEquals(cal, stored.value!!.calibration)

            camera = cal.copy(headingDeg = 18.0, lateralPxPerM = 11.0, forwardPxPerM = 9.0, perspective = -0.003) // twist and zoom
            walkLeg(90.0)
            assertEquals("one bearing cannot pin four terms", FitHealth.Suspect, c.health.value)
            assertEquals(cal, stored.value!!.calibration)

            walkLeg(45.0)
            assertEquals(FitHealth.Valid, c.health.value)
            assertEquals(refit, stored.value!!.calibration)
            val left =
                spoolDir
                    .walkTopDown()
                    .filter { it.isFile }
                    .map { it.path.removePrefix(spoolDir.path) }
                    .toList()
            assertTrue("frames are gone once the fit is valid, left: $left", left.isEmpty())
        }

    @Test
    fun `a suspect fit is still suspect after a new coordinator observes the stored record`() =
        runTest {
            fakeWalks()
            stored.value = storedFit
            val first = coordinator()
            delay(1)
            camera = cal.copy(headingDeg = 18.0, lateralPxPerM = 11.0, forwardPxPerM = 9.0, perspective = -0.003)
            walkLeg(90.0)
            assertEquals(FitHealth.Suspect, first.health.value)

            val restarted = coordinator()
            delay(1)
            assertEquals(FitHealth.Suspect, restarted.health.value)
        }

    /** Suspect fit, then [bearings] as legs; returns how many full fits ran and the coordinator. */
    private suspend fun TestScope.refitCalls(
        fullFit: SolveResult,
        vararg bearings: Double,
    ): Pair<Int, AutoFitCoordinator> {
        fakeWalks()
        stored.value = storedFit
        val c = coordinator()
        var calls = 0
        c.fullFit = { _, _, _, _ ->
            calls++
            fullFit
        }
        delay(1)
        camera = cal.copy(headingDeg = 18.0, lateralPxPerM = 11.0, forwardPxPerM = 9.0, perspective = -0.003)
        bearings.forEach { walkLeg(it) }
        return calls to c
    }

    @Test
    fun `oppositeLegsDoNotTriggerFullRefit`() =
        runTest {
            assertEquals(0, refitCalls(SolveResult.ImplausibleFit, 0.0, 180.0).first)
            assertEquals(0, refitCalls(SolveResult.ImplausibleFit, 45.0, 225.0).first)
        }

    @Test
    fun `legs 0 and 90 still re-fit`() =
        runTest {
            assertEquals(1, refitCalls(SolveResult.ImplausibleFit, 0.0, 90.0).first)
        }

    @Test
    fun `a failed full fit is not retried with the same legs`() =
        runTest {
            // 0 and 90 fail and are dropped; 45 alone cannot span two axes
            assertEquals(1, refitCalls(SolveResult.ImplausibleFit, 0.0, 90.0, 45.0).first)
        }

    @Test
    fun `a closed gate takes no frame`() =
        runTest {
            fakeWalks()
            stored.value = storedFit
            coordinator()
            tapToWalk.value = false
            walkLeg(0.0)
            tapToWalk.value = true
            locationRepository.setMockMode(MockMode.ROUTE_REPLAY)
            repeat(20) {
                // a replay moves the position without a walk target
                delay(3000)
                val p = locationRepository.currentPosition.value!!
                locationRepository.updatePosition(LatLng(p.latitude + 2.0 / 111_320, p.longitude))
            }
            coVerify(exactly = 0) { source.captureGrayFrame() }
        }

    @Test
    fun `learning with no fit walks the learning legs and ends valid`() =
        runTest {
            fakeWalks()
            val c = coordinator()
            delay(1)
            assertEquals(FitHealth.None, c.health.value)

            val outcome = c.learnMap()
            assertEquals(LearnOutcome.Fitted(stored.value!!), outcome)

            assertEquals(FitHealth.Valid, c.health.value)
            assertNotNull(stored.value)
            assertEquals(CalibrationWalkMath.LEARNING_BEARINGS_DEG.size, walks.size)
            assertEquals(CalibrationWalkMath.LEARNING_LEG_METERS, haversineDistance(start, walks[0]), 0.2)
            verify(exactly = 0) { mapController.teleportTo(any()) }
        }

    private fun TestScope.learnAsync(c: AutoFitCoordinator): CompletableDeferred<LearnOutcome> {
        val outcome = CompletableDeferred<LearnOutcome>()
        backgroundScope.launch { outcome.complete(c.learnMap()) }
        return outcome
    }

    @Test
    fun `cancel during learning returns Cancelled`() =
        runTest {
            fakeWalks()
            val c = coordinator()
            delay(1)
            val outcome = learnAsync(c)
            delay(20_000)
            c.cancel()
            assertEquals(LearnOutcome.Cancelled, outcome.await())
        }

    @Test
    fun `walk replaced mid-leg returns Interrupted`() =
        runTest {
            fakeWalks()
            val c = coordinator()
            delay(1)
            val outcome = learnAsync(c)
            while (walks.isEmpty()) delay(1000)
            delay(5_000)
            locationRepository.setWalkTarget(null) // another walk replaced the leg short of its target
            assertEquals(LearnOutcome.Interrupted, outcome.await())
        }

    @Test
    fun `learning state is Analysing after the last leg arrives`() =
        runTest {
            fakeWalks()
            val c = coordinator()
            delay(1)
            val outcome = learnAsync(c)
            while (c.learning.value != LearningState.Analysing) delay(500)
            assertEquals(CalibrationWalkMath.LEARNING_BEARINGS_DEG.size, walks.size)
            outcome.await()
        }

    @Test
    fun `learnMap returns Interrupted when the position changed while the fit was analysed`() =
        runTest {
            fakeWalks()
            val c = coordinator()
            delay(1)
            val outcome = learnAsync(c)
            while (c.learning.value != LearningState.Analysing) delay(500)
            val p = locationRepository.currentPosition.value!!
            locationRepository.updatePosition(LatLng(p.latitude + 0.01, p.longitude)) // a teleport while the fit runs
            assertEquals(LearnOutcome.Interrupted, outcome.await())
        }

    @Test
    fun `learning is refused while the game is not in front`() =
        runTest {
            fakeWalks()
            every { source.gameInFront } returns false
            val c = coordinator()
            assertEquals(LearnOutcome.NotReady, c.learnMap())
            verify(exactly = 0) { mapController.walkTo(any(), any()) }
        }

    @Test
    fun `cancel stops the learning walk and drops the buffered frames`() =
        runTest {
            fakeWalks()
            val c = coordinator()
            delay(1)
            val result = backgroundScope.launch { c.learnMap() }
            delay(20_000)
            c.cancel()
            delay(30_000)
            result.join()
            verify { mapController.stopWalk() }
            assertEquals(LearningState.Idle, c.learning.value)
        }

    @Test
    fun `cancel stops the walk even when the job reaches Idle first`() =
        runTest {
            fakeWalks()
            val eager = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job(backgroundScope.coroutineContext[Job]))
            val c = coordinator(appScope = eager)
            delay(1)
            backgroundScope.launch { c.learnMap() }
            delay(20_000)
            c.cancel()
            verify { mapController.stopWalk() }
        }

    @Test
    fun `a walk started by the user during learning is not stopped`() =
        runTest {
            fakeWalks()
            val c = coordinator()
            delay(1)
            val outcome = learnAsync(c)
            while (walks.isEmpty()) delay(1000)
            delay(5_000)
            locationRepository.setWalkTarget(LatLng(start.latitude + 1, start.longitude)) // the user's own walk
            delay(120_000)
            verify(exactly = 0) { mapController.stopWalk() }
            assertEquals(LearnOutcome.Interrupted, outcome.await())
            assertEquals(LearningState.Idle, c.learning.value)
        }

    @Test
    fun `a joystick pause ends learning at once without stopping the walk`() =
        runTest {
            fakeWalks()
            val c = coordinator()
            delay(1)
            val outcome = learnAsync(c)
            while (walks.isEmpty()) delay(1000)
            delay(5_000)
            locationRepository.setWalkPaused(true)
            delay(2_000)
            assertEquals(LearningState.Idle, c.learning.value)
            verify(exactly = 0) { mapController.stopWalk() }
            outcome.await()
        }

    @Test
    fun `turning Tap to Walk off ends learning within one poll`() =
        runTest {
            fakeWalks()
            every { source.captureAllowed } returns MutableStateFlow(true) // the capture gate must not be what ends learning
            val c = coordinator()
            delay(1)
            learnAsync(c)
            while (walks.isEmpty()) delay(1000)
            delay(5_000)
            tapToWalk.value = false
            delay(2_000)
            assertEquals(LearningState.Idle, c.learning.value)
            verify { mapController.stopWalk() }
        }

    /** A stand-in for the service scope that a test can cancel without ending the test's own scopes. */
    private fun TestScope.serviceScope() = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext[Job]))

    @Test
    fun `cancelling the observe scope during analysis stops the full fit`() =
        runTest {
            fakeWalks()
            val service = serviceScope()
            val c = coordinator(serviceScope = service)
            c.analysisDispatcher = Dispatchers.Default // the fake fit blocks its thread, which a test dispatcher cannot take
            val started = CountDownLatch(1)
            val stopped = CountDownLatch(1)
            val interrupted = AtomicBoolean(false)
            c.fullFit = { _, _, _, checkCancelled ->
                started.countDown()
                try {
                    while (true) {
                        checkCancelled()
                        Thread.sleep(1)
                    }
                    @Suppress("UNREACHABLE_CODE")
                    SolveResult.FeetNotFound
                } catch (e: CancellationException) {
                    interrupted.set(true)
                    throw e
                } finally {
                    stopped.countDown()
                }
            }
            delay(1)
            walkLeg(0.0)
            walkLeg(90.0)

            withContext(Dispatchers.Default) { assertTrue(started.await(10, TimeUnit.SECONDS)) }
            service.cancel()
            withContext(Dispatchers.Default) { assertTrue("the fit kept running", stopped.await(10, TimeUnit.SECONDS)) }
            assertTrue(interrupted.get())
        }

    @Test
    fun `cancelling the observe scope ends the learning walk`() =
        runTest {
            fakeWalks()
            val service = serviceScope()
            val c = coordinator(serviceScope = service)
            delay(1)
            val result = backgroundScope.launch { c.learnMap() }
            while (walks.isEmpty()) delay(1000)
            service.cancel()
            val walked = walks.size
            delay(120_000)

            result.join()
            assertEquals(LearningState.Idle, c.learning.value)
            assertEquals("no walk starts once the service is gone", walked, walks.size)
        }

    @Test
    fun `a leg after the service scope restarts is analysed`() =
        runTest {
            fakeWalks()
            stored.value = storedFit
            val service = serviceScope()
            val c = coordinator(serviceScope = service)
            val frames = mutableListOf<Pair<Int, Int>>()
            c.measure = { leg, fx, fy, w, h ->
                frames += leg.frames().size to leg.positions.size
                fakeMeasure(leg, fx, fy, w, h)
            }
            delay(1)
            val from = locationRepository.currentPosition.value!!
            mapController.walkTo(CalibrationWalkMath.commandedTarget(from, 0.0, 30.0), null)
            delay(10_000) // mid-leg: frames are on disk
            service.cancel()
            delay(5_000)

            c.observe(serviceScope()) // the same walk goes on, so the first frames of the new session are mid-leg
            delay(120_000)

            assertTrue("the leg was measured", frames.isNotEmpty())
            frames.forEach { (read, positions) -> assertEquals("every frame of the leg is readable", positions, read) }
        }

    private fun spoolFiles() = spoolDir.walkTopDown().filter { it.isFile }.toList()

    @Test
    fun `a leftover tap_autofit dir is deleted when observe starts`() =
        runTest {
            val leftover = java.io.File(spoolDir, "9/0.gray").apply { parentFile.mkdirs() }
            leftover.writeBytes(ByteArray(W * H))
            coordinator()
            delay(1)
            assertFalse(spoolDir.exists())
        }

    @Test
    fun `turning Tap to Walk off deletes buffered and pending spools`() =
        runTest {
            fakeWalks()
            coordinator(SolveResult.FeetNotFound)
            delay(1)
            walkLeg(0.0)
            walkLeg(180.0) // parallel: no full fit runs, so the legs stay buffered
            assertTrue("buffered legs sit on disk", spoolFiles().isNotEmpty())

            val from = locationRepository.currentPosition.value!!
            mapController.walkTo(CalibrationWalkMath.commandedTarget(from, 45.0, 300.0), null)
            delay(60_000) // an open leg is on disk too
            tapToWalk.value = false
            delay(AppConstants.TapToWalkConstants.AUTOFIT_FRAME_INTERVAL_MS * 10)

            assertEquals(emptyList<java.io.File>(), spoolFiles())
        }

    @Test
    fun `spool over the byte cap drops the oldest leg`() =
        runTest {
            fakeWalks()
            val c = coordinator(SolveResult.FeetNotFound)
            val cap = 25L * W * H
            c.spoolCapBytes = cap
            delay(1)
            repeat(4) { walkLeg(it * 90.0) }

            val bytes = spoolFiles().sumOf { it.length() }
            assertTrue("spool is $bytes bytes, cap $cap", bytes in 1..(cap + W * H))
        }

    private companion object {
        const val W = 32
        const val H = 64
        const val GROUND_W = 1080.0
        const val GROUND_H = 2340.0
    }
}
