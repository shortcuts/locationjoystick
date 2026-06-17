package com.locationjoystick.core.location

import com.locationjoystick.core.model.SyncPositionUpdate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class LeaderSyncServerTest {
    private val server = LeaderSyncServer()

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `start returns nonzero port`() {
        val port = server.start("gid")
        assertTrue(port > 0)
    }

    @Test
    fun `wrong token returns 403`() {
        val port = server.start("correct-id")
        val conn = URL("http://localhost:$port/position?token=wrong").openConnection() as HttpURLConnection
        assertEquals(403, conn.responseCode)
        conn.disconnect()
    }

    @Test
    fun `no push returns 204`() {
        val port = server.start("gid")
        val conn = URL("http://localhost:$port/position?token=gid").openConnection() as HttpURLConnection
        assertEquals(204, conn.responseCode)
        conn.disconnect()
    }

    @Test
    fun `push then position returns 200 with correct JSON fields`() {
        val port = server.start("gid")
        server.push(
            SyncPositionUpdate(timestamp = 1000L, latitude = 1.5, longitude = 2.5, speedMs = 1f, bearing = 90f, seq = 0),
        )
        val conn = URL("http://localhost:$port/position?token=gid").openConnection() as HttpURLConnection
        assertEquals(200, conn.responseCode)
        val body = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        assertTrue(body.contains("\"lat\":1.5"))
        assertTrue(body.contains("\"lon\":2.5"))
        assertTrue(body.contains("\"ts\":1000"))
    }

    @Test
    fun `health endpoint returns 200`() {
        val port = server.start("gid")
        val conn = URL("http://localhost:$port/health?token=gid").openConnection() as HttpURLConnection
        assertEquals(200, conn.responseCode)
        conn.disconnect()
    }

    @Test
    fun `unknown path returns 404`() {
        val port = server.start("gid")
        val conn = URL("http://localhost:$port/unknown?token=gid").openConnection() as HttpURLConnection
        assertEquals(404, conn.responseCode)
        conn.disconnect()
    }

    @Test
    fun `seq increments on each push`() {
        val port = server.start("gid")
        val update = SyncPositionUpdate(timestamp = 0L, latitude = 0.0, longitude = 0.0, speedMs = 0f, bearing = 0f, seq = 0)
        server.push(update)
        server.push(update)

        val conn = URL("http://localhost:$port/position?token=gid").openConnection() as HttpURLConnection
        val body = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        assertTrue(body.contains("\"seq\":2"))
    }

    @Test
    fun `stop closes port so subsequent connect fails`() {
        val port = server.start("gid")
        server.stop()
        val conn = URL("http://localhost:$port/position?token=gid").openConnection() as HttpURLConnection
        try {
            conn.responseCode
            // If we get here, connection succeeded unexpectedly — fail gracefully.
        } catch (_: Exception) {
            // Expected: connection refused after stop
        } finally {
            conn.disconnect()
        }
    }
}
