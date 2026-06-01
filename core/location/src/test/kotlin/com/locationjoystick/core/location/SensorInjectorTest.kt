package com.locationjoystick.core.location

import com.locationjoystick.core.common.constants.AppConstants.ElevationConstants.GRAVITY
import com.locationjoystick.core.common.constants.AppConstants.ElevationConstants.NOISE_AMPLITUDE_MS2
import com.locationjoystick.core.common.constants.AppConstants.ElevationConstants.TILT_JITTER_DEGREES
import com.locationjoystick.core.model.ElevationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

class SensorInjectorTest {

    private val zeroNoiseRandom = object : Random() {
        override fun nextBits(bitCount: Int) = 0
        // nextFloat() returns 0f → noise = (0*2-1)*AMP = -AMP; use seed 0 for fixed behaviour
    }
    private val seededRandom = Random(42)

    // -------------------------------------------------------------------------
    // Neutral mode
    // -------------------------------------------------------------------------

    @Test
    fun `neutral gravity vector has z close to GRAVITY`() {
        val v = elevationGravityVector(ElevationMode.Neutral, 45f, seededRandom)
        // x is noise-only, y is noise-only, z ≈ GRAVITY ± noise
        assertEquals(GRAVITY, v[2], NOISE_AMPLITUDE_MS2 + 0.01f)
    }

    @Test
    fun `neutral gravity vector y-base is zero within noise`() {
        val v = elevationGravityVector(ElevationMode.Neutral, 45f, seededRandom)
        assertTrue("y should be near 0 for neutral", kotlin.math.abs(v[1]) <= NOISE_AMPLITUDE_MS2 + 0.01f)
    }

    // -------------------------------------------------------------------------
    // TiltUp mode
    // -------------------------------------------------------------------------

    @Test
    fun `tilt-up gravity vector y is negative`() {
        val v = elevationGravityVector(ElevationMode.TiltUp, 45f, seededRandom)
        assertTrue("y should be negative when tilting up", v[1] < 0f)
    }

    @Test
    fun `tilt-up gravity vector magnitude is approximately GRAVITY`() {
        // Run 20 samples; magnitude should always be within noise tolerance of GRAVITY
        repeat(20) {
            val v = elevationGravityVector(ElevationMode.TiltUp, 45f, Random(it.toLong()))
            val mag = sqrt((v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).toDouble()).toFloat()
            assertEquals("magnitude should be ~GRAVITY", GRAVITY, mag, NOISE_AMPLITUDE_MS2 * 3 + 0.05f)
        }
    }

    // -------------------------------------------------------------------------
    // TiltDown mode
    // -------------------------------------------------------------------------

    @Test
    fun `tilt-down gravity vector y is positive`() {
        val v = elevationGravityVector(ElevationMode.TiltDown, 45f, seededRandom)
        assertTrue("y should be positive when tilting down", v[1] > 0f)
    }

    @Test
    fun `tilt-up and tilt-down y-base magnitudes are symmetric`() {
        // Use a zero-noise random (nextFloat always 0.5 → noise = (0.5*2-1)*AMP = 0)
        val zeroNoise = Random(0L)
        // To get zero noise we need nextFloat() == 0.5 exactly, which isn't guaranteed.
        // Instead verify magnitudes match within noise tolerance across many seeds.
        repeat(50) { seed ->
            val up = elevationGravityVector(ElevationMode.TiltUp, 45f, Random(seed.toLong()))
            val down = elevationGravityVector(ElevationMode.TiltDown, 45f, Random(seed.toLong()))
            // Same seed → same noise values; y_up = -base+noise, y_down = +base+noise
            // So y_up + y_down = 2*noise, and |y_down| - |y_up| ≈ 0 when base >> noise
            val sumY = up[1] + down[1] // = 2 * noise(r1) for this seed
            assertTrue(
                "sum of y-components should be small (2*noise), got $sumY",
                kotlin.math.abs(sumY) <= 2 * NOISE_AMPLITUDE_MS2 + 0.01f,
            )
        }
    }

    // -------------------------------------------------------------------------
    // Rotation vector (unit quaternion)
    // -------------------------------------------------------------------------

    @Test
    fun `neutral rotation vector is identity quaternion`() {
        val q = elevationRotationVector(ElevationMode.Neutral, 45f)
        assertEquals(0f, q[0], 0.0001f)
        assertEquals(0f, q[1], 0.0001f)
        assertEquals(0f, q[2], 0.0001f)
        assertEquals(1f, q[3], 0.0001f)
    }

    @Test
    fun `tilt-up rotation vector is unit quaternion`() {
        val q = elevationRotationVector(ElevationMode.TiltUp, 45f)
        val norm = sqrt((q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]).toDouble()).toFloat()
        assertEquals(1f, norm, 0.0001f)
    }

    @Test
    fun `tilt-down rotation vector is unit quaternion`() {
        val q = elevationRotationVector(ElevationMode.TiltDown, 60f)
        val norm = sqrt((q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]).toDouble()).toFloat()
        assertEquals(1f, norm, 0.0001f)
    }

    @Test
    fun `tilt-up x-component is negative, tilt-down is positive`() {
        val up = elevationRotationVector(ElevationMode.TiltUp, 45f)
        val down = elevationRotationVector(ElevationMode.TiltDown, 45f)
        assertTrue(up[0] < 0f)
        assertTrue(down[0] > 0f)
    }

    // -------------------------------------------------------------------------
    // Noise bounds
    // -------------------------------------------------------------------------

    @Test
    fun `noise stays within amplitude bounds`() {
        repeat(1000) { seed ->
            val n = elevationNoise(Random(seed.toLong()))
            assertTrue("noise $n out of bounds", kotlin.math.abs(n) <= NOISE_AMPLITUDE_MS2 + 0.0001f)
        }
    }

    // -------------------------------------------------------------------------
    // Tilt angle variation
    // -------------------------------------------------------------------------

    @Test
    fun `larger tilt angle produces larger y deflection for TiltUp`() {
        val small = elevationGravityVector(ElevationMode.TiltUp, 20f, Random(0))
        val large = elevationGravityVector(ElevationMode.TiltUp, 70f, Random(0))
        assertTrue(
            "larger tilt should produce more negative y",
            large[1] < small[1],
        )
    }
}
