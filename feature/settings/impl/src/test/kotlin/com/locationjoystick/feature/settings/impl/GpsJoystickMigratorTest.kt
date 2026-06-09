package com.locationjoystick.feature.settings.impl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Unit tests for [GpsJoystickMigrator] using anonymized real .db fixtures.
 *
 * Coordinates and names in the fixtures have been randomized to avoid data leaks.
 * Structural counts (favorites, routes, waypoints) are preserved from the originals.
 *
 * Expected values per fixture (derived from original files):
 *   20250809 — 3 favs (default names), 7 routes, 253 total waypoints (36 each + 37 last)
 *   20250812 — 3 favs (default names), 7 routes, 222 total waypoints (31 each + 37 last)
 *   20250901 — 4 favs (default names), 6 routes, 222 total waypoints (37 each)
 *   20251004 — 4 favs (default names), 9 routes, 255 total waypoints (28 each + 27 last)
 *   20251008 — 4 favs (default names), 9 routes, 7 total waypoints
 *   20251008-1 — 4 favs (default names), 9 routes, 7 total waypoints (duplicate variant)
 *   20260508 — 8 named favs, 9 routes, 37 total waypoints
 *   20260520 — 4 favs (default names), 8 routes, 38 total waypoints
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
        assertTrue("$label should not be zero", value != 0.0)
        assertTrue("$label lat/lon should be plausible (abs <= 180)", Math.abs(value) <= 180.0)
    }

    private fun assertFavorites(
        migration: MigrationResult,
        expectedCount: Int,
        named: Boolean,
    ) {
        assertEquals("favorites count", expectedCount, migration.favorites.size)
        migration.favorites.forEachIndexed { i, fav ->
            assertTrue("fav[$i].name should not be blank", fav.name.isNotBlank())
            if (!named) {
                assertTrue(
                    "fav[$i].name should be default 'Favorite N' pattern",
                    fav.name.matches(Regex("Favorite \\d+")),
                )
            }
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
    fun `20250809 - 3 default-named favs and 7 routes with 253 waypoints`() {
        val result = GpsJoystickMigrator.parse(loadFixture("gpsjoystick_20250809211402.db"))
        assertTrue(result.isSuccess)
        val m = result.getOrThrow()
        assertFavorites(m, expectedCount = 3, named = false)
        assertRoutes(m, expectedCount = 7, totalWaypoints = 253)
    }

    @Test
    fun `20250812 - 3 default-named favs and 7 routes with 222 waypoints`() {
        val result = GpsJoystickMigrator.parse(loadFixture("gpsjoystick_20250812234223.db"))
        assertTrue(result.isSuccess)
        val m = result.getOrThrow()
        assertFavorites(m, expectedCount = 3, named = false)
        assertRoutes(m, expectedCount = 7, totalWaypoints = 222)
    }

    @Test
    fun `20250901 - 4 default-named favs and 6 routes with 222 waypoints`() {
        val result = GpsJoystickMigrator.parse(loadFixture("gpsjoystick_20250901192919.db"))
        assertTrue(result.isSuccess)
        val m = result.getOrThrow()
        assertFavorites(m, expectedCount = 4, named = false)
        assertRoutes(m, expectedCount = 6, totalWaypoints = 222)
    }

    @Test
    fun `20251004 - 4 default-named favs and 9 routes with 255 waypoints`() {
        val result = GpsJoystickMigrator.parse(loadFixture("gpsjoystick_20251004095615.db"))
        assertTrue(result.isSuccess)
        val m = result.getOrThrow()
        assertFavorites(m, expectedCount = 4, named = false)
        assertRoutes(m, expectedCount = 9, totalWaypoints = 255)
    }

    @Test
    fun `20251008 - 4 default-named favs and 1 fallback route with 7 waypoints`() {
        val result = GpsJoystickMigrator.parse(loadFixture("gpsjoystick_20251008134939.db"))
        assertTrue(result.isSuccess)
        val m = result.getOrThrow()
        assertFavorites(m, expectedCount = 4, named = false)
        assertRoutes(m, expectedCount = 1, totalWaypoints = 7)
    }

    @Test
    fun `20251008-1 - duplicate variant same expectations`() {
        val result = GpsJoystickMigrator.parse(loadFixture("gpsjoystick_20251008134939-1.db"))
        assertTrue(result.isSuccess)
        val m = result.getOrThrow()
        assertFavorites(m, expectedCount = 4, named = false)
        assertRoutes(m, expectedCount = 1, totalWaypoints = 7)
    }

    @Test
    fun `20260508 - 8 named favs and 9 routes with 37 waypoints`() {
        val result = GpsJoystickMigrator.parse(loadFixture("gpsjoystick_20260508222509.db"))
        assertTrue(result.isSuccess)
        val m = result.getOrThrow()
        assertFavorites(m, expectedCount = 8, named = true)
        assertRoutes(m, expectedCount = 9, totalWaypoints = 37)
    }

    @Test
    fun `20260520 - 4 default-named favs and 8 routes with 38 waypoints`() {
        val result = GpsJoystickMigrator.parse(loadFixture("gpsjoystick_20260520131222.db"))
        assertTrue(result.isSuccess)
        val m = result.getOrThrow()
        assertFavorites(m, expectedCount = 4, named = false)
        assertRoutes(m, expectedCount = 8, totalWaypoints = 38)
    }

    // ── synthetic binary tests (branch coverage) ─────────────────────────────

    /** Minimal valid Realm header (T-DB at offset 16) with optional appended blocks. */
    private fun buildRealm(vararg blocks: ByteArray): ByteArray {
        val header = ByteArray(20)
        header[16] = 'T'.code.toByte()
        header[17] = '-'.code.toByte()
        header[18] = 'D'.code.toByte()
        header[19] = 'B'.code.toByte()
        return blocks.fold(header) { acc, b -> acc + b }
    }

    /** Double array block in Realm binary format (0x0C type). */
    private fun doubleBlock(vararg values: Double): ByteArray {
        val data = ByteArray(values.size * 8)
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { buf.putDouble(it) }
        return byteArrayOf(0x41, 0x41, 0x41, 0x41, 0x0C, 0, 0, values.size.toByte()) + data
    }

    /** String array block in Realm binary format (0x0D type), each entry padded to 16 bytes. */
    private fun stringBlock(vararg names: String): ByteArray {
        val header = byteArrayOf(0x41, 0x41, 0x41, 0x41, 0x0D, 0, 0, names.size.toByte())
        val body = mutableListOf<Byte>()
        var consumed = 0
        names.forEach { name ->
            val nb = name.toByteArray(Charsets.UTF_8)
            nb.forEach { body.add(it) }
            body.add(0)
            consumed += nb.size + 1
            val rem = consumed % 16
            if (rem != 0) {
                repeat(16 - rem) { body.add(0) }
                consumed += 16 - rem
            }
        }
        return header + body.toByteArray()
    }

    @Test
    fun `valid realm header with no coord data returns empty migration`() {
        val result = GpsJoystickMigrator.parse(buildRealm())
        assertTrue(result.isSuccess)
        val m = result.getOrThrow()
        assertTrue(m.favorites.isEmpty())
        assertTrue(m.routes.isEmpty())
    }

    @Test
    fun `single coord pair with matching name array produces favorites only`() {
        // count=1 string array + count=1 lat array + count=1 lon array
        // hasMatchingNameArray=true → favPair set, routePair=null
        val bytes = buildRealm(
            stringBlock("Paris"),
            doubleBlock(48.8566), // lat
            doubleBlock(2.3522),  // lon
        )
        val result = GpsJoystickMigrator.parse(bytes)
        assertTrue(result.isSuccess)
        val m = result.getOrThrow()
        assertEquals(1, m.favorites.size)
        assertEquals("Paris", m.favorites[0].name)
        assertTrue(m.routes.isEmpty())
    }

    @Test
    fun `single coord pair with no matching name array produces routes only`() {
        // count=2 lat + count=2 lon, no string array → hasMatchingNameArray=false
        // favPair=null, routePair set
        val bytes = buildRealm(
            doubleBlock(48.8566, 48.9000), // lats
            doubleBlock(2.3522, 2.4000),   // lons
        )
        val result = GpsJoystickMigrator.parse(bytes)
        assertTrue(result.isSuccess)
        val m = result.getOrThrow()
        assertTrue(m.favorites.isEmpty())
        assertEquals(1, m.routes.size)
        assertEquals(2, m.routes[0].waypoints.size)
    }

    // ── speed profiles ────────────────────────────────────────────────────────

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
