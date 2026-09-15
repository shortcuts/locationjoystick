package com.locationjoystick.core.designsystem.component

import com.locationjoystick.core.common.constants.AppConstants
import org.junit.Assert.assertEquals
import org.junit.Test

class StartToggleLabelTest {
    @Test
    fun `stop ignores the location label`() {
        assertEquals("Stop", startToggleLabel(isSpoofing = true, locationLabel = "Very Long Route Name"))
    }

    @Test
    fun `start with no location`() {
        assertEquals("Start", startToggleLabel(isSpoofing = false, locationLabel = null))
        assertEquals("Start", startToggleLabel(isSpoofing = false, locationLabel = "  "))
    }

    @Test
    fun `start with a short location keeps the full name`() {
        assertEquals("Start · Paris, France", startToggleLabel(isSpoofing = false, locationLabel = "Paris, France"))
    }

    @Test
    fun `start with a long location drops characters after the limit`() {
        val longName = "Llanfairpwllgwyngyll, United Kingdom"
        val actual = startToggleLabel(isSpoofing = false, locationLabel = longName, maxLocationChars = 18)
        assertEquals("Start · Llanfairpwllgwyngy", actual)
        assertEquals(18, actual.removePrefix("Start · ").length)
    }

    @Test
    fun `default limit matches TopBarConstants`() {
        val longName = "a".repeat(50)
        val actual = startToggleLabel(isSpoofing = false, locationLabel = longName)
        assertEquals(
            AppConstants.TopBarConstants.LOCATION_LABEL_MAX_CHARS,
            actual.removePrefix("Start · ").length,
        )
    }
}
