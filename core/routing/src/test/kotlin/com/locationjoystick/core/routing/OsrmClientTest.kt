package com.locationjoystick.core.routing

import com.locationjoystick.core.model.LatLng
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OsrmClientTest {
    private val client = OsrmClient()

    // MockWebServer tests

    private lateinit var server: MockWebServer
    private lateinit var testClient: OsrmClient

    @Before
    fun setUpServer() {
        server = MockWebServer()
        server.start()
        testClient = OsrmClient(server.url("/").toString())
    }

    @After
    fun tearDownServer() {
        server.shutdown()
    }

    @Test
    fun `getRoute returns multi-point polyline on Ok response`() =
        runTest {
            val body =
                """
                {
                  "code": "Ok",
                  "routes": [{
                    "geometry": {
                      "type": "LineString",
                      "coordinates": [
                        [2.3522, 48.8566],
                        [2.3540, 48.8580],
                        [2.3560, 48.8600]
                      ]
                    },
                    "distance": 500.0,
                    "duration": 360.0
                  }]
                }
                """.trimIndent()
            server.enqueue(MockResponse().setResponseCode(200).setBody(body))

            val result =
                testClient.getRoute(
                    profile = "foot",
                    waypoints = listOf(LatLng(48.8566, 2.3522), LatLng(48.8600, 2.3560)),
                )

            assertTrue("Expected success", result.isSuccess)
            val points = result.getOrNull()!!
            assertTrue("Expected more than 2 points from OSRM polyline", points.size > 2)
            // GeoJSON is [lon, lat]; verify parsing swaps them correctly
            assertEquals(48.8566, points[0].latitude, 0.0001)
            assertEquals(2.3522, points[0].longitude, 0.0001)
        }

    @Test
    fun `getRoute returns failure on non-Ok OSRM code`() =
        runTest {
            val body = """{"code": "NoRoute", "routes": []}"""
            server.enqueue(MockResponse().setResponseCode(200).setBody(body))

            val result =
                testClient.getRoute(
                    profile = "foot",
                    waypoints = listOf(LatLng(48.8566, 2.3522), LatLng(48.8600, 2.3560)),
                )

            assertTrue("Expected failure for non-Ok OSRM code", result.isFailure)
        }

    @Test
    fun `getRoute returns failure on HTTP error`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(400).setBody("Bad Request"))

            val result =
                testClient.getRoute(
                    profile = "foot",
                    waypoints = listOf(LatLng(48.8566, 2.3522), LatLng(48.8600, 2.3560)),
                )

            assertTrue("Expected failure on HTTP 400", result.isFailure)
        }

    @Test
    fun `getRoute request uses correct profile and coordinate format`() =
        runTest {
            val body =
                """
                {
                  "code": "Ok",
                  "routes": [{
                    "geometry": {"type": "LineString", "coordinates": [[2.3522, 48.8566], [2.356, 48.86]]},
                    "distance": 100.0,
                    "duration": 60.0
                  }]
                }
                """.trimIndent()
            server.enqueue(MockResponse().setResponseCode(200).setBody(body))

            testClient.getRoute(
                profile = "foot",
                waypoints = listOf(LatLng(48.8566, 2.3522), LatLng(48.8600, 2.3560)),
            )

            val request = server.takeRequest()
            assertTrue("Path should contain profile 'foot'", request.path!!.contains("/foot/"))
            // Coordinates are encoded as lon,lat;lon,lat
            assertTrue("Path should contain coordinates", request.path!!.contains("2.3522,48.8566"))
        }

    // straightLineRoute

    @Test
    fun `straightLineRoute returns two points`() {
        val from = LatLng(0.0, 0.0)
        val to = LatLng(1.0, 1.0)
        val route = client.straightLineRoute(from, to)

        assertEquals(2, route.size)
        assertEquals(from, route[0])
        assertEquals(to, route[1])
    }

    @Test
    fun `straightLineRoute preserves exact coordinates`() {
        val from = LatLng(48.8566, 2.3522)
        val to = LatLng(51.5074, -0.1278)
        val route = client.straightLineRoute(from, to)

        assertEquals(48.8566, route[0].latitude, 0.0001)
        assertEquals(2.3522, route[0].longitude, 0.0001)
        assertEquals(51.5074, route[1].latitude, 0.0001)
        assertEquals(-0.1278, route[1].longitude, 0.0001)
    }

    @Test
    fun `straightLineRoute same start and end returns two identical points`() {
        val point = LatLng(0.0, 0.0)
        val route = client.straightLineRoute(point, point)

        assertEquals(2, route.size)
        assertEquals(point, route[0])
        assertEquals(point, route[1])
    }

    @Test
    fun `straightLineRoute with negative coordinates`() {
        val from = LatLng(-33.8688, 151.2093) // Sydney
        val to = LatLng(-34.0, 151.5)
        val route = client.straightLineRoute(from, to)

        assertEquals(2, route.size)
        assertEquals(-33.8688, route[0].latitude, 0.0001)
        assertEquals(151.2093, route[0].longitude, 0.0001)
    }

    @Test
    fun `straightLineRoute with equator crossing`() {
        val from = LatLng(1.0, 0.0)
        val to = LatLng(-1.0, 0.0)
        val route = client.straightLineRoute(from, to)

        assertEquals(2, route.size)
        assertTrue("first point should be north", route[0].latitude > 0)
        assertTrue("second point should be south", route[1].latitude < 0)
    }

    @Test
    fun `straightLineRoute result is non-null and non-empty`() {
        val from = LatLng(0.0, 0.0)
        val to = LatLng(0.001, 0.001)
        val route = client.straightLineRoute(from, to)

        assertNotNull(route)
        assertTrue(route.isNotEmpty())
    }

    // resolveRoute

    @Test
    fun `resolveRoute with followRoads false returns straight line without network call`() =
        runTest {
            val from = LatLng(48.8566, 2.3522)
            val to = LatLng(51.5074, -0.1278)
            val route = client.resolveRoute("foot", from, to, followRoads = false)

            assertEquals(2, route.size)
            assertEquals(from, route[0])
            assertEquals(to, route[1])
        }

    @Test
    fun `resolveRoute with followRoads true and successful response returns OSRM route`() =
        runTest {
            val body =
                """
                {
                  "code": "Ok",
                  "routes": [{
                    "geometry": {"type": "LineString", "coordinates": [[2.3522, 48.8566], [2.354, 48.858], [2.356, 48.86]]},
                    "distance": 500.0,
                    "duration": 360.0
                  }]
                }
                """.trimIndent()
            server.enqueue(MockResponse().setResponseCode(200).setBody(body))

            val route =
                testClient.resolveRoute(
                    "foot",
                    LatLng(48.8566, 2.3522),
                    LatLng(48.86, 2.356),
                    followRoads = true,
                )

            assertEquals(3, route.size)
        }

    @Test
    fun `resolveRoute with followRoads true falls back to straight line on OSRM failure`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(500).setBody("error"))

            val from = LatLng(48.8566, 2.3522)
            val to = LatLng(51.5074, -0.1278)
            val route = testClient.resolveRoute("foot", from, to, followRoads = true)

            assertEquals(2, route.size)
            assertEquals(from, route[0])
            assertEquals(to, route[1])
        }

    @Test
    fun `getRoute skips malformed coordinates with fewer than 2 elements`() =
        runTest {
            val body =
                """
                {
                  "code": "Ok",
                  "routes": [{
                    "geometry": {
                      "type": "LineString",
                      "coordinates": [[2.3522, 48.8566], [2.354], [2.356, 48.86]]
                    },
                    "distance": 100.0,
                    "duration": 60.0
                  }]
                }
                """.trimIndent()
            server.enqueue(MockResponse().setResponseCode(200).setBody(body))

            val result =
                testClient.getRoute(
                    profile = "foot",
                    waypoints = listOf(LatLng(48.8566, 2.3522), LatLng(48.86, 2.356)),
                )

            assertTrue(result.isSuccess)
            assertEquals(2, result.getOrNull()!!.size)
        }

    @Test
    fun `getRoute returns failure when routes list is null`() =
        runTest {
            val body = """{"code": "Ok"}"""
            server.enqueue(MockResponse().setResponseCode(200).setBody(body))

            val result =
                testClient.getRoute(
                    profile = "foot",
                    waypoints = listOf(LatLng(48.8566, 2.3522), LatLng(48.86, 2.356)),
                )

            assertTrue("Expected failure when routes is null", result.isFailure)
        }

    // PROFILE_FOOT constant

    @Test
    fun `OsrmClient PROFILE_FOOT constant is defined`() {
        assertTrue(OsrmClient.PROFILE_FOOT.isNotEmpty())
    }
}
