package com.locationjoystick.feature.map.impl

import com.locationjoystick.core.model.MockLocationState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapUiStateTest {
    @Test
    fun `showMapHint is false while idle when teleport is enabled`() {
        val state = MapUiState(mockLocationState = MockLocationState.IDLE, hideTeleportFeatures = false)

        assertFalse(state.showMapHint)
    }

    @Test
    fun `showMapHint is true while running when teleport is enabled`() {
        val state = MapUiState(mockLocationState = MockLocationState.RUNNING, hideTeleportFeatures = false)

        assertTrue(state.showMapHint)
    }

    @Test
    fun `showMapHint is true while idle when teleport is hidden`() {
        val state = MapUiState(mockLocationState = MockLocationState.IDLE, hideTeleportFeatures = true)

        assertTrue(state.showMapHint)
    }
}
