package com.locationjoystick.core.location

import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.CalibrationWalkMath
import com.locationjoystick.core.model.GrayImage
import com.locationjoystick.core.model.GroundCalibration
import com.locationjoystick.core.model.GroundFlowFit
import com.locationjoystick.core.model.GroundMap
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.StoredGroundCalibration
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor

@OptIn(ExperimentalCoroutinesApi::class)
class AutoFitCoordinatorTest {
    private val start = LatLng(48.8566, 2.3522)
    private lateinit var locationRepository: LocationRepository
    private lateinit var source: ScreenCaptureSource
    private lateinit var settings: SettingsRepository
    private val stored = MutableStateFlow<StoredGroundCalibration?>(null)
    private val tapToWalk = MutableStateFlow(true)
    private var clockMs = 0L

    /** The camera the fake game shows. */
    private val camera = GroundFlowFit.calibration(-12.0, 7.0)
    private var scene = camera
    private var popup: GrayImage? = null

    @Before
    fun setUp() {
        locationRepository = LocationRepository()
        locationRepository.startSpoofing()
        locationRepository.setPositionInternal(start)
        locationRepository.setMockMode(MockMode.WALK_TO)
        source = mockk()
        every { source.captureAllowed } returns tapToWalk
        coEvery { source.captureGrayFrame() } answers { popup ?: render(scene, locationRepository.currentPosition.value!!) }
        settings = mockk(relaxed = true)
        every { settings.getGroundCalibration() } returns stored
        coEvery { settings.setGroundCalibration(any()) } answers { stored.value = firstArg() }
    }

    private fun TestScope.coordinator(): AutoFitCoordinator {
        val c = AutoFitCoordinator(locationRepository, source, settings)
        c.nowMs = { clockMs }
        c.analysisDispatcher = Dispatchers.Unconfined
        c.observe(backgroundScope)
        runCurrent() // the loop's first tick sees no move and parks on its delay; the test drives tick() itself
        return c
    }

    /** Moves the player [meters] towards [bearingDeg], [seconds] later, then runs one tick. */
    private suspend fun step(
        c: AutoFitCoordinator,
        bearingDeg: Double,
        meters: Double,
        seconds: Double = 3.0,
    ) {
        val pos = locationRepository.currentPosition.value!!
        locationRepository.updatePosition(CalibrationWalkMath.commandedTarget(pos, bearingDeg, meters))
        clockMs += (seconds * 1000).toLong()
        c.tick()
    }

    private suspend fun primeAndWalk(
        c: AutoFitCoordinator,
        bearingDeg: Double,
        steps: Int,
    ) {
        c.tick() // remembers the position
        repeat(steps) { step(c, bearingDeg, 4.0) }
    }

    @Test
    fun `a straight walk stores the camera heading and scale`() =
        runTest {
            val c = coordinator()
            primeAndWalk(c, 70.0, 4)
            assertNotNull(stored.value)
            val fit = stored.value!!
            assertEquals(W, fit.screenWidth)
            assertEquals(0.0, angleGap(camera.headingDeg, fit.calibration.headingDeg), 2.5)
            assertEquals(camera.lateralPxPerM, fit.calibration.lateralPxPerM, camera.lateralPxPerM * 0.05)
        }

    @Test
    fun `a straight walk writes the stored map once`() =
        runTest {
            val c = coordinator()
            primeAndWalk(c, 70.0, 8)
            coVerify(exactly = 1) { settings.setGroundCalibration(any()) }
        }

    @Test
    fun `standing still takes no screenshot`() =
        runTest {
            val c = coordinator()
            repeat(3) { c.tick() }
            coVerify(exactly = 0) { source.captureGrayFrame() }
        }

    @Test
    fun `tap to walk off takes no screenshot`() =
        runTest {
            val c = coordinator()
            tapToWalk.value = false
            runCurrent()
            primeAndWalk(c, 0.0, 3)
            coVerify(exactly = 0) { source.captureGrayFrame() }
            assertNull(stored.value)
        }

    @Test
    fun `a teleport-speed step is not measured`() =
        runTest {
            val c = coordinator()
            c.tick()
            step(c, 0.0, 4.0)
            step(c, 0.0, 60.0)
            step(c, 0.0, 4.0)
            assertNull(stored.value)
        }

    private fun storedCamera() = StoredGroundCalibration(camera, W, H, 1L, 10, 0.5).also { stored.value = it }

    @Test
    fun `a walk over a static screen keeps the stored map`() =
        runTest {
            val keep = storedCamera()
            popup = GrayImage(W, H, ByteArray(W * H) { (it % 7 * 30).toByte() })
            val c = coordinator()
            primeAndWalk(c, 70.0, 8)
            assertEquals(keep, stored.value)
        }

    @Test
    fun `a two-move walk under another camera keeps the stored map`() =
        runTest {
            val keep = storedCamera()
            scene = GroundFlowFit.calibration(60.0, 7.0)
            val c = coordinator()
            primeAndWalk(c, 70.0, 3)
            assertEquals(keep, stored.value)
        }

    @Test
    fun `a camera twist replaces the stored map within one walk`() =
        runTest {
            storedCamera()
            scene = GroundFlowFit.calibration(60.0, 7.0)
            val c = coordinator()
            primeAndWalk(c, 70.0, 5)
            assertEquals(0.0, angleGap(60.0, stored.value!!.calibration.headingDeg), 2.5)
        }

    private fun angleGap(
        a: Double,
        b: Double,
    ): Double {
        val d = abs(a - b) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }

    /** Screenshot of value-noise ground (1.5 m lattice) seen through [cal] with the player at [at]. */
    private fun render(
        cal: GroundCalibration,
        at: LatLng,
    ): GrayImage {
        val map = GroundMap(cal, W.toDouble(), H.toDouble())
        val e0 = (at.longitude - start.longitude) * CalibrationWalkMath.METERS_PER_DEGREE * cos(Math.toRadians(start.latitude))
        val n0 = (at.latitude - start.latitude) * CalibrationWalkMath.METERS_PER_DEGREE
        val px = ByteArray(W * H)
        for (y in 0 until H) {
            for (x in 0 until W) {
                val g = map.pixelToGround(x.toDouble(), y.toDouble())
                px[y * W + x] = (if (g == null) 200.0 else ground(g.eastMeters + e0, g.northMeters + n0)).toInt().toByte()
            }
        }
        return GrayImage(W, H, px)
    }

    private fun ground(
        e: Double,
        n: Double,
    ): Double {
        val x = e / 1.5
        val y = n / 1.5
        val i = floor(x).toInt()
        val j = floor(y).toInt()
        var fx = x - i
        var fy = y - j
        fx = fx * fx * (3 - 2 * fx)
        fy = fy * fy * (3 - 2 * fy)
        val top = lattice(i, j) * (1 - fx) + lattice(i + 1, j) * fx
        val bot = lattice(i, j + 1) * (1 - fx) + lattice(i + 1, j + 1) * fx
        return top * (1 - fy) + bot * fy
    }

    private fun lattice(
        i: Int,
        j: Int,
    ): Double {
        var v = ((i.toLong() * 73856093L) xor (j.toLong() * 19349663L)) and 0xFFFFFFFFL
        v = (v * 2654435761L) and 0xFFFFFFFFL
        return ((v shr 16) and 0xFFL).toDouble()
    }

    private companion object {
        const val W = 540
        const val H = 1170
    }
}
