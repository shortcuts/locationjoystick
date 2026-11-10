package com.locationjoystick.feature.settings.impl

import com.locationjoystick.core.model.GuidedPath
import com.locationjoystick.core.model.TapToWalkMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDisclosureGatesTest {
    @Test
    fun tapToWalkToggleEnablesWhenAccessibilityAccepted() {
        assertEquals(true, tapToWalkToggleResult(on = true, accessibilityAccepted = true))
        assertNull(tapToWalkToggleResult(on = true, accessibilityAccepted = false))
        assertEquals(false, tapToWalkToggleResult(on = false, accessibilityAccepted = true))
        assertEquals(false, tapToWalkToggleResult(on = false, accessibilityAccepted = false))
    }

    @Test
    fun fullDisclosureShownWheneverUnacceptedEvenIfServiceAlreadyGranted() {
        assertTrue(showsFullAccessibilityDisclosure(sdkInt = 30, accessibilityAccepted = false))
        assertFalse(showsFullAccessibilityDisclosure(sdkInt = 30, accessibilityAccepted = true))
        assertFalse(showsFullAccessibilityDisclosure(sdkInt = 29, accessibilityAccepted = false))
    }

    @Test
    fun screenAccessBlockShownWhenEitherFeatureOnFromApi30() {
        assertTrue(showsScreenAccessBlock(sdkInt = 30, tapToWalkOn = false, reconnectOn = true))
        assertTrue(showsScreenAccessBlock(sdkInt = 30, tapToWalkOn = true, reconnectOn = false))
        assertFalse(showsScreenAccessBlock(sdkInt = 30, tapToWalkOn = false, reconnectOn = false))
        assertFalse(showsScreenAccessBlock(sdkInt = 29, tapToWalkOn = true, reconnectOn = true))
    }

    @Test
    fun reconnectConsentPromptOnlyWhenReconnectOnWithoutConsent() {
        assertTrue(showsReconnectConsentPrompt(reconnectOn = true, accepted = false))
        assertFalse(showsReconnectConsentPrompt(reconnectOn = true, accepted = true))
        assertFalse(showsReconnectConsentPrompt(reconnectOn = false, accepted = false))
    }

    @Test
    fun serviceOffHintOnlyWhenFeatureOnAndServiceNotGranted() {
        assertTrue(showsServiceOffHint(featureOn = true, serviceGranted = false))
        assertFalse(showsServiceOffHint(featureOn = true, serviceGranted = true))
        assertFalse(showsServiceOffHint(featureOn = false, serviceGranted = false))
    }

    @Test
    fun onlyTheSoleCheckedVisibleMoveRowIsDisabled() {
        val walk = setOf(TapToWalkMove.WALK)
        assertFalse(tapToWalkRowEnabled(TapToWalkMove.WALK, walk, hideTeleport = false))
        assertTrue(tapToWalkRowEnabled(TapToWalkMove.VIA_ROADS, walk, hideTeleport = false))
        assertTrue(tapToWalkRowEnabled(TapToWalkMove.WALK, setOf(TapToWalkMove.WALK, TapToWalkMove.TELEPORT), hideTeleport = false))
        assertFalse(tapToWalkRowEnabled(TapToWalkMove.WALK, setOf(TapToWalkMove.WALK, TapToWalkMove.TELEPORT), hideTeleport = true))
        assertTrue(tapToWalkRowEnabled(TapToWalkMove.WALK, setOf(TapToWalkMove.TELEPORT), hideTeleport = true))
    }

    @Test
    fun screenHelpersCardHiddenWhenGuidedPathHidesBothFeatures() {
        assertTrue(showsScreenHelpersCard(GuidedPath.DEFAULT))
        assertTrue(showsScreenHelpersCard(GuidedPath.POKEMON_GO))
        assertFalse(showsScreenHelpersCard(GuidedPath.PIKMIN_BLOOM))
    }
}
