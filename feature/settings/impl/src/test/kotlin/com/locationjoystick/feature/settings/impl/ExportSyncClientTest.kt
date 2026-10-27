package com.locationjoystick.feature.settings.impl

import com.locationjoystick.core.common.constants.AppConstants
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExportSyncClientTest {
    private val client = ExportSyncClient()

    private fun fetchBody(body: String): String {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(body))
        server.start()
        try {
            return runBlocking { client.fetch(server.hostName, server.port, "t") }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `body within cap is read`() {
        assertEquals("{\"a\":1}", fetchBody("{\"a\":1}"))
    }

    @Test
    fun `body above cap throws`() {
        val oversized = "x".repeat((AppConstants.ExportConstants.MAX_IMPORT_SIZE_BYTES + 1).toInt())
        assertThrows(IllegalArgumentException::class.java) { fetchBody(oversized) }
    }
}
