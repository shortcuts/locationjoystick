package com.locationjoystick.core.common.util

import com.locationjoystick.core.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PastedCoordinatesTest {
    @Test
    fun `empty text returns no points`() {
        assertEquals(emptyList<LatLng>(), parsePastedCoordinates(""))
        assertEquals(emptyList<LatLng>(), parsePastedCoordinates("   \n\n  "))
    }

    @Test
    fun `plain lat lon pair is parsed`() {
        val points = parsePastedCoordinates("64.147609, -21.922327")
        assertEquals(1, points.size)
        assertEquals(64.147609, points[0].latitude, 1e-9)
        assertEquals(-21.922327, points[0].longitude, 1e-9)
    }

    @Test
    fun `emoji keycap and pin are stripped`() {
        val text =
            """
            1️⃣ 64.147609, -21.922327
            2. 📍 64.152840, -21.932219
            """.trimIndent()
        val points = parsePastedCoordinates(text)
        assertEquals(2, points.size)
        assertEquals(64.147609, points[0].latitude, 1e-9)
        assertEquals(-21.922327, points[0].longitude, 1e-9)
        assertEquals(64.152840, points[1].latitude, 1e-9)
        assertEquals(-21.932219, points[1].longitude, 1e-9)
    }

    @Test
    fun `typoed dots between lat and lon are fixed`() {
        val points = parsePastedCoordinates("23.627875.86.180014")
        assertEquals(1, points.size)
        assertEquals(23.627875, points[0].latitude, 1e-9)
        assertEquals(86.180014, points[0].longitude, 1e-9)
    }

    @Test
    fun `leading numbering is stripped`() {
        val text =
            """
            1. 51.5074, -0.1278
            23) 48.8566, 2.3522
            2- 40.7128, -74.0060
            """.trimIndent()
        val points = parsePastedCoordinates(text)
        assertEquals(3, points.size)
        assertEquals(51.5074, points[0].latitude, 1e-6)
        assertEquals(48.8566, points[1].latitude, 1e-6)
        assertEquals(40.7128, points[2].latitude, 1e-6)
    }

    @Test
    fun `out of range latitude is skipped`() {
        val points = parsePastedCoordinates("91.0, 0.0\n45.0, 10.0")
        assertEquals(listOf(LatLng(45.0, 10.0)), points)
    }

    @Test
    fun `swap lat lon reorders the pair`() {
        val points = parsePastedCoordinates("139.77, 35.68", swapLatLon = true)
        assertEquals(1, points.size)
        assertEquals(35.68, points[0].latitude, 1e-9)
        assertEquals(139.77, points[0].longitude, 1e-9)
    }

    @Test
    fun `swap is required when first number is a longitude`() {
        assertTrue(parsePastedCoordinates("139.77, 35.68").isEmpty())
        assertEquals(1, parsePastedCoordinates("139.77, 35.68", swapLatLon = true).size)
    }

    @Test
    fun `lines without two numbers are skipped`() {
        val points = parsePastedCoordinates("hello\n51.5, 0.0\nnot coords")
        assertEquals(listOf(LatLng(51.5, 0.0)), points)
    }

    @Test
    fun `prose lines with unrelated numbers are ignored`() {
        val points =
            parsePastedCoordinates(
                """
                route for some windflowers: ends in 1 hour
                batch 2 ends in 1 hour
                51.5, 0.0
                """.trimIndent(),
            )

        assertEquals(listOf(LatLng(51.5, 0.0)), points)
    }

    @Test
    fun `google maps dms pair is parsed`() {
        val points = parsePastedCoordinates("""37°34'11.4"N 127°00'17.9"E""")
        assertEquals(1, points.size)
        assertEquals(37.569833333, points[0].latitude, 1e-6)
        assertEquals(127.004972222, points[0].longitude, 1e-6)
    }

    @Test
    fun `dms with unicode primes and comma is parsed`() {
        val points = parsePastedCoordinates("37°34′11.4″N, 127°00′17.9″E")
        assertEquals(1, points.size)
        assertEquals(37.569833333, points[0].latitude, 1e-6)
        assertEquals(127.004972222, points[0].longitude, 1e-6)
    }

    @Test
    fun `dms hemisphere prefix and southern west are parsed`() {
        val points = parsePastedCoordinates("S33°52'07.7\" W151°12'33.5\"")
        assertEquals(1, points.size)
        assertEquals(-33.868805555, points[0].latitude, 1e-6)
        assertEquals(-151.209305555, points[0].longitude, 1e-6)
    }

    @Test
    fun `dms without seconds is parsed`() {
        val points = parsePastedCoordinates("37°34'N 127°00'E")
        assertEquals(1, points.size)
        assertEquals(37 + 34.0 / 60.0, points[0].latitude, 1e-9)
        assertEquals(127.0, points[0].longitude, 1e-9)
    }

    @Test
    fun `dms list becomes several route points`() {
        val text =
            """
            37°34'11.4"N 127°00'17.9"E
            51°30'26.0"N 0°07'40.0"W
            """.trimIndent()
        val points = parsePastedCoordinates(text)
        assertEquals(2, points.size)
        assertEquals(37.569833333, points[0].latitude, 1e-6)
        assertEquals(-0.127777777, points[1].longitude, 1e-6)
    }

    @Test
    fun `incomplete dms is not misread as the first two numbers`() {
        assertEquals(emptyList<LatLng>(), parsePastedCoordinates("""37°34'11.4"N"""))
    }

    @Test
    fun `dms hemispheres ignore swap lat lon`() {
        val points = parsePastedCoordinates("""37°34'11.4"N 127°00'17.9"E""", swapLatLon = true)
        assertEquals(1, points.size)
        assertEquals(37.569833333, points[0].latitude, 1e-6)
        assertEquals(127.004972222, points[0].longitude, 1e-6)
    }

    @Test
    fun `clipboard paste replaces a blank field`() {
        assertEquals("51.5, 0.0", mergeClipboardIntoPasteText(current = "", clipboard = "51.5, 0.0"))
        assertEquals("51.5, 0.0", mergeClipboardIntoPasteText(current = "  \n", clipboard = " 51.5, 0.0 "))
    }

    @Test
    fun `clipboard paste appends a second copy on a new line`() {
        assertEquals(
            "51.5, 0.0\n48.8, 2.3",
            mergeClipboardIntoPasteText(current = "51.5, 0.0", clipboard = "48.8, 2.3"),
        )
    }

    @Test
    fun `empty clipboard leaves existing paste text`() {
        assertEquals("51.5, 0.0", mergeClipboardIntoPasteText(current = "51.5, 0.0", clipboard = "  "))
    }
}
