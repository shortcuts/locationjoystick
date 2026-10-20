package com.locationjoystick.core.data

import com.locationjoystick.core.common.constants.AppConstants
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HotLocationsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var cache: File

    private fun body(vararg names: String) =
        """{"schema":1,"locations":[${names.joinToString(",") { """{"name":"$it","lat":1.0,"lon":2.0,"country":"C","city":"X"}""" }}]}"""

    private fun repo(): HotLocationsRepository =
        HotLocationsRepository(cache) { body("Seed") }.also { it.url = server.url("/hot/locations.json").toString() }

    private suspend fun loaded(): HotLocationsRepository = repo().also { it.refreshIfStale() }

    private fun names(r: HotLocationsRepository) = r.locations.value.map { it.name }

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        cache = File(tmp.root, "hot_locations.json")
    }

    @After
    fun tearDown() = server.shutdown()

    private fun staleCache(json: String) {
        cache.writeText(json)
        cache.setLastModified(System.currentTimeMillis() - AppConstants.HotLocationsConstants.CHECK_INTERVAL_MS - 60_000)
    }

    @Test
    fun `seed used when no cache, valid cache beats seed, corrupt cache falls back to seed`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(503))
            assertEquals(listOf("Seed"), names(loaded()))
            cache.writeText(body("Cached"))
            assertEquals(listOf("Cached"), names(loaded()))
            cache.writeText("garbage")
            assertEquals(listOf("Seed"), names(loaded()))
        }

    @Test
    fun `valid fetch updates list and persists across instances`() =
        runTest {
            server.enqueue(MockResponse().setBody(body("Remote")))
            val r = repo()
            assertTrue(r.refreshIfStale())
            assertEquals(listOf("Remote"), names(r))
            assertEquals(listOf("Remote"), names(loaded()))
        }

    @Test
    fun `invalid responses leave list and cache unchanged`() =
        runTest {
            staleCache(body("Cached"))
            listOf(
                MockResponse().setResponseCode(500),
                MockResponse().setBody("not json"),
                MockResponse().setBody(body("Bad").replace("\"schema\":1", "\"schema\":2")),
                MockResponse().setBody("""{"schema":1,"locations":[]}"""),
            ).forEach { resp ->
                staleCache(body("Cached"))
                server.enqueue(resp)
                val r = repo()
                assertFalse(r.refreshIfStale())
                assertEquals(listOf("Cached"), names(r))
                assertEquals(body("Cached"), cache.readText())
            }
        }

    @Test
    fun `fresh cache makes no request, stale does, failure on stale cache waits 24h`() =
        runTest {
            cache.writeText(body("Cached"))
            assertFalse(repo().refreshIfStale())
            assertEquals(0, server.requestCount)

            staleCache(body("Cached"))
            server.enqueue(MockResponse().setResponseCode(503))
            val r = repo()
            assertFalse(r.refreshIfStale())
            assertFalse(r.refreshIfStale())
            assertEquals(1, server.requestCount)
        }
}
