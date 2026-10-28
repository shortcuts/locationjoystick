package com.locationjoystick.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenFeatureGatesTest {
    private val sdk = ScreenFeatureGates.MIN_SDK

    @Test
    fun `active when enabled, accepted, path shows it and sdk at the floor`() {
        for (path in listOf(GuidedPath.DEFAULT, GuidedPath.POKEMON_GO)) {
            assertTrue(ScreenFeatureGates.tapToWalkActive(true, true, path, sdk))
            assertTrue(ScreenFeatureGates.reconnectActive(true, true, path, sdk))
        }
    }

    @Test
    fun `pikmin bloom is never active`() {
        assertFalse(ScreenFeatureGates.tapToWalkActive(true, true, GuidedPath.PIKMIN_BLOOM, sdk))
        assertFalse(ScreenFeatureGates.reconnectActive(true, true, GuidedPath.PIKMIN_BLOOM, sdk))
    }

    @Test
    fun `each missing input closes the gate`() {
        assertFalse(ScreenFeatureGates.tapToWalkActive(false, true, GuidedPath.DEFAULT, sdk))
        assertFalse(ScreenFeatureGates.reconnectActive(false, true, GuidedPath.DEFAULT, sdk))
        assertFalse(ScreenFeatureGates.tapToWalkActive(true, false, GuidedPath.DEFAULT, sdk))
        assertFalse(ScreenFeatureGates.reconnectActive(true, false, GuidedPath.DEFAULT, sdk))
        assertFalse(ScreenFeatureGates.tapToWalkActive(true, true, GuidedPath.DEFAULT, sdk - 1))
        assertFalse(ScreenFeatureGates.reconnectActive(true, true, GuidedPath.DEFAULT, sdk - 1))
    }

    @Test
    fun `shown ignores disclosure and sdk`() {
        assertTrue(ScreenFeatureGates.tapToWalkShown(true, GuidedPath.POKEMON_GO))
        assertTrue(ScreenFeatureGates.reconnectShown(true, GuidedPath.DEFAULT))
        assertFalse(ScreenFeatureGates.tapToWalkShown(false, GuidedPath.DEFAULT))
        assertFalse(ScreenFeatureGates.reconnectShown(false, GuidedPath.DEFAULT))
        assertFalse(ScreenFeatureGates.tapToWalkShown(true, GuidedPath.PIKMIN_BLOOM))
        assertFalse(ScreenFeatureGates.reconnectShown(true, GuidedPath.PIKMIN_BLOOM))
    }
}
