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

class UpdateCheckRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: UpdateCheckRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = UpdateCheckRepository()
        repository.client = OkHttpClient.Builder().build()
        repository.apiUrl = server.url("/releases/latest").toString()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `strips v prefix from tag_name`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"tag_name":"v0.22.0"}"""))
            assertEquals("0.22.0", repository.fetchLatestVersion())
        }

    @Test
    fun `returns null on non-200`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(403))
            assertNull(repository.fetchLatestVersion())
        }

    @Test
    fun `returns null on malformed json`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))
            assertNull(repository.fetchLatestVersion())
        }

    @Test
    fun `returns null when server unreachable`() =
        runTest {
            server.shutdown()
            assertNull(repository.fetchLatestVersion())
        }
}
