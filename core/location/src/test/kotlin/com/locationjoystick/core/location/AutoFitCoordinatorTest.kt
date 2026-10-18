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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

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
        val offsets = leg.positions.map { CalibrationWalkMath.alongTrackOffset(leg.positions[0], it, leg.bearingDeg, lat0) }
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

    private fun TestScope.coordinator(fullFit: SolveResult = SolveResult.Ok(GroundFit(cal, 0.7, 120), FeetPixel(16.0, 40.0))) =
        AutoFitCoordinator(mapController, locationRepository, source, settings, context, backgroundScope as CoroutineScope).also {
            it.nowMs = { currentTime }
            it.analysisDispatcher = UnconfinedTestDispatcher(testScheduler)
            it.ioDispatcher = UnconfinedTestDispatcher(testScheduler) // file writes in virtual time, so ticks stay 3 s apart
            it.measure = { leg, fx, fy, w, h -> fakeMeasure(leg, fx, fy, w, h) }
            it.fullFit = { _, _, _ -> fullFit }
            it.observe(backgroundScope)
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

            assertTrue(c.learnMap())

            assertEquals(FitHealth.Valid, c.health.value)
            assertNotNull(stored.value)
            assertEquals(CalibrationWalkMath.LEARNING_BEARINGS_DEG.size, walks.size)
            assertEquals(CalibrationWalkMath.LEARNING_LEG_METERS, haversineDistance(start, walks[0]), 0.2)
            verify(exactly = 0) { mapController.teleportTo(any()) }
        }

    @Test
    fun `learning is refused while the game is not in front`() =
        runTest {
            fakeWalks()
            every { source.gameInFront } returns false
            val c = coordinator()
            assertFalse(c.learnMap())
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

    private companion object {
        const val W = 32
        const val H = 64
        const val GROUND_W = 1080.0
        const val GROUND_H = 2340.0
    }
}
