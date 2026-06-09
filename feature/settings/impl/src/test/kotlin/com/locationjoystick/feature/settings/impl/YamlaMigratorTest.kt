package com.locationjoystick.feature.settings.impl

import com.locationjoystick.core.common.constants.AppConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class YamlaMigratorTest {
    // -------------------------------------------------------------------------
    // Format 1: obfuscated settings object
    // -------------------------------------------------------------------------

    @Test
    fun `parses speeds from obfuscated settings`() {
        val json = """{"e":2.0,"f":10.0,"g":20.0,"w":[]}"""
        val result = YamlaMigrator.parse(json)
        assertTrue(result.isSuccess)
        val migration = result.getOrNull()!!
        assertApprox(2.0 / 3.6, migration.walkSpeed!!)
        assertApprox(10.0 / 3.6, migration.runSpeed!!)
        assertApprox(20.0 / 3.6, migration.bikeSpeed!!)
    }

    @Test
    fun `parses empty favorites when w is empty array`() {
        val json = """{"e":2.0,"f":10.0,"g":20.0,"w":[]}"""
        val result = YamlaMigrator.parse(json)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.favorites.isEmpty())
    }

    @Test
    fun `parses populated favorites from obfuscated settings`() {
        val json = """{"w":[{"name":"Louvre","lat":48.86,"lon":2.33}]}"""
        val result = YamlaMigrator.parse(json)
        assertTrue(result.isSuccess)
        val favorites = result.getOrNull()!!.favorites
        assertEquals(1, favorites.size)
        assertEquals("Louvre", favorites[0].name)
        assertApprox(48.86, favorites[0].position.latitude)
        assertApprox(2.33, favorites[0].position.longitude)
    }

    @Test
    fun `obfuscated settings produces no routes`() {
        val json = """{"e":2.0,"w":[]}"""
        val result = YamlaMigrator.parse(json)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.routes.isEmpty())
    }

    @Test
    fun `clamps speed below minimum`() {
        val json = """{"e":0.0001}"""
        val result = YamlaMigrator.parse(json)
        assertTrue(result.isSuccess)
        val migration = result.getOrNull()!!
        assertEquals(AppConstants.ProfileConstants.MIN_SPEED_MS, migration.walkSpeed!!, 0.0001)
    }

    @Test
    fun `clamps speed above maximum`() {
        val json = """{"e":999.0}"""
        val result = YamlaMigrator.parse(json)
        assertTrue(result.isSuccess)
        val migration = result.getOrNull()!!
        assertEquals(AppConstants.ProfileConstants.MAX_SPEED_MS, migration.walkSpeed!!, 0.0001)
    }

    @Test
    fun `missing speed fields produce null speeds`() {
        val json = """{}"""
        val result = YamlaMigrator.parse(json)
        assertTrue(result.isSuccess)
        val migration = result.getOrNull()!!
        assertNull(migration.walkSpeed)
        assertNull(migration.runSpeed)
        assertNull(migration.bikeSpeed)
    }

    @Test
    fun `parses yamla settings fixture file`() {
        val json = loadFixture("yamla_settings.json")
        val result = YamlaMigrator.parse(json)
        assertTrue("Expected success but got: ${result.exceptionOrNull()}", result.isSuccess)
        val migration = result.getOrNull()!!
        // Settings fixture has speeds e/f/g and possibly favorites in w
        assertTrue(migration.routes.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Format 2: all_routes array
    // -------------------------------------------------------------------------

    @Test
    fun `parses all_routes format inline`() {
        val json =
            """[{"name":"Park loop","points":[""" +
                """{"latitude":48861000,"longitude":2335000},""" +
                """{"latitude":48862000,"longitude":2336000}]}]"""
        val result = YamlaMigrator.parse(json)
        assertTrue(result.isSuccess)
        val migration = result.getOrNull()!!
        assertEquals(1, migration.routes.size)
        assertEquals("Park loop", migration.routes[0].name)
        assertEquals(2, migration.routes[0].waypoints.size)
        assertApprox(
            48.861,
            migration.routes[0]
                .waypoints[0]
                .position.latitude,
        )
        assertApprox(
            2.335,
            migration.routes[0]
                .waypoints[0]
                .position.longitude,
        )
        assertTrue(migration.favorites.isEmpty())
        assertNull(migration.walkSpeed)
        assertNull(migration.runSpeed)
        assertNull(migration.bikeSpeed)
    }

    @Test
    fun `parses all_routes fixture file`() {
        val json = loadFixture("all_routes.json")
        val result = YamlaMigrator.parse(json)
        assertTrue("Expected success but got: ${result.exceptionOrNull()}", result.isSuccess)
        val migration = result.getOrNull()!!
        assertTrue("Expected routes but got none", migration.routes.isNotEmpty())
        assertTrue("Expected no favorites", migration.favorites.isEmpty())
        migration.routes.forEach { route ->
            assertTrue("Route name should not be blank", route.name.isNotBlank())
            assertTrue("Route should have waypoints", route.waypoints.isNotEmpty())
            route.waypoints.forEach { wp ->
                assertTrue("Latitude out of range: ${wp.position.latitude}", wp.position.latitude in -90.0..90.0)
                assertTrue("Longitude out of range: ${wp.position.longitude}", wp.position.longitude in -180.0..180.0)
            }
        }
    }

    @Test
    fun `all_routes waypoint order indices are sequential`() {
        val json =
            """[{"name":"Route","points":[""" +
                """{"latitude":10000000,"longitude":20000000},""" +
                """{"latitude":11000000,"longitude":21000000},""" +
                """{"latitude":12000000,"longitude":22000000}]}]"""
        val result = YamlaMigrator.parse(json)
        assertTrue(result.isSuccess)
        val waypoints = result.getOrNull()!!.routes[0].waypoints
        assertEquals(listOf(0, 1, 2), waypoints.map { it.orderIndex })
    }

    // -------------------------------------------------------------------------
    // Format 3: favorites array
    // -------------------------------------------------------------------------

    @Test
    fun `parses favorites format inline`() {
        val json = """[{"latLng":{"latitude":48861000,"longitude":2335000},"name":"My place"}]"""
        val result = YamlaMigrator.parse(json)
        assertTrue(result.isSuccess)
        val migration = result.getOrNull()!!
        assertEquals(1, migration.favorites.size)
        assertEquals("My place", migration.favorites[0].name)
        assertApprox(48.861, migration.favorites[0].position.latitude)
        assertApprox(2.335, migration.favorites[0].position.longitude)
        assertTrue(migration.routes.isEmpty())
        assertNull(migration.walkSpeed)
    }

    @Test
    fun `parses favorites fixture file`() {
        val json = loadFixture("favorites.json")
        val result = YamlaMigrator.parse(json)
        assertTrue("Expected success but got: ${result.exceptionOrNull()}", result.isSuccess)
        val migration = result.getOrNull()!!
        assertTrue("Expected favorites but got none", migration.favorites.isNotEmpty())
        assertTrue("Expected no routes", migration.routes.isEmpty())
        migration.favorites.forEach { fav ->
            assertTrue("Favorite name should not be blank", fav.name.isNotBlank())
            assertTrue("Latitude out of range: ${fav.position.latitude}", fav.position.latitude in -90.0..90.0)
            assertTrue("Longitude out of range: ${fav.position.longitude}", fav.position.longitude in -180.0..180.0)
        }
    }

    // -------------------------------------------------------------------------
    // Error cases
    // -------------------------------------------------------------------------

    @Test
    fun `returns failure on malformed JSON`() {
        val result = YamlaMigrator.parse("{not-valid-json")
        assertTrue(result.isFailure)
    }

    @Test
    fun `returns failure on empty string`() {
        val result = YamlaMigrator.parse("")
        assertTrue(result.isFailure)
    }

    @Test
    fun `returns failure when json does not start with object or array`() {
        val result = YamlaMigrator.parse("\"just a string\"")
        assertTrue(result.isFailure)
    }

    @Test
    fun `returns failure when array element has unrecognised format`() {
        val result = YamlaMigrator.parse("""[{"unknown": "key"}]""")
        assertTrue(result.isFailure)
    }

    @Test
    fun `empty array returns empty migration`() {
        val result = YamlaMigrator.parse("[]")
        assertTrue(result.isSuccess)
        val migration = result.getOrNull()!!
        assertTrue(migration.routes.isEmpty())
        assertTrue(migration.favorites.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun loadFixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) {
            "Fixture not found: $name"
        }.bufferedReader().readText()

    private fun assertApprox(
        expected: Double,
        actual: Double,
        delta: Double = 0.0001,
    ) {
        assertTrue(
            "Expected ~$expected but was $actual",
            abs(expected - actual) <= delta,
        )
    }
}
