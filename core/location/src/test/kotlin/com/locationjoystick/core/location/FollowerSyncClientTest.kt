package com.locationjoystick.core.location

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.SyncPositionUpdate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.PrintWriter
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class FollowerSyncClientTest {
    private val server = LeaderSyncServer()
    private val client = FollowerSyncClient()
    private var serverPort = 0

    @Before
    fun setUp() {
        serverPort = server.start("test-group")
    }

    @After
    fun tearDown() {
        client.stopPolling()
        server.stop()
    }

    private fun freshUpdate(
        lat: Double = 1.0,
        lon: Double = 2.0,
        seq: Long = 0,
    ) = SyncPositionUpdate(
        timestamp = System.currentTimeMillis(),
        latitude = lat,
        longitude = lon,
        speedMs = 1f,
        bearing = 0f,
        seq = seq,
    )

    @Test
    fun `successful poll delivers position to callback`() {
        server.push(freshUpdate(lat = 1.0, lon = 2.0))
        val results = LinkedBlockingQueue<Pair<Double, Double>>()

        client.startPolling("127.0.0.1", serverPort, "test-group") { update ->
            results.offer(Pair(update.lat, update.lon))
        }

        val result = results.poll(3, TimeUnit.SECONDS)
        assertNotNull("Callback should be invoked within timeout", result)
        assertEquals(1.0, result!!.first, 0.0)
        assertEquals(2.0, result.second, 0.0)
    }

    @Test
    fun `poll delivers speedMs and bearing to callback unchanged`() {
        server.push(freshUpdate(lat = 1.0, lon = 2.0).copy(speedMs = 2.5f, bearing = 90f))
        val results = LinkedBlockingQueue<Pair<Float, Float>>()

        client.startPolling("127.0.0.1", serverPort, "test-group") { update ->
            results.offer(Pair(update.speedMs, update.bearing))
        }

        val result = results.poll(3, TimeUnit.SECONDS)
        assertNotNull("Callback should be invoked within timeout", result)
        assertEquals(2.5f, result!!.first, 0f)
        assertEquals(90f, result.second, 0f)
    }

    @Test
    fun `inactive leader update delivers active false to callback`() {
        server.push(freshUpdate(lat = 1.0, lon = 2.0).copy(active = false))
        val results = LinkedBlockingQueue<Boolean>()

        client.startPolling("127.0.0.1", serverPort, "test-group") { update ->
            results.offer(update.active)
        }

        val result = results.poll(3, TimeUnit.SECONDS)
        assertNotNull("Callback should be invoked within timeout", result)
        assertFalse("Inactive leader update should deliver active=false", result!!)
    }

    @Test
    fun `stale update is not delivered to callback`() {
        val staleUpdate =
            SyncPositionUpdate(
                timestamp = System.currentTimeMillis() - 10_000L,
                latitude = 9.0,
                longitude = 9.0,
                speedMs = 0f,
                bearing = 0f,
                seq = 0,
            )
        server.push(staleUpdate)
        val results = LinkedBlockingQueue<Pair<Double, Double>>()

        client.startPolling("127.0.0.1", serverPort, "test-group") { update ->
            results.offer(Pair(update.lat, update.lon))
        }

        val result = results.poll(600, TimeUnit.MILLISECONDS)
        assertNull("Stale update should not be delivered", result)
    }

    @Test
    fun `wrong token returns no position`() {
        server.push(freshUpdate(lat = 5.0, lon = 6.0))
        val results = LinkedBlockingQueue<Pair<Double, Double>>()

        // Intentionally wrong groupId — server returns 403, client skips
        client.startPolling("127.0.0.1", serverPort, "wrong-group") { update ->
            results.offer(Pair(update.lat, update.lon))
        }

        val result = results.poll(600, TimeUnit.MILLISECONDS)
        assertNull("403 response should not deliver position", result)
    }

    @Test
    fun `duplicate seq is not delivered twice`() {
        server.push(freshUpdate(lat = 3.0, lon = 4.0))
        val results = LinkedBlockingQueue<Pair<Double, Double>>()

        client.startPolling("127.0.0.1", serverPort, "test-group", pollIntervalMs = 50) { update ->
            results.offer(Pair(update.lat, update.lon))
        }

        // Wait long enough for multiple polls — server still serves same seq
        Thread.sleep(300)
        client.stopPolling()

        assertEquals("Same seq should only be delivered once", 1, results.size)
    }

    @Test
    fun `new seq after update is delivered`() {
        server.push(freshUpdate(lat = 1.0, lon = 2.0))
        val results = LinkedBlockingQueue<Pair<Double, Double>>()

        client.startPolling("127.0.0.1", serverPort, "test-group", pollIntervalMs = 50) { update ->
            results.offer(Pair(update.lat, update.lon))
        }

        val first = results.poll(3, TimeUnit.SECONDS)
        assertNotNull(first)

        // Push a new position — different seq
        server.push(freshUpdate(lat = 10.0, lon = 20.0))

        val second = results.poll(3, TimeUnit.SECONDS)
        assertNotNull("New seq should be delivered", second)
        assertEquals(10.0, second!!.first, 0.0)
    }

    @Test
    fun `repeated non-403 error responses eventually trigger group-lost`() {
        val errorServer = AlwaysErrorServer()
        val errorPort = errorServer.start("test-group")
        val groupLost = LinkedBlockingQueue<Unit>()
        val pollIntervalMs = 20L

        try {
            client.startPolling(
                "127.0.0.1",
                errorPort,
                "test-group",
                pollIntervalMs = pollIntervalMs,
                onGroupLost = { groupLost.offer(Unit) },
            ) { }

            val timeoutMs =
                AppConstants.SyncConstants.MAX_CONSECUTIVE_POLL_FAILURES * (pollIntervalMs + 200) + 2_000
            val lost = groupLost.poll(timeoutMs, TimeUnit.MILLISECONDS)
            assertNotNull("500 responses should eventually surface as group-lost", lost)
        } finally {
            errorServer.stop()
        }
    }

    @Test
    fun `alternating success and failure never triggers group-lost`() {
        val alternatingServer = AlternatingServer()
        val altPort = alternatingServer.start("test-group")
        val groupLost = AtomicInteger(0)

        try {
            client.startPolling(
                "127.0.0.1",
                altPort,
                "test-group",
                pollIntervalMs = 20,
                onGroupLost = { groupLost.incrementAndGet() },
            ) { }

            Thread.sleep(1_000)
            assertTrue("Alternating success/failure should keep polling", client.isPolling)
            assertFalse("Reset-on-success must be preserved", groupLost.get() > 0)
        } finally {
            alternatingServer.stop()
        }
    }

    @Test
    fun `checkGroupExists returns true for valid group`() =
        runBlocking {
            server.push(freshUpdate())
            assertTrue(client.checkGroupExists("127.0.0.1", serverPort, "test-group"))
        }

    @Test
    fun `checkGroupExists returns false for 403 response`() =
        runBlocking {
            server.push(freshUpdate())
            assertFalse(client.checkGroupExists("127.0.0.1", serverPort, "wrong-group"))
        }

    @Test
    fun `checkGroupExists returns true on transient network error`() =
        runBlocking {
            val errorServer = AlwaysErrorServer()
            val errorPort = errorServer.start("test-group")
            try {
                assertTrue(client.checkGroupExists("127.0.0.1", errorPort, "test-group"))
            } finally {
                errorServer.stop()
            }
        }

    private class AlwaysErrorServer : TokenAuthHttpServer(TAG) {
        fun start(groupId: String): Int = startServer(groupId)

        fun stop() = stopServer()

        override fun handleRequest(
            path: String,
            socket: Socket,
            writer: PrintWriter,
        ) {
            writer.print("HTTP/1.1 500 Internal Server Error\r\nContent-Length: 0\r\n\r\n")
            writer.flush()
        }

        companion object {
            private const val TAG = "AlwaysErrorServer"
        }
    }

    private class AlternatingServer : TokenAuthHttpServer(TAG) {
        private val requestCount = AtomicInteger(0)

        fun start(groupId: String): Int = startServer(groupId)

        fun stop() = stopServer()

        override fun handleRequest(
            path: String,
            socket: Socket,
            writer: PrintWriter,
        ) {
            val n = requestCount.getAndIncrement()
            if (n % 2 == 0) {
                writer.print("HTTP/1.1 500 Internal Server Error\r\nContent-Length: 0\r\n\r\n")
            } else {
                writer.print("HTTP/1.1 204 No Content\r\nContent-Length: 0\r\n\r\n")
            }
            writer.flush()
        }

        companion object {
            private const val TAG = "AlternatingServer"
        }
    }
}
