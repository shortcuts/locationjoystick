package com.locationjoystick.feature.settings.impl

import com.locationjoystick.core.common.constants.AppConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class YamlaMigratorTest {
    @Test
    fun `parses speeds from real file`() {
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
    fun `parses populated favorites`() {
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
    fun `returns no routes`() {
        val json = """{"e":2.0,"w":[]}"""
        val result = YamlaMigrator.parse(json)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.routes.isEmpty())
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
    fun `returns failure on malformed JSON`() {
        val result = YamlaMigrator.parse("{not-valid-json")
        assertTrue(result.isFailure)
    }

    @Test
    fun `returns failure on empty string`() {
        val result = YamlaMigrator.parse("")
        assertTrue(result.isFailure)
    }

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
