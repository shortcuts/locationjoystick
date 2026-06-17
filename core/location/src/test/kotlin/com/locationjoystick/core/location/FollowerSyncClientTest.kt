package com.locationjoystick.core.location

import com.locationjoystick.core.model.SyncPositionUpdate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

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

        client.startPolling("127.0.0.1", serverPort, "test-group") { lat, lon ->
            results.offer(Pair(lat, lon))
        }

        val result = results.poll(3, TimeUnit.SECONDS)
        assertNotNull("Callback should be invoked within timeout", result)
        assertEquals(1.0, result!!.first, 0.0)
        assertEquals(2.0, result.second, 0.0)
    }

    @Test
    fun `stale update is not delivered to callback`() {
        val staleUpdate = SyncPositionUpdate(
            timestamp = System.currentTimeMillis() - 10_000L,
            latitude = 9.0,
            longitude = 9.0,
            speedMs = 0f,
            bearing = 0f,
            seq = 0,
        )
        server.push(staleUpdate)
        val results = LinkedBlockingQueue<Pair<Double, Double>>()

        client.startPolling("127.0.0.1", serverPort, "test-group") { lat, lon ->
            results.offer(Pair(lat, lon))
        }

        val result = results.poll(600, TimeUnit.MILLISECONDS)
        assertNull("Stale update should not be delivered", result)
    }

    @Test
    fun `wrong token returns no position`() {
        server.push(freshUpdate(lat = 5.0, lon = 6.0))
        val results = LinkedBlockingQueue<Pair<Double, Double>>()

        // Intentionally wrong groupId — server returns 403, client skips
        client.startPolling("127.0.0.1", serverPort, "wrong-group") { lat, lon ->
            results.offer(Pair(lat, lon))
        }

        val result = results.poll(600, TimeUnit.MILLISECONDS)
        assertNull("403 response should not deliver position", result)
    }

    @Test
    fun `duplicate seq is not delivered twice`() {
        server.push(freshUpdate(lat = 3.0, lon = 4.0))
        val results = LinkedBlockingQueue<Pair<Double, Double>>()

        client.startPolling("127.0.0.1", serverPort, "test-group", pollIntervalMs = 50) { lat, lon ->
            results.offer(Pair(lat, lon))
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

        client.startPolling("127.0.0.1", serverPort, "test-group", pollIntervalMs = 50) { lat, lon ->
            results.offer(Pair(lat, lon))
        }

        val first = results.poll(3, TimeUnit.SECONDS)
        assertNotNull(first)

        // Push a new position — different seq
        server.push(freshUpdate(lat = 10.0, lon = 20.0))

        val second = results.poll(3, TimeUnit.SECONDS)
        assertNotNull("New seq should be delivered", second)
        assertEquals(10.0, second!!.first, 0.0)
    }
}
