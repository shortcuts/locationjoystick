package com.locationjoystick.app

import android.content.Intent
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxOpenIntentsTest {
    private fun viewIntent(
        mime: String?,
        dataString: String?,
    ): Intent {
        val uri = dataString?.let { mockk<Uri>() }
        if (uri != null) {
            every { uri.toString() } returns dataString
            every { uri.lastPathSegment } returns dataString.substringAfterLast('/')
            every { uri.scheme } returns
                when {
                    dataString.startsWith("content:", ignoreCase = true) -> "content"
                    dataString.startsWith("file:", ignoreCase = true) -> "file"
                    dataString.startsWith("geo:", ignoreCase = true) -> "geo"
                    dataString.startsWith("https:", ignoreCase = true) -> "https"
                    dataString.startsWith("http:", ignoreCase = true) -> "http"
                    else -> null
                }
        }
        val intent = mockk<Intent>(relaxed = true)
        every { intent.action } returns Intent.ACTION_VIEW
        every { intent.type } returns mime
        every { intent.data } returns uri
        every { intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) } returns null
        return intent
    }

    @Test
    fun `VIEW with gpx mime is a gpx open`() {
        assertTrue(shouldHandleAsGpxOpen(viewIntent("application/gpx+xml", "content://downloads/document/1")))
    }

    @Test
    fun `VIEW with gpx path is a gpx open`() {
        assertTrue(
            shouldHandleAsGpxOpen(
                viewIntent(null, "content://com.android.providers.downloads.documents/document/walk.gpx"),
            ),
        )
    }

    @Test
    fun `SEND stream with gpx mime is a gpx open`() {
        val uri = mockk<Uri>()
        every { uri.toString() } returns "content://media/external/file/9"
        every { uri.lastPathSegment } returns "9"
        every { uri.scheme } returns "content"
        val intent = mockk<Intent>(relaxed = true)
        every { intent.action } returns Intent.ACTION_SEND
        every { intent.type } returns "application/gpx+xml"
        every { intent.data } returns null
        every { intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) } returns uri
        every { intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) } returns uri
        assertTrue(shouldHandleAsGpxOpen(intent))
        assertTrue(shouldTryOpenAsGpx(intent))
        assertEquals(uri, gpxUriFromIntent(intent))
    }

    @Test
    fun `geo VIEW is not a gpx open`() {
        assertFalse(shouldHandleAsGpxOpen(viewIntent(null, "geo:35.62,139.77")))
    }

    @Test
    fun `SEND text plain without stream is not a gpx open`() {
        val intent = mockk<Intent>(relaxed = true)
        every { intent.action } returns Intent.ACTION_SEND
        every { intent.type } returns "text/plain"
        every { intent.data } returns null
        every { intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) } returns null
        assertFalse(shouldHandleAsGpxOpen(intent))
        assertFalse(shouldTryOpenAsGpx(intent))
    }

    @Test
    fun `Generic octet-stream VIEW without gpx path still tries open`() {
        val intent =
            viewIntent(
                "application/octet-stream",
                "content://com.example.documents/items/27",
            )
        assertFalse(shouldHandleAsGpxOpen(intent))
        assertTrue(shouldTryOpenAsGpx(intent))
        assertTrue(shouldHandleAsGpxOpen(intent, "NB_flower_path.gpx"))
    }

    @Test
    fun `octet-stream content URI without gpx path still tries open`() {
        val intent = viewIntent("application/octet-stream", "content://downloads/document/123")
        assertFalse(shouldHandleAsGpxOpen(intent))
        assertTrue(shouldTryOpenAsGpx(intent))
    }

    @Test
    fun `geo VIEW is not tried as gpx`() {
        assertFalse(shouldTryOpenAsGpx(viewIntent(null, "geo:35.62,139.77")))
    }
}
