package com.locationjoystick.core.routing

import com.locationjoystick.core.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OsrmClientTest {
    private val client = OsrmClient()

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

    // PROFILE_FOOT constant

    @Test
    fun `OsrmClient PROFILE_FOOT constant is defined`() {
        assertTrue(OsrmClient.PROFILE_FOOT.isNotEmpty())
    }
}
