package com.locationjoystick.app

import android.content.Intent
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkParserTest {
    private fun intent(
        lat: String?,
        lon: String?,
        scheme: String = "https",
    ): Intent {
        val uri = mockk<Uri>()
        every { uri.getQueryParameter("lat") } returns lat
        every { uri.getQueryParameter("lon") } returns lon
        val intent = mockk<Intent>()
        every { intent.data } returns uri
        return intent
    }

    @Test
    fun `https scheme with valid lat lon returns pair`() {
        val result = parseDeepLinkCoords(intent("35.62", "139.77"))
        assertEquals(35.62 to 139.77, result)
    }

    @Test
    fun `custom scheme with query params returns pair`() {
        val result = parseDeepLinkCoords(intent("-33.87", "151.21"))
        assertEquals(-33.87 to 151.21, result)
    }

    @Test
    fun `negative lat and lon both parsed correctly`() {
        val result = parseDeepLinkCoords(intent("-35.62", "-139.77"))
        assertEquals(-35.62 to -139.77, result)
    }

    @Test
    fun `lat out of bounds returns null`() {
        assertNull(parseDeepLinkCoords(intent("91.0", "0.0")))
        assertNull(parseDeepLinkCoords(intent("-91.0", "0.0")))
    }

    @Test
    fun `lon out of bounds returns null`() {
        assertNull(parseDeepLinkCoords(intent("0.0", "181.0")))
        assertNull(parseDeepLinkCoords(intent("0.0", "-181.0")))
    }

    @Test
    fun `missing lat returns null`() {
        assertNull(parseDeepLinkCoords(intent(null, "139.77")))
    }

    @Test
    fun `missing lon returns null`() {
        assertNull(parseDeepLinkCoords(intent("35.62", null)))
    }

    @Test
    fun `non-numeric lat returns null`() {
        assertNull(parseDeepLinkCoords(intent("abc", "139.77")))
    }

    @Test
    fun `no data uri returns null`() {
        val intent = mockk<Intent>()
        every { intent.data } returns null
        assertNull(parseDeepLinkCoords(intent))
    }

    @Test
    fun `boundary values are accepted`() {
        assertEquals(90.0 to 180.0, parseDeepLinkCoords(intent("90.0", "180.0")))
        assertEquals(-90.0 to -180.0, parseDeepLinkCoords(intent("-90.0", "-180.0")))
        assertEquals(0.0 to 0.0, parseDeepLinkCoords(intent("0.0", "0.0")))
    }
}
