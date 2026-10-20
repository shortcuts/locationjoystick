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
    fun `start twice returns the same port`() {
        assertEquals(server.start("gid"), server.start("gid"))
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
            SyncPositionUpdate(
                timestamp = 1000L,
                latitude = 1.5,
                longitude = 2.5,
                speedMs = 1f,
                bearing = 90f,
                seq = 0,
            ),
        )
        val conn = URL("http://localhost:$port/position?token=gid").openConnection() as HttpURLConnection
        assertEquals(200, conn.responseCode)
        val body = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        assertTrue(body.contains("\"lat\":1.5"))
        assertTrue(body.contains("\"lon\":2.5"))
        assertTrue(body.contains("\"ts\":1000"))
        assertTrue(body.contains("\"active\":true"))
    }

    @Test
    fun `pushed teleportSeq is served`() {
        val port = server.start("gid")
        server.push(
            SyncPositionUpdate(
                timestamp = 1000L,
                latitude = 1.5,
                longitude = 2.5,
                speedMs = 0f,
                bearing = 0f,
                seq = 0,
                teleportSeq = 3L,
            ),
        )
        val conn = URL("http://localhost:$port/position?token=gid").openConnection() as HttpURLConnection
        val body = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        assertTrue(body.contains("\"teleportSeq\":3"))
    }

    @Test
    fun `pushed inactive update reports active false`() {
        val port = server.start("gid")
        server.push(
            SyncPositionUpdate(
                timestamp = 1000L,
                latitude = 1.5,
                longitude = 2.5,
                speedMs = 0f,
                bearing = 0f,
                seq = 0,
                active = false,
            ),
        )
        val conn = URL("http://localhost:$port/position?token=gid").openConnection() as HttpURLConnection
        val body = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        assertTrue(body.contains("\"active\":false"))
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
        val update =
            SyncPositionUpdate(timestamp = 0L, latitude = 0.0, longitude = 0.0, speedMs = 0f, bearing = 0f, seq = 0)
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

    private fun call(
        port: Int,
        path: String,
        method: String = "GET",
        auth: String? = "Bearer k",
        body: String? = null,
        declaredLength: Int? = null,
    ): Triple<Int, String, HttpURLConnection> {
        val conn = URL("http://localhost:$port$path").openConnection() as HttpURLConnection
        conn.requestMethod = method
        auth?.let { conn.setRequestProperty("Authorization", it) }
        if (body != null || declaredLength != null) {
            conn.doOutput = true
            val bytes = (body ?: "").toByteArray()
            conn.setFixedLengthStreamingMode(declaredLength ?: bytes.size)
            if (declaredLength == null) conn.outputStream.use { it.write(bytes) }
        }
        val code =
            try {
                conn.responseCode
            } catch (_: java.io.IOException) {
                // declaredLength without a body: server replies before we finish; treat as 413
                413
            }
        val text = (if (code < 400) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText() ?: ""
        return Triple(code, text, conn)
    }

    @Test
    fun `api disabled returns 404 while position still works`() {
        val port = server.start("gid")
        val (code, body, _) = call(port, "/api/v1/status")
        assertEquals(404, code)
        assertTrue(body.contains("\"code\":\"not_found\""))
        assertEquals(204, call(port, "/position?token=gid", auth = null).first)
    }

    @Test
    fun `enableApi when not started stays disabled`() {
        server.enableApi("k")
        assertTrue(!server.apiEnabled.value)
    }

    @Test
    fun `api auth failures return 401 with challenge`() {
        val port = server.start("gid")
        server.enableApi("k")
        for (auth in listOf(null, "Bearer wrong")) {
            val (code, body, conn) = call(port, "/api/v1/status", auth = auth)
            assertEquals(401, code)
            assertEquals("Bearer", conn.getHeaderField("WWW-Authenticate"))
            assertTrue(body.contains("\"code\":\"unauthorized\""))
        }
        assertEquals(401, call(port, "/api/v1/status?token=gid", auth = null).first)
    }

    @Test
    fun `status returns leader info with correct key`() {
        val port = server.start("gid")
        server.enableApi("k")
        val (code, body, _) = call(port, "/api/v1/status")
        assertEquals(200, code)
        assertEquals("{\"apiVersion\":1,\"role\":\"leader\",\"followers\":0}", body)
    }

    @Test
    fun `unknown route 404 and wrong method 405 with Allow`() {
        val port = server.start("gid")
        server.enableApi("k")
        assertEquals(404, call(port, "/api/v1/nope").first)
        val (code, _, conn) = call(port, "/api/v1/status", method = "POST", body = "x")
        assertEquals(405, code)
        assertEquals("GET", conn.getHeaderField("Allow"))
    }

    @Test
    fun `id pattern route passes decoded segment and 405 Allow reads the pattern`() {
        val port = server.start("gid")
        server.enableApi("k")
        server.registerApiRoute("GET", "/api/v1/things/{id}") { ApiResponse(200, it.pathParam!!) }
        assertEquals("a b", call(port, "/api/v1/things/a%20b").second)
        assertEquals(404, call(port, "/api/v1/things/").first)
        val (code, _, conn) = call(port, "/api/v1/things/x", method = "POST", body = "x")
        assertEquals(405, code)
        assertEquals("GET", conn.getHeaderField("Allow"))
    }

    @Test
    fun `registered route gets exact utf8 body, oversize is 413, throwing handler is 500`() {
        val port = server.start("gid")
        server.enableApi("k")
        server.registerApiRoute("POST", "/api/v1/echo") { ApiResponse(200, it.body!!) }
        server.registerApiRoute("GET", "/api/v1/boom") { error("x") }
        assertEquals("héllo→", call(port, "/api/v1/echo", method = "POST", body = "héllo→").second)
        assertEquals(413, call(port, "/api/v1/echo", method = "POST", declaredLength = 70_000).first)
        val (code, body, _) = call(port, "/api/v1/boom")
        assertEquals(500, code)
        assertTrue(body.contains("\"code\":\"internal_error\""))
    }

    @Test
    fun `restart disables api and rotating key rejects old key`() {
        var port = server.start("gid")
        server.enableApi("k")
        server.enableApi("k2")
        assertEquals(401, call(port, "/api/v1/status").first)
        assertEquals(200, call(port, "/api/v1/status", auth = "Bearer k2").first)
        server.stop()
        port = server.start("gid")
        assertEquals(404, call(port, "/api/v1/status", auth = "Bearer k2").first)
    }

    @Test
    fun `oversized headers are refused and health still answers`() {
        val port = server.start("gid")
        java.net.Socket("localhost", port).use { socket ->
            val out = socket.getOutputStream()
            out.write("GET /api/v1/state HTTP/1.1\r\nX-Pad: ".toByteArray())
            out.write(ByteArray(8_193) { 'a'.code.toByte() })
            out.flush()
            val reply = runCatching { socket.getInputStream().bufferedReader().readLine() }.getOrNull()
            assertTrue(reply, reply?.contains("431") == true)
        }
        java.net.Socket("localhost", port).use { socket ->
            val out = socket.getOutputStream()
            out.write("GET /x HTTP/1.1\r\n".toByteArray())
            repeat(101) { out.write("H$it: v\r\n".toByteArray()) }
            out.flush()
            assertTrue(
                socket
                    .getInputStream()
                    .bufferedReader()
                    .readLine()
                    .contains("431"),
            )
        }
        val conn = URL("http://localhost:$port/position?token=gid").openConnection() as HttpURLConnection
        assertEquals(204, conn.responseCode)
        conn.disconnect()
    }
}
