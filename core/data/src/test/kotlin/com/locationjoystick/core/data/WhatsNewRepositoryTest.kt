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

class WhatsNewRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: WhatsNewRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = WhatsNewRepository()
        repository.client = OkHttpClient.Builder().build()
        repository.baseUrl = server.url("/changelog/").toString()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetchEntries parses entries array on success`() =
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"version":"1.0.0","date":"2026-01-01","entries":[
                        {"category":"feat","scope":"General","summary":"Did a thing."}
                    ]}""",
                ),
            )
            val result = repository.fetchEntries("1.0.0")
            assertEquals(1, result!!.size)
            assertEquals(WhatsNewEntry("feat", "General", "Did a thing."), result[0])
        }

    @Test
    fun `fetchEntries returns null on non-200`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(500))
            val result = repository.fetchEntries("1.0.0")
            assertNull(result)
        }

    @Test
    fun `fetchEntries returns null on malformed json`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))
            val result = repository.fetchEntries("1.0.0")
            assertNull(result)
        }

    @Test
    fun `fetchEntries returns null when server unreachable`() =
        runTest {
            server.shutdown()
            val result = repository.fetchEntries("1.0.0")
            assertNull(result)
        }
}
