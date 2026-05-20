package com.locationjoystick.feature.settings.impl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test

class GpsJoystickMigratorTest {
    private fun loadTestDb(): ByteArray? = javaClass.classLoader?.getResourceAsStream("gpsjoystick_test.db")?.readBytes()

    @Test
    fun `parses favorites from real db`() {
        val bytes = loadTestDb()
        Assume.assumeNotNull("gpsjoystick_test.db not present in test resources — skipping", bytes)
        val result = GpsJoystickMigrator.parse(bytes!!)
        assertTrue("Expected success", result.isSuccess)
        val migration = result.getOrNull()!!
        assertEquals("Expected 4 favorites", 4, migration.favorites.size)
        val names = migration.favorites.map { it.name }
        assertTrue("Expected Maison", names.any { it == "Maison" })
        assertTrue("Expected Cita", names.any { it == "Cita" })
        assertTrue("Expected Tuileries", names.any { it == "Tuileries" })
        assertTrue("Expected Lux", names.any { it == "Lux" })
        migration.favorites.forEach { fav ->
            assertTrue("Lat should be ~48–51", fav.position.latitude in 46.0..53.0)
            assertTrue("Lon should be ~2–3", fav.position.longitude in 0.0..5.0)
        }
    }

    @Test
    fun `parses routes from real db`() {
        val bytes = loadTestDb()
        Assume.assumeNotNull("gpsjoystick_test.db not present in test resources — skipping", bytes)
        val result = GpsJoystickMigrator.parse(bytes!!)
        assertTrue("Expected success", result.isSuccess)
        val migration = result.getOrNull()!!
        assertEquals("Expected 8 routes", 8, migration.routes.size)
        migration.routes.forEach { route ->
            assertTrue("Route name should not be blank", route.name.isNotBlank())
            assertTrue("Route should have ≥ 2 waypoints", route.waypoints.size >= 2)
        }
    }

    @Test
    fun `routes have valid coordinates`() {
        val bytes = loadTestDb()
        Assume.assumeNotNull("gpsjoystick_test.db not present in test resources — skipping", bytes)
        val result = GpsJoystickMigrator.parse(bytes!!)
        assertTrue("Expected success", result.isSuccess)
        val migration = result.getOrNull()!!
        migration.routes.forEach { route ->
            route.waypoints.forEach { wp ->
                assertTrue("Latitude should be finite and non-zero", wp.position.latitude.isFinite() && wp.position.latitude != 0.0)
                assertTrue("Longitude should be finite and non-zero", wp.position.longitude.isFinite() && wp.position.longitude != 0.0)
            }
        }
    }

    @Test
    fun `returns no speed profiles`() {
        val bytes = loadTestDb()
        Assume.assumeNotNull("gpsjoystick_test.db not present in test resources — skipping", bytes)
        val result = GpsJoystickMigrator.parse(bytes!!)
        assertTrue("Expected success", result.isSuccess)
        val migration = result.getOrNull()!!
        assertNull("walkSpeed should be null", migration.walkSpeed)
        assertNull("runSpeed should be null", migration.runSpeed)
        assertNull("bikeSpeed should be null", migration.bikeSpeed)
    }

    @Test
    fun `returns failure on empty bytes`() {
        val result = GpsJoystickMigrator.parse(ByteArray(0))
        assertTrue("Expected failure on empty bytes", result.isFailure)
    }

    @Test
    fun `returns failure on non-realm bytes`() {
        val result = GpsJoystickMigrator.parse("{}".toByteArray())
        assertTrue("Expected failure on non-Realm bytes", result.isFailure)
    }
}
