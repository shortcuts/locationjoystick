package com.locationjoystick.core.common.constants

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConstantsTest {
    @Test
    fun `DEFAULT_REPLAY_SPEED_MS equals WALK_SPEED_MPS`() {
        assertEquals(
            AppConstants.ProfileConstants.WALK_SPEED_MPS,
            AppConstants.LocationConstants.DEFAULT_REPLAY_SPEED_MS,
            0.0001,
        )
    }

    @Test
    fun `DEFAULT_REPLAY_SPEED_MS is within valid speed range`() {
        assertTrue(
            AppConstants.LocationConstants.DEFAULT_REPLAY_SPEED_MS >= AppConstants.ProfileConstants.MIN_SPEED_MS,
        )
        assertTrue(
            AppConstants.LocationConstants.DEFAULT_REPLAY_SPEED_MS <= AppConstants.ProfileConstants.MAX_SPEED_MS,
        )
    }

    @Test
    fun `WALK_SPEED_MPS is approximately 2 kmh`() {
        assertEquals(2.0 / 3.6, AppConstants.ProfileConstants.WALK_SPEED_MPS, 0.001)
    }
}
