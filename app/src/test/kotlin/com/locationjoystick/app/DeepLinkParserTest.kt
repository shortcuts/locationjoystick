package com.locationjoystick.app

import android.content.Intent
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkParserTest {
    private fun queryParamIntent(
        lat: String?,
        lon: String?,
    ): Intent {
        val uri = mockk<Uri>()
        every { uri.scheme } returns "https"
        every { uri.host } returns null
        every { uri.getQueryParameter("lat") } returns lat
        every { uri.getQueryParameter("lon") } returns lon
        val intent = mockk<Intent>()
        every { intent.data } returns uri
        return intent
    }

    private fun geoUri(schemeSpecificPart: String): Intent {
        val uri = mockk<Uri>()
        every { uri.scheme } returns "geo"
        every { uri.schemeSpecificPart } returns schemeSpecificPart
        val intent = mockk<Intent>()
        every { intent.data } returns uri
        return intent
    }

    private fun navigationUri(schemeSpecificPart: String): Intent {
        val uri = mockk<Uri>()
        every { uri.scheme } returns "google.navigation"
        every { uri.schemeSpecificPart } returns schemeSpecificPart
        val intent = mockk<Intent>()
        every { intent.data } returns uri
        return intent
    }

    private fun googleMapsUri(
        host: String,
        path: String? = null,
        pathSegments: List<String> = emptyList(),
        queryParams: Map<String, String?> = emptyMap(),
    ): Intent {
        val uri = mockk<Uri>()
        every { uri.scheme } returns "https"
        every { uri.host } returns host
        every { uri.path } returns path
        every { uri.pathSegments } returns pathSegments
        every { uri.getQueryParameter(any()) } answers { queryParams[it.invocation.args[0]] }
        val intent = mockk<Intent>()
        every { intent.data } returns uri
        return intent
    }

    @Test
    fun `https scheme with valid lat lon returns pair`() {
        val result = parseDeepLinkCoords(queryParamIntent("35.62", "139.77"))
        assertEquals(35.62 to 139.77, result)
    }

    @Test
    fun `custom scheme with query params returns pair`() {
        val result = parseDeepLinkCoords(queryParamIntent("-33.87", "151.21"))
        assertEquals(-33.87 to 151.21, result)
    }

    @Test
    fun `negative lat and lon both parsed correctly`() {
        val result = parseDeepLinkCoords(queryParamIntent("-35.62", "-139.77"))
        assertEquals(-35.62 to -139.77, result)
    }

    @Test
    fun `lat out of bounds returns null`() {
        assertNull(parseDeepLinkCoords(queryParamIntent("91.0", "0.0")))
        assertNull(parseDeepLinkCoords(queryParamIntent("-91.0", "0.0")))
    }

    @Test
    fun `lon out of bounds returns null`() {
        assertNull(parseDeepLinkCoords(queryParamIntent("0.0", "181.0")))
        assertNull(parseDeepLinkCoords(queryParamIntent("0.0", "-181.0")))
    }

    @Test
    fun `missing lat returns null`() {
        assertNull(parseDeepLinkCoords(queryParamIntent(null, "139.77")))
    }

    @Test
    fun `missing lon returns null`() {
        assertNull(parseDeepLinkCoords(queryParamIntent("35.62", null)))
    }

    @Test
    fun `non-numeric lat returns null`() {
        assertNull(parseDeepLinkCoords(queryParamIntent("abc", "139.77")))
    }

    @Test
    fun `no data uri returns null`() {
        val intent = mockk<Intent>()
        every { intent.data } returns null
        assertNull(parseDeepLinkCoords(intent))
    }

    @Test
    fun `boundary values are accepted`() {
        assertEquals(90.0 to 180.0, parseDeepLinkCoords(queryParamIntent("90.0", "180.0")))
        assertEquals(-90.0 to -180.0, parseDeepLinkCoords(queryParamIntent("-90.0", "-180.0")))
        assertEquals(0.0 to 0.0, parseDeepLinkCoords(queryParamIntent("0.0", "0.0")))
    }

    @Test
    fun `geo uri with simple lat lon returns pair`() {
        val result = parseDeepLinkCoords(geoUri("35.62,139.77"))
        assertEquals(35.62 to 139.77, result)
    }

    @Test
    fun `geo uri with negative coords returns pair`() {
        val result = parseDeepLinkCoords(geoUri("-33.87,151.21"))
        assertEquals(-33.87 to 151.21, result)
    }

    @Test
    fun `geo uri with optional parameters ignores them`() {
        val result = parseDeepLinkCoords(geoUri("35.62,139.77?z=10"))
        assertEquals(35.62 to 139.77, result)
    }

    @Test
    fun `geo uri with out of bounds coords returns null`() {
        assertNull(parseDeepLinkCoords(geoUri("91.0,0.0")))
        assertNull(parseDeepLinkCoords(geoUri("0.0,181.0")))
    }

    @Test
    fun `geo uri with invalid format returns null`() {
        assertNull(parseDeepLinkCoords(geoUri("35.62")))
        assertNull(parseDeepLinkCoords(geoUri("abc,def")))
    }

    @Test
    fun `geo uri with placeholder base and q param prefers q coords`() {
        val result = parseDeepLinkCoords(geoUri("0,0?q=35.62,139.77(Landmark)"))
        assertEquals(35.62 to 139.77, result)
    }

    @Test
    fun `geo uri with q param missing label still parses`() {
        val result = parseDeepLinkCoords(geoUri("0,0?q=-33.87,151.21"))
        assertEquals(-33.87 to 151.21, result)
    }

    @Test
    fun `google navigation scheme returns pair`() {
        val result = parseDeepLinkCoords(navigationUri("q=35.62,139.77"))
        assertEquals(35.62 to 139.77, result)
    }

    @Test
    fun `maps google com with q param returns pair`() {
        val intent =
            googleMapsUri(
                host = "maps.google.com",
                path = "/maps",
                queryParams = mapOf("q" to "35.62,139.77"),
            )
        assertEquals(35.62 to 139.77, parseDeepLinkCoords(intent))
    }

    @Test
    fun `www google com maps with query param returns pair`() {
        val intent =
            googleMapsUri(
                host = "www.google.com",
                path = "/maps/search/",
                queryParams = mapOf("query" to "35.62,139.77"),
            )
        assertEquals(35.62 to 139.77, parseDeepLinkCoords(intent))
    }

    @Test
    fun `www google com non-maps path is not treated as maps link`() {
        val intent =
            googleMapsUri(
                host = "www.google.com",
                path = "/search",
                queryParams = mapOf("q" to "35.62,139.77"),
            )
        assertNull(parseDeepLinkCoords(intent))
    }

    @Test
    fun `google maps at-segment path returns pair`() {
        val intent =
            googleMapsUri(
                host = "www.google.com",
                path = "/maps/@35.62,139.77,15z",
                pathSegments = listOf("maps", "@35.62,139.77,15z"),
            )
        assertEquals(35.62 to 139.77, parseDeepLinkCoords(intent))
    }
}
