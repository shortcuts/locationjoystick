package com.locationjoystick.feature.settings.impl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [GpsJoystickMigrator] using anonymized real .db fixtures.
 *
 * Coordinates and names in the fixtures have been randomized to avoid data leaks.
 * Structural counts (favorites, routes, waypoints) are extracted directly from
 * Realm's B-tree and Group table directory:
 *   20250809 — 3 named favs, 7 routes with 214 total waypoints
 *   20250812 — 3 named favs, 7 routes with 183 total waypoints
 *   20250901 — 4 named favs, 6 routes with 176 total waypoints
 *   20251004 — 4 named favs, 9 routes with 209 total waypoints
 *   20251008 — 4 named favs, 9 routes with 217 total waypoints
 *   20251008-1 — 4 named favs, 9 routes with 217 total waypoints (duplicate variant)
 *   20260508 — 8 named favs, 9 routes with 131 total waypoints
 *   20260520 — 4 named favs, 8 routes with 132 total waypoints
 *   20260613 — 0 favs, 2 routes with 21 total waypoints
 *   user_feedback — 30 named favs, 17 routes with 475 total waypoints
 */
class GpsJoystickMigratorTest {
    private fun loadFixture(name: String): ByteArray {
        val stream = javaClass.classLoader?.getResourceAsStream(name)
        assertNotNull("Test fixture not found: $name", stream)
        return stream!!.readBytes()
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun assertValidCoord(
        value: Double,
        label: String,
    ) {
        assertTrue("$label should be finite", value.isFinite())
        assertTrue("$label lat/lon should be plausible (abs <= 180)", Math.abs(value) <= 180.0)
    }

    private fun assertFavorites(
        migration: MigrationResult,
        expectedCount: Int,
    ) {
        assertEquals("favorites count", expectedCount, migration.favorites.size)
        migration.favorites.forEachIndexed { i, fav ->
            assertTrue("fav[$i].name should not be blank", fav.name.isNotBlank())
            assertValidCoord(fav.position.latitude, "fav[$i].lat")
            assertValidCoord(fav.position.longitude, "fav[$i].lon")
        }
    }

    private fun assertRoutes(
        migration: MigrationResult,
        expectedCount: Int,
        totalWaypoints: Int,
    ) {
        assertEquals("routes count", expectedCount, migration.routes.size)
        val actualTotal = migration.routes.sumOf { it.waypoints.size }
        assertEquals("total waypoints across all routes", totalWaypoints, actualTotal)
        migration.routes.forEachIndexed { ri, route ->
            assertTrue("route[$ri].name should not be blank", route.name.isNotBlank())
            assertTrue("route[$ri] should have ≥ 1 waypoint", route.waypoints.isNotEmpty())
            route.waypoints.forEachIndexed { wi, wp ->
                assertValidCoord(wp.position.latitude, "route[$ri].wp[$wi].lat")
                assertValidCoord(wp.position.longitude, "route[$ri].wp[$wi].lon")
            }
        }
    }

    // ── error cases ───────────────────────────────────────────────────────────

    @Test
    fun `returns failure on empty bytes`() {
        assertTrue(GpsJoystickMigrator.parse(ByteArray(0)).isFailure)
    }

    @Test
    fun `returns failure on non-realm bytes`() {
        assertTrue(GpsJoystickMigrator.parse("{}".toByteArray()).isFailure)
    }

    // ── per-fixture tests ─────────────────────────────────────────────────────

    @Test
    fun `20250809 - 3 named favs and 7 routes with 214 waypoints`() {
        val result = GpsJoystickMigrator.parse(loadFixture("gpsjoystick_20250809211402.db"))
        assertTrue(result.isSuccess)
        val m = result.getOrThrow()
        assertFavorites(m, expectedCount = 3)
        assertRoutes(m, expectedCount = 7, totalWaypoints = 214)
    }

    @Test
    fun `20250812 - 3 named favs and 7 routes with 183 waypoints`() {
        val result = GpsJoystickMigrator.parse(loadFixture("gpsjoystick_20250812234223.db"))
        assertTrue(result.isSuccess)
        val m = result.getOrThrow()
        assertFavorites(m, expectedCount = 3)
        assertRoutes(m, expectedCount = 7, totalWaypoints = 183)
    }

    @Test
    fun `20250901 - 4 named favs and 6 routes with 176 waypoints`() {
        val result = GpsJoystickMigrator.parse(loadFixture("gpsjoystick_20250901192919.db"))
        assertTrue(result.isSuccess)
        val m = result.getOrThrow()
        assertFavorites(m, expectedCount = 4)
        assertRoutes(m, expectedCount = 6, totalWaypoints = 176)
    }

    @Test
    fun `20251004 - 4 named favs and 9 routes with 209 waypoints`() {
        val result = GpsJoystickMigrator.parse(loadFixture("gpsjoystick_20251004095615.db"))
        assertTrue(result.isSuccess)
        val m = result.getOrThrow()
        assertFavorites(m, expectedCount = 4)
        assertRoutes(m, expectedCount = 9, totalWaypoints = 209)
    }

    @Test
    fun `20251008 - 4 named favs and 9 routes with 217 waypoints`() {
        val result = GpsJoystickMigrator.parse(loadFixture("gpsjoystick_20251008134939.db"))
        assertTrue(result.isSuccess)
        val m = result.getOrThrow()
        assertFavorites(m, expectedCount = 4)
        assertRoutes(m, expectedCount = 9, totalWaypoints = 217)
    }

    @Test
    fun `20251008-1 - duplicate variant same expectations`() {
        val result = GpsJoystickMigrator.parse(loadFixture("gpsjoystick_20251008134939-1.db"))
        assertTrue(result.isSuccess)
        val m = result.getOrThrow()
        assertFavorites(m, expectedCount = 4)
        assertRoutes(m, expectedCount = 9, totalWaypoints = 217)
    }

    @Test
    fun `20260508 - 8 named favs and 9 routes with 131 waypoints`() {
        val result = GpsJoystickMigrator.parse(loadFixture("gpsjoystick_20260508222509.db"))
        assertTrue(result.isSuccess)
        val m = result.getOrThrow()
        assertFavorites(m, expectedCount = 8)
        assertRoutes(m, expectedCount = 9, totalWaypoints = 131)
    }

    @Test
    fun `20260520 - 4 named favs and 8 routes with 132 waypoints`() {
        val result = GpsJoystickMigrator.parse(loadFixture("gpsjoystick_20260520131222.db"))
        assertTrue(result.isSuccess)
        val m = result.getOrThrow()
        assertFavorites(m, expectedCount = 4)
        assertRoutes(m, expectedCount = 8, totalWaypoints = 132)
    }

    @Test
    fun `20260613 - 0 favs and 2 routes with 21 waypoints`() {
        val result = GpsJoystickMigrator.parse(loadFixture("gpsjoystick_20260613213704.db"))
        assertTrue(result.isSuccess)
        val m = result.getOrThrow()
        assertFavorites(m, expectedCount = 0)
        assertRoutes(m, expectedCount = 2, totalWaypoints = 21)
    }

    @Test
    fun testGpsJoystickImportUserFeedback() {
        val result = GpsJoystickMigrator.parse(loadFixture("gpsjoystick_user_feedback.db"))
        assertTrue(result.isSuccess)
        val m = result.getOrThrow()
        assertFavorites(m, expectedCount = 30)
        assertRoutes(m, expectedCount = 17, totalWaypoints = 475)
    }

    // ── GPX format (newer GPS Joystick exports) ──────────────────────────────

    @Test
    fun `issue 63 - bare wpt GPX export imports as a single unnamed route`() {
        // Reuses the Routes screen's generic GPX import (parseGpxRoutes), which has no concept
        // of favorites — a bare top-level <wpt> becomes a one-waypoint "Imported Route" (issue #27).
        val result = GpsJoystickMigrator.parse(loadFixture("gpsjoystick_20260904003942.gpx.txt"))
        assertTrue(result.isSuccess)
        val m = result.getOrThrow()
        assertTrue(m.favorites.isEmpty())
        assertRoutes(m, expectedCount = 1, totalWaypoints = 1)
        assertEquals("Imported Route", m.routes[0].name)
    }

    @Test
    fun `GPX export with a trk imports as a named route`() {
        val gpx =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1"><trk><name>Morning walk</name><trkseg>
            <trkpt lat="35.0" lon="132.0"/><trkpt lat="35.1" lon="132.1"/>
            </trkseg></trk></gpx>
            """.trimIndent()
        val result = GpsJoystickMigrator.parse(gpx.toByteArray())
        assertTrue(result.isSuccess)
        val m = result.getOrThrow()
        assertTrue(m.favorites.isEmpty())
        assertRoutes(m, expectedCount = 1, totalWaypoints = 2)
        assertEquals("Morning walk", m.routes[0].name)
    }

    @Test
    fun `GPS Joystick db files do not contain speed profiles`() {
        listOf(
            "gpsjoystick_20260508222509.db",
            "gpsjoystick_20250901192919.db",
        ).forEach { fixture ->
            val m = GpsJoystickMigrator.parse(loadFixture(fixture)).getOrThrow()
            assertEquals("$fixture walkSpeed", null, m.walkSpeed)
            assertEquals("$fixture runSpeed", null, m.runSpeed)
            assertEquals("$fixture bikeSpeed", null, m.bikeSpeed)
        }
    }
}
