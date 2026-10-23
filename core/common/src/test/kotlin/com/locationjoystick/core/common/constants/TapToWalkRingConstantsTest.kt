package com.locationjoystick.core.common.constants

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class TapToWalkRingConstantsTest {
    private val settings =
        JSONObject(
            javaClass
                .getResourceAsStream("/pokeminers/interaction_range_settings.json")!!
                .bufferedReader()
                .readText(),
        ).getJSONObject("settings")

    @Test
    fun `interaction ring radius matches the PokeMiners game master`() {
        assertEquals(
            settings.getDouble("interactionRangeMeters"),
            AppConstants.TapToWalkConstants.INTERACTION_RANGE_METERS,
            0.0,
        )
    }

    @Test
    fun `white pulse radius matches the PokeMiners game master`() {
        assertEquals(
            settings.getDouble("whitePulseRadiusMeters"),
            AppConstants.TapToWalkConstants.WHITE_PULSE_RADIUS_METERS,
            0.0,
        )
    }
}
