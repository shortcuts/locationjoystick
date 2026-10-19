package com.locationjoystick.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseCorrelationTest {
    private val worldW = 420
    private val worldH = 260

    // Zero-mean LCG noise (a DC offset biases phase correlation toward zero shift). cv2.phaseCorrelate on
    // the same crops returns (-4.998, 2.999) with response 0.944 for the first test.
    private val world: FloatArray =
        run {
            var seed = 12345L
            FloatArray(worldW * worldH) {
                seed = seed * 6364136223846793005L + 1442695040888963407L
                ((seed ushr 33) % 256).toFloat() - 127.5f
            }
        }

    private fun crop(
        x0: Int,
        y0: Int,
        w: Int,
        h: Int,
    ) = FloatArray(w * h) { world[(y0 + it / w) * worldW + x0 + it % w] }

    @Test
    fun recoversIntegerShiftWithStrongResponse() {
        // b is a sampled 5 px right and 3 px up: its content moved by (-5, +3)
        val r = PhaseCorrelation.correlate(crop(10, 10, 128, 96), crop(15, 7, 128, 96), 128, 96)
        assertEquals(-5.0, r.fx, 0.1)
        assertEquals(3.0, r.fy, 0.1)
        assertTrue("response ${r.response}", r.response >= 0.8)
    }

    @Test
    fun unrelatedNoiseHasLowResponse() {
        val r = PhaseCorrelation.correlate(crop(0, 0, 128, 96), crop(200, 100, 128, 96), 128, 96)
        assertTrue("response ${r.response}", r.response < 0.2)
    }

    @Test
    fun sizeThatIsNotAPowerOfTwo() {
        // 360x164 pads to 360x180, as OpenCV does
        val r = PhaseCorrelation.correlate(crop(0, 0, 360, 164), crop(0, 7, 360, 164), 360, 164)
        assertEquals(0.0, r.fx, 0.1)
        assertEquals(-7.0, r.fy, 0.1)
    }

    @Test
    fun knownShiftRecoveredAtOddPaddedHeight() {
        // 135 rows is its own padded size; the cyclic shift uses the integer centre, which differs from OpenCV's fftShift here
        val r = PhaseCorrelation.correlate(crop(10, 10, 360, 135), crop(7, 8, 360, 135), 360, 135)
        assertEquals(3.0, r.fx, 0.05)
        assertEquals(2.0, r.fy, 0.05)
    }
}
