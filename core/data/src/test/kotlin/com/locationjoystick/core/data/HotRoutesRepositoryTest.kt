package com.locationjoystick.core.data

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.RouteType
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HotRoutesRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var cache: File

    private fun route(
        name: String,
        type: String = "STRAIGHT",
        pts: String = "[1.0,2.0],[3.0,4.0]",
    ) = """{"name":"$name","country":"C","city":"X","type":"$type","waypoints":[$pts]}"""

    private fun body(vararg names: String) = """{"schema":1,"routes":[${names.joinToString(",") { route(it) }}]}"""

    private fun repo() = HotRoutesRepository(cache) { body("Seed") }.also { it.url = server.url("/hot/routes.json").toString() }

    private suspend fun loaded(): HotRoutesRepository = repo().also { it.refreshIfStale() }

    private fun names(r: HotRoutesRepository) = r.routes.value.map { it.name }

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        cache = File(tmp.root, "hot_routes.json")
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `seed used when no cache`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(503))
            assertEquals(listOf("Seed"), names(loaded()))
        }

    @Test
    fun `valid fetch replaces list and persists`() =
        runTest {
            server.enqueue(MockResponse().setBody(body("Remote")))
            val r = repo()
            assertTrue(r.refreshIfStale())
            assertEquals(listOf("Remote"), names(r))
            assertEquals(listOf("Remote"), names(loaded()))
        }

    @Test
    fun `invalid bodies leave list and cache unchanged`() =
        runTest {
            listOf(
                MockResponse().setResponseCode(500),
                MockResponse().setBody("not json"),
                MockResponse().setBody(body("Bad").replace("\"schema\":1", "\"schema\":2")),
                MockResponse().setBody("""{"schema":1,"routes":[]}"""),
                MockResponse().setBody("""{"schema":1,"routes":[${route("One", pts = "[1.0,2.0]")}]}"""),
                MockResponse().setBody("""{"schema":1,"routes":[${route("Foo", type = "FOO")}]}"""),
                MockResponse().setBody("""{"schema":1,"routes":[${route("Tp", type = "TELEPORT")}]}"""),
            ).forEach { resp ->
                cache.writeText(body("Cached"))
                cache.setLastModified(System.currentTimeMillis() - AppConstants.HotLocationsConstants.CHECK_INTERVAL_MS - 60_000)
                server.enqueue(resp)
                val r = repo()
                assertFalse(r.refreshIfStale())
                assertEquals(listOf("Cached"), names(r))
                assertEquals(body("Cached"), cache.readText())
            }
        }

    @Test
    fun `parse keeps type and waypoints`() {
        val r = parseHotRoutes("""{"schema":1,"routes":[${route("G", type = "GUIDED")}]}""")!!.single()
        assertEquals(RouteType.GUIDED, r.routeType)
        assertEquals(2, r.waypoints.size)
        assertNull(parseHotRoutes("{}"))
    }

    @Test
    fun `bundled seed has the five original routes with stable ids`() {
        val seed = File("../../docs/wiki/hot/routes.json").readText()
        val routes = parseHotRoutes(seed)
        assertNotNull(routes)
        assertEquals(
            listOf(
                "hot_route_faelledparken_copenhagen",
                "hot_route_faelledparken__via_roads__copenhagen",
                "hot_route_go_stamp_rally__minato_tokyo",
                "hot_route_go_stamp_rally__koto_tokyo",
                "hot_route_go_stamp_rally__shinagawa_tokyo",
            ),
            routes!!.map { RouteRepository.idForRoute(it.name, it.city) },
        )
    }
}
