package com.locationjoystick.core.common.util

import com.locationjoystick.core.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureLinkTest {
    private val mushroom = LatLng(36.977695, 128.363905)
    private val flower = LatLng(36.982194, 128.370129)

    @Test
    fun `capture when enabled and coords present`() {
        assertEquals(CaptureLinkDecision.CAPTURE, decideCaptureLink(true, true, false, mushroom.latitude to mushroom.longitude))
    }

    @Test
    fun `forward when enabled but no coords`() {
        assertEquals(CaptureLinkDecision.FORWARD, decideCaptureLink(true, true, false, null))
    }

    @Test
    fun `forward when both actions are off`() {
        assertEquals(
            CaptureLinkDecision.FORWARD,
            decideCaptureLink(true, false, false, mushroom.latitude to mushroom.longitude),
        )
    }

    @Test
    fun `capture enabled with coords`() {
        assertEquals(
            CaptureLinkDecision.CAPTURE,
            decideCaptureLink(true, true, false, mushroom.latitude to mushroom.longitude),
        )
    }

    @Test
    fun `jump without capture teleports only`() {
        assertEquals(
            CaptureLinkDecision.JUMP,
            decideCaptureLink(true, false, true, mushroom.latitude to mushroom.longitude),
        )
    }

    @Test
    fun `capture and jump performs both`() {
        assertEquals(
            CaptureLinkDecision.CAPTURE_AND_JUMP,
            decideCaptureLink(true, true, true, mushroom.latitude to mushroom.longitude),
        )
    }

    @Test
    fun `mode off passes coordinate links through even when app is not default browser`() {
        assertEquals(
            CaptureLinkDecision.FORWARD,
            decideCaptureLink(false, true, true, mushroom.latitude to mushroom.longitude),
        )
    }

    @Test
    fun `recognizes only Google Maps web links`() {
        assertEquals(true, isGoogleMapsWebLink("https://maps.app.goo.gl/abc"))
        assertEquals(true, isGoogleMapsWebLink("https://www.google.com/maps/place/test"))
        assertEquals(false, isGoogleMapsWebLink("https://www.google.com/search?q=maps"))
        assertEquals(false, isGoogleMapsWebLink("https://example.com/maps"))
    }

    @Test
    fun `append to empty list`() {
        assertEquals(listOf(mushroom), appendCapturedPoint(emptyList(), mushroom))
    }

    @Test
    fun `append different point after last`() {
        assertEquals(listOf(mushroom, flower), appendCapturedPoint(listOf(mushroom), flower))
    }

    @Test
    fun `skip exact duplicate of last point`() {
        assertEquals(listOf(mushroom), appendCapturedPoint(listOf(mushroom), mushroom))
    }

    @Test
    fun `duplicate of earlier point still appends`() {
        assertEquals(listOf(mushroom, flower, mushroom), appendCapturedPoint(listOf(mushroom, flower), mushroom))
    }

    @Test
    fun `preferred browser wins when not self`() {
        val picked =
            pickForwardBrowserPackage(
                candidates = listOf("com.locationjoystick.app", "com.android.chrome", "com.sec.android.app.sbrowser"),
                selfPackage = "com.locationjoystick.app",
                preferred = "com.android.chrome",
            )
        assertEquals("com.android.chrome", picked)
    }

    @Test
    fun `falls back to first other candidate when preferred missing`() {
        val picked =
            pickForwardBrowserPackage(
                candidates = listOf("com.locationjoystick.app", "com.sec.android.app.sbrowser"),
                selfPackage = "com.locationjoystick.app",
                preferred = "com.android.chrome",
            )
        assertEquals("com.sec.android.app.sbrowser", picked)
    }

    @Test
    fun `returns null when only self is a candidate`() {
        assertNull(
            pickForwardBrowserPackage(
                candidates = listOf("com.locationjoystick.app"),
                selfPackage = "com.locationjoystick.app",
                preferred = "com.android.chrome",
            ),
        )
    }

    @Test
    fun `saved package used when it is not self`() {
        assertEquals(
            "org.mozilla.firefox",
            resolvePreferredBrowserPackage(savedPackage = "org.mozilla.firefox", selfPackage = "com.locationjoystick.app"),
        )
    }

    @Test
    fun `chrome used when saved package is self or blank`() {
        assertEquals(
            CaptureBrowserPackages.CHROME,
            resolvePreferredBrowserPackage(savedPackage = "com.locationjoystick.app", selfPackage = "com.locationjoystick.app"),
        )
        assertEquals(
            CaptureBrowserPackages.CHROME,
            resolvePreferredBrowserPackage(savedPackage = null, selfPackage = "com.locationjoystick.app"),
        )
        assertEquals(
            CaptureBrowserPackages.CHROME,
            resolvePreferredBrowserPackage(savedPackage = "  ", selfPackage = "com.locationjoystick.app"),
        )
    }

    @Test
    fun `clipboard text is one lat lon pair per line`() {
        assertEquals("", formatCapturedPointsForClipboard(emptyList()))
        assertEquals("36.977695, 128.363905", formatCapturedPointsForClipboard(listOf(mushroom)))
        assertEquals(
            "36.977695, 128.363905\n36.982194, 128.370129",
            formatCapturedPointsForClipboard(listOf(mushroom, flower)),
        )
    }

    @Test
    fun `orderedCapturedPoints keeps capture order unless proximity is on`() {
        val far = LatLng(1.0, 1.0)
        val start = LatLng(0.0, 0.0)
        val near = LatLng(0.05, 0.05)
        val captured = listOf(far, start, near)
        assertEquals(captured, orderedCapturedPoints(captured, optimizeProximity = false))
        assertEquals(listOf(start, near, far), orderedCapturedPoints(captured, optimizeProximity = true))
    }
}
