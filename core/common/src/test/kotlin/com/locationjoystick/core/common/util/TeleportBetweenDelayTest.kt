package com.locationjoystick.core.common.util

import com.locationjoystick.core.common.constants.AppConstants
import org.junit.Assert.assertEquals
import org.junit.Test

class TeleportBetweenDelayTest {
    @Test
    fun `zero seconds still waits one GPS tick`() {
        assertEquals(AppConstants.LocationConstants.UPDATE_INTERVAL_MS, hopLingerDurationMs(0))
    }

    @Test
    fun `default delay is eight seconds in milliseconds`() {
        assertEquals(
            8_000L,
            hopLingerDurationMs(AppConstants.RouteConstants.TELEPORT_BETWEEN_DEFAULT_DELAY_SECONDS),
        )
    }

    @Test
    fun `clamp rejects values above max`() {
        assertEquals(
            AppConstants.RouteConstants.TELEPORT_BETWEEN_MAX_DELAY_SECONDS,
            clampTeleportBetweenDelaySeconds(601),
        )
    }

    @Test
    fun `empty text uses the default delay`() {
        assertEquals(
            AppConstants.RouteConstants.TELEPORT_BETWEEN_DEFAULT_DELAY_SECONDS,
            parseTeleportBetweenDelaySeconds(""),
        )
    }

    @Test
    fun `non numeric text uses the default delay`() {
        assertEquals(
            AppConstants.RouteConstants.TELEPORT_BETWEEN_DEFAULT_DELAY_SECONDS,
            parseTeleportBetweenDelaySeconds("abc"),
        )
    }
}
