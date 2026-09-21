package com.locationjoystick.core.routing

import com.locationjoystick.core.common.constants.AppConstants
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
            // NoRoute advances the ladder through all three profiles (foot/bike/driving).
            repeat(3) { server.enqueue(MockResponse().setResponseCode(200).setBody(body)) }

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
            // HTTP errors are classified ServerError and advance through all three ladder slots.
            repeat(3) { server.enqueue(MockResponse().setResponseCode(400).setBody("Bad Request")) }

            val result =
                testClient.getRoute(
                    profile = "foot",
                    waypoints = listOf(LatLng(48.8566, 2.3522), LatLng(48.8600, 2.3560)),
                )

            assertTrue("Expected failure on HTTP 400", result.isFailure)
            assertEquals("Expected one attempt per ladder slot (foot/bike/driving)", 3, server.requestCount)
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

    @Test
    fun `getRoute request path has no double slash even when base URL has a trailing slash`() =
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
            assertTrue("Path should start with a single slash before 'route'", request.path!!.startsWith("/route/"))
        }

    @Test
    fun `getRoute escalates foot to bike then driving on NoRoute`() =
        runTest {
            val okBody =
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
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code": "NoRoute", "routes": []}"""))
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code": "NoRoute", "routes": []}"""))
            server.enqueue(MockResponse().setResponseCode(200).setBody(okBody))

            val result =
                testClient.getRoute(
                    profile = "foot",
                    waypoints = listOf(LatLng(48.8566, 2.3522), LatLng(48.8600, 2.3560)),
                )

            assertTrue("Expected success after profile escalation", result.isSuccess)
            assertTrue("First request should use 'foot' profile", server.takeRequest().path!!.contains("/foot/"))
            assertTrue("Second request should use 'bike' profile", server.takeRequest().path!!.contains("/bike/"))
            assertTrue("Third request should use 'driving' profile", server.takeRequest().path!!.contains("/driving/"))
        }

    @Test
    fun `getRoute snaps waypoints to nearest road and retries on NoSegment`() =
        runTest {
            val noSegmentBody = """{"code": "NoSegment", "routes": []}"""
            val nearestBody = """{"code": "Ok", "waypoints": [{"location": [2.353, 48.857]}]}"""
            val okBody =
                """
                {
                  "code": "Ok",
                  "routes": [{
                    "geometry": {"type": "LineString", "coordinates": [[2.353, 48.857], [2.356, 48.86]]},
                    "distance": 100.0,
                    "duration": 60.0
                  }]
                }
                """.trimIndent()
            server.enqueue(MockResponse().setResponseCode(200).setBody(noSegmentBody))
            server.enqueue(MockResponse().setResponseCode(200).setBody(nearestBody))
            server.enqueue(MockResponse().setResponseCode(200).setBody(nearestBody))
            server.enqueue(MockResponse().setResponseCode(200).setBody(okBody))

            val result =
                testClient.getRoute(
                    profile = "foot",
                    waypoints = listOf(LatLng(48.8566, 2.3522), LatLng(48.8600, 2.3560)),
                )

            assertTrue("Expected success after snap-to-road retry", result.isSuccess)
            server.takeRequest() // initial route request (NoSegment)
            val nearest1 = server.takeRequest()
            val nearest2 = server.takeRequest()
            val retry = server.takeRequest()
            assertTrue("Expected nearest lookup", nearest1.path!!.contains("/nearest/"))
            assertTrue("Expected nearest lookup", nearest2.path!!.contains("/nearest/"))
            assertTrue("Retry should use route endpoint", retry.path!!.contains("/route/"))
        }

    // Generic retry

    @Test
    fun `getRoute retries on server error and succeeds on third attempt`() =
        runTest {
            val okBody =
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
            server.enqueue(MockResponse().setResponseCode(500).setBody("error"))
            server.enqueue(MockResponse().setResponseCode(500).setBody("error"))
            server.enqueue(MockResponse().setResponseCode(200).setBody(okBody))

            val result =
                testClient.getRoute(
                    profile = "foot",
                    waypoints = listOf(LatLng(48.8566, 2.3522), LatLng(48.8600, 2.3560)),
                )

            assertTrue("Expected success on the third ladder slot", result.isSuccess)
            assertEquals("Expected exactly 3 attempts (foot/bike/driving slots)", 3, server.requestCount)
        }

    @Test
    fun `getRoute fails over to the secondary backend when the primary keeps erroring`() =
        runTest {
            val okBody =
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
            val secondary = MockWebServer()
            secondary.start()
            try {
                val failoverClient =
                    OsrmClient(
                        listOf(
                            OsrmBackend(singleGraph = false) { server.url("/").toString().trimEnd('/') },
                            OsrmBackend(singleGraph = false) { secondary.url("/").toString().trimEnd('/') },
                        ),
                    )
                repeat(3) { server.enqueue(MockResponse().setResponseCode(500).setBody("error")) }
                secondary.enqueue(MockResponse().setResponseCode(200).setBody(okBody))

                val result =
                    failoverClient.getRoute(
                        profile = "foot",
                        waypoints = listOf(LatLng(48.8566, 2.3522), LatLng(48.8600, 2.3560)),
                    )

                assertTrue("Expected success from the secondary backend", result.isSuccess)
                assertEquals("Primary should get one attempt per profile", 3, server.requestCount)
                assertEquals("Secondary should answer on its first slot", 1, secondary.requestCount)
                assertTrue("Secondary request should restart at 'foot'", secondary.takeRequest().path!!.contains("/foot/"))
            } finally {
                secondary.shutdown()
            }
        }

    @Test
    fun `getRoute retries a 429 response and eventually succeeds`() =
        runTest {
            val okBody =
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
            server.enqueue(MockResponse().setResponseCode(429).setBody("rate limited"))
            server.enqueue(MockResponse().setResponseCode(200).setBody(okBody))

            val result =
                testClient.getRoute(
                    profile = "foot",
                    waypoints = listOf(LatLng(48.8566, 2.3522), LatLng(48.8600, 2.3560)),
                )

            assertTrue("429 must be retried rather than failing immediately", result.isSuccess)
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `getRoute honors a numeric Retry-After header on 429`() =
        runTest {
            val okBody =
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
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "7").setBody("rate limited"))
            server.enqueue(MockResponse().setResponseCode(200).setBody(okBody))

            val result =
                testClient.getRoute(
                    profile = "foot",
                    waypoints = listOf(LatLng(48.8566, 2.3522), LatLng(48.8600, 2.3560)),
                )

            assertTrue("429 with Retry-After must still succeed after one retry", result.isSuccess)
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `classifyOsrmFailure classifies 429 as RateLimited distinct from generic ServerError, honoring Retry-After`() {
        val rateLimited = classifyOsrmFailure(OsrmHttpException(429, "OSRM HTTP 429", retryAfterMs = 7_000L))
        assertTrue("429 must classify as RateLimited, not ServerError", rateLimited is OsrmFailureReason.RateLimited)
        assertEquals(7_000L, (rateLimited as OsrmFailureReason.RateLimited).retryAfterMs)

        val noHeader = classifyOsrmFailure(OsrmHttpException(429, "OSRM HTTP 429"))
        assertEquals(null, (noHeader as OsrmFailureReason.RateLimited).retryAfterMs)

        val serverError = classifyOsrmFailure(OsrmHttpException(500, "OSRM HTTP 500"))
        assertTrue("Non-429 errors must remain generic ServerError", serverError is OsrmFailureReason.ServerError)
    }

    @Test
    fun `getRoute skips remaining profiles of a single-graph backend on NoRoute`() =
        runTest {
            val body = """{"code": "NoRoute", "routes": []}"""
            server.enqueue(MockResponse().setResponseCode(200).setBody(body))
            val singleGraphClient =
                OsrmClient(listOf(OsrmBackend(singleGraph = true) { server.url("/").toString().trimEnd('/') }))

            val result =
                singleGraphClient.getRoute(
                    profile = "foot",
                    waypoints = listOf(LatLng(48.8566, 2.3522), LatLng(48.8600, 2.3560)),
                )

            assertTrue("Expected failure", result.isFailure)
            assertEquals(
                "A single-graph backend answers identically for every profile — bike/driving slots must be skipped",
                1,
                server.requestCount,
            )
        }

    // Bisection

    @Test
    fun `bisection is not triggered below the distance threshold`() =
        runTest {
            val body = """{"code": "NoRoute", "routes": []}"""
            repeat(3) { server.enqueue(MockResponse().setResponseCode(200).setBody(body)) }

            // ~600m apart, well under BISECTION_MIN_DISTANCE_METERS (2500m).
            val result =
                testClient.getRoute(
                    profile = "foot",
                    waypoints = listOf(LatLng(48.8566, 2.3522), LatLng(48.8600, 2.3560)),
                )

            assertTrue("Expected failure (no bisection below threshold)", result.isFailure)
            assertEquals("Expected only the direct foot/bike/driving attempts", 3, server.requestCount)
        }

    @Test
    fun `bisection splits a long failing leg and recombines successful halves`() =
        runTest {
            val noRoute = """{"code": "NoRoute", "routes": []}"""

            fun okBody(
                lon: Double,
                lat: Double,
                distance: Double,
            ) = """
                {
                  "code": "Ok",
                  "routes": [{
                    "geometry": {"type": "LineString", "coordinates": [[$lon, $lat], [${lon + 0.001}, ${lat + 0.001}]]},
                    "distance": $distance,
                    "duration": 10.0
                  }]
                }
                """.trimIndent()
            repeat(3) { server.enqueue(MockResponse().setResponseCode(200).setBody(noRoute)) } // direct foot/bike/driving
            server.enqueue(MockResponse().setResponseCode(200).setBody(okBody(2.3522, 48.8566, 300.0))) // left half
            server.enqueue(MockResponse().setResponseCode(200).setBody(okBody(2.3522, 48.8566, 300.0))) // right half

            // ~4.8km apart, beyond BISECTION_MIN_DISTANCE_METERS (2500m).
            val result =
                testClient.getRoute(
                    profile = "foot",
                    waypoints = listOf(LatLng(48.8566, 2.3522), LatLng(48.9000, 2.3522)),
                )

            assertTrue("Expected bisection to recover a route", result.isSuccess)
            assertEquals("Expected 3 direct attempts + 2 bisected halves", 5, server.requestCount)
        }

    @Test
    fun `bisection recurses deeper when one half keeps failing`() =
        runTest {
            val noRoute = """{"code": "NoRoute", "routes": []}"""
            val okBody =
                """
                {
                  "code": "Ok",
                  "routes": [{
                    "geometry": {"type": "LineString", "coordinates": [[2.3522, 48.8566], [2.353, 48.857]]},
                    "distance": 150.0,
                    "duration": 10.0
                  }]
                }
                """.trimIndent()
            repeat(3) { server.enqueue(MockResponse().setResponseCode(200).setBody(noRoute)) } // direct foot/bike/driving
            // depth-1 split: one half succeeds, the other fails and must recurse to depth-2
            server.enqueue(MockResponse().setResponseCode(200).setBody(okBody))
            server.enqueue(MockResponse().setResponseCode(200).setBody(noRoute))
            // depth-2 split of the failed half: both sub-halves succeed
            server.enqueue(MockResponse().setResponseCode(200).setBody(okBody))
            server.enqueue(MockResponse().setResponseCode(200).setBody(okBody))

            val result =
                testClient.getRoute(
                    profile = "foot",
                    waypoints = listOf(LatLng(48.8566, 2.3522), LatLng(48.9000, 2.3522)),
                )

            assertTrue("Expected recursive bisection to recover a route", result.isSuccess)
            assertEquals("Expected 3 direct + 2 depth-1 + 2 depth-2 attempts", 7, server.requestCount)
        }

    @Test
    fun `bisection falls back to straight-line past max depth without exceeding bounded request volume`() =
        runTest {
            val noRoute = """{"code": "NoRoute", "routes": []}"""
            // 3 direct + 2 (depth1) + 4 (depth2) + 8 (depth3) + 16 (depth4) + 32 (depth5) = 65.
            // Bisection leaves are one attempt per backend (single backend here), so this is also
            // the exact total request count.
            repeat(65) { server.enqueue(MockResponse().setResponseCode(200).setBody(noRoute)) }

            val result =
                testClient.getRoute(
                    profile = "foot",
                    waypoints = listOf(LatLng(48.8566, 2.3522), LatLng(48.9000, 2.3522)),
                )

            assertTrue("Max-depth leaves fall back to straight-line, so the overall route still succeeds", result.isSuccess)
            assertEquals("Recursion must stop at depth 5, bounding total request volume", 65, server.requestCount)
        }

    @Test
    fun `bisection time budget is enforced preemptively, not by the underlying call timeout`() =
        runTest {
            val noRoute = """{"code": "NoRoute", "routes": []}"""
            repeat(3) { server.enqueue(MockResponse().setResponseCode(200).setBody(noRoute)) } // direct foot/bike/driving
            // Both depth-1 halves hang past the 2s bisection budget (but under the 2.5s callTimeout).
            server.enqueue(MockResponse().setResponseCode(200).setBody(noRoute).setBodyDelay(4, java.util.concurrent.TimeUnit.SECONDS))
            server.enqueue(MockResponse().setResponseCode(200).setBody(noRoute).setBodyDelay(4, java.util.concurrent.TimeUnit.SECONDS))

            val elapsedMs =
                kotlin.system.measureTimeMillis {
                    val result =
                        testClient.getRoute(
                            profile = "foot",
                            waypoints = listOf(LatLng(48.8566, 2.3522), LatLng(48.9000, 2.3522)),
                        )
                    assertTrue("Expected failure once the bisection budget is exceeded", result.isFailure)
                }

            // Pins enforcement to the 2s budget itself (plus ~0.6s of ladder backoffs), not a loose
            // bound that would also pass if cancellation silently failed and the call instead ran to
            // the 4s mock delay (the old blocking-execute() regression).
            assertTrue(
                "Expected the call to return near the 2s bisection budget, not the 4s mock delay " +
                    "(took ${elapsedMs}ms)",
                elapsedMs < 3_500,
            )
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
            // NoRoute on all three ladder slots (foot/bike/driving).
            repeat(3) { server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code": "NoRoute", "routes": []}""")) }

            // Kept under the bisection distance threshold — this test is about the simple
            // fallback path, not bisection (covered separately below).
            val from = LatLng(48.8566, 2.3522)
            val to = LatLng(48.8600, 2.3560)
            var reportedReason: OsrmFailureReason? = null
            val route = testClient.resolveRoute("foot", from, to, followRoads = true, onFallback = { reportedReason = it })

            assertEquals(2, route.size)
            assertEquals(from, route[0])
            assertEquals(to, route[1])
            assertEquals(OsrmFailureReason.NoRouteFound, reportedReason)
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
            // Any failure advances the ladder; enqueue one per profile slot.
            repeat(3) { server.enqueue(MockResponse().setResponseCode(200).setBody(body)) }

            val result =
                testClient.getRoute(
                    profile = "foot",
                    waypoints = listOf(LatLng(48.8566, 2.3522), LatLng(48.86, 2.356)),
                )

            assertTrue("Expected failure when routes is null", result.isFailure)
        }

    // Caching and cooldown

    private val okBody =
        """{"code":"Ok","routes":[{"geometry":{"type":"LineString","coordinates":[[2.35,48.85],[2.36,48.86]]},""" +
            """"distance":1.0,"duration":1.0}]}"""
    private val cachePoints = listOf(LatLng(48.8566, 2.3522), LatLng(48.8600, 2.3560))

    private fun clientWith(
        cooldowns: BackendCooldowns? = null,
        nowMs: () -> Long = System::currentTimeMillis,
    ) = OsrmClient(
        listOf(OsrmBackend(singleGraph = false) { server.url("/").toString().trimEnd('/') }),
        cooldowns,
        nowMs,
    )

    @Test
    fun `repeated identical getRoute is served from cache`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody(okBody))
            assertTrue(testClient.getRoute("foot", cachePoints).isSuccess)
            assertTrue(testClient.getRoute("foot", cachePoints).isSuccess)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `expired cache entry is served stale when the ladder fails`() =
        runTest {
            var now = 0L
            val c = clientWith(nowMs = { now })
            server.enqueue(MockResponse().setResponseCode(200).setBody(okBody))
            assertTrue(c.getRoute("foot", cachePoints).isSuccess)
            now += AppConstants.OsrmConstants.CACHE_TTL_MS + 1
            repeat(3) { server.enqueue(MockResponse().setResponseCode(500)) }
            assertTrue(c.getRoute("foot", cachePoints).isSuccess)
        }

    @Test
    fun `429 cools the backend so later calls make no request`() =
        runTest {
            val c = clientWith(BackendCooldowns())
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "60"))
            assertTrue(c.getRoute("foot", cachePoints).isFailure)
            val after = server.requestCount
            assertTrue(c.getRoute("foot", listOf(LatLng(10.0, 10.0), LatLng(10.001, 10.001))).isFailure)
            assertEquals(after, server.requestCount)
        }

    // PROFILE_FOOT constant

    @Test
    fun `OsrmClient PROFILE_FOOT constant is defined`() {
        assertTrue(OsrmClient.PROFILE_FOOT.isNotEmpty())
    }

    // Live integration test — requires network; validates real OSRM round-trip

    @Test
    fun `getRoute live - two Paris points return road-following polyline`() =
        runTest {
            val from = LatLng(48.8566, 2.3522) // Notre-Dame
            val to = LatLng(48.8606, 2.3376) // Louvre
            val result = client.getRoute(profile = OsrmClient.PROFILE_FOOT, waypoints = listOf(from, to))

            assertTrue("Live OSRM call failed: ${result.exceptionOrNull()}", result.isSuccess)
            val points = result.getOrNull()!!
            assertTrue("Expected road-following polyline (>2 points), got ${points.size}", points.size > 2)
        }
}
