package com.locationjoystick.core.data

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ElevationRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: ElevationRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = ElevationRepository()
        repository.client = OkHttpClient.Builder().build()
        repository.baseUrl = server.url("/v1/elevation").toString()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetchElevationMeters parses elevation array on success`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"elevation":[38.0]}"""))
            val result = repository.fetchElevationMeters(52.52, 13.41)
            assertEquals(38.0, result!!, 0.0001)
        }

    @Test
    fun `fetchElevationMeters returns null on non-200`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(500))
            val result = repository.fetchElevationMeters(52.52, 13.41)
            assertNull(result)
        }

    @Test
    fun `fetchElevationMeters returns null on malformed json`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))
            val result = repository.fetchElevationMeters(52.52, 13.41)
            assertNull(result)
        }

    @Test
    fun `fetchElevationMeters returns null when server unreachable`() =
        runTest {
            server.shutdown()
            val result = repository.fetchElevationMeters(52.52, 13.41)
            assertNull(result)
        }

    @Test
    fun `nearby lookups in the same cell make one request`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"elevation":[38.0]}"""))
            repository.fetchElevationMeters(52.5201, 13.4101)
            val second = repository.fetchElevationMeters(52.5204, 13.4104)
            assertEquals(38.0, second!!, 0.0001)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `failed lookup is not cached`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(500))
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"elevation":[38.0]}"""))
            assertNull(repository.fetchElevationMeters(52.52, 13.41))
            assertEquals(38.0, repository.fetchElevationMeters(52.52, 13.41)!!, 0.0001)
            assertEquals(2, server.requestCount)
        }
}
