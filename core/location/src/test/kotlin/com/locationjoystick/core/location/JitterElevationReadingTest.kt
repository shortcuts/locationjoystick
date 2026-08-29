package com.locationjoystick.core.location

import com.locationjoystick.core.common.constants.AppConstants
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class JitterElevationReadingTest {
    @Test
    fun `offsets a round elevation reading off the exact integer`() {
        val result = jitterElevationReading(36.0, Random(seed = 1))
        assertTrue("Expected a non-round result, got $result", result != 36.0)
    }

    @Test
    fun `stays within the configured jitter radius`() {
        repeat(200) { seed ->
            val result = jitterElevationReading(100.0, Random(seed))
            val diff = kotlin.math.abs(result - 100.0)
            assertTrue(
                "Offset $diff exceeded jitter radius",
                diff <= AppConstants.RealismConstants.ELEVATION_FRACTIONAL_JITTER_METERS,
            )
        }
    }
}
