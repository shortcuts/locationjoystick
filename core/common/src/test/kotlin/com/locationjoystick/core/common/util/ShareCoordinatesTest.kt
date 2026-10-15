package com.locationjoystick.core.common.util

import android.content.Intent
import com.locationjoystick.core.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareCoordinatesTest {
    @Test
    fun `currentLocationShareText is null without a position`() {
        assertNull(currentLocationShareText(null))
    }

    @Test
    fun `currentLocationShareText uses six decimal degrees`() {
        assertEquals("11.012777, 79.480650", currentLocationShareText(LatLng(11.0127769, 79.48065)))
    }

    @Test
    fun `overlay share uses NEW_TASK so the chooser can start from the widget`() {
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, shareChooserFlags(fromActivity = false))
    }

    @Test
    fun `activity share does not force NEW_TASK`() {
        assertEquals(0, shareChooserFlags(fromActivity = true))
    }

    @Test
    fun `locationShareLink is the HTTPS deep link for the position`() {
        assertEquals("https://locationjoystick.shrtcts.fr/?lat=11.5&lon=79.25", locationShareLink(LatLng(11.5, 79.25)))
    }
}
