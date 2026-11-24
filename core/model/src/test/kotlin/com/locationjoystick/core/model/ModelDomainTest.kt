package com.locationjoystick.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelDomainTest {
    @Test
    fun `LatLng with extreme coordinates works`() {
        val northPole = LatLng(90.0, 0.0)
        val southPole = LatLng(-90.0, 0.0)
        val dateLine = LatLng(0.0, 180.0)

        assertTrue(northPole.distanceTo(southPole) > 0)
        assertTrue(northPole.distanceTo(dateLine) > 0)
    }

    @Test
    fun `LatLng bearingTo at poles is valid`() {
        val northPole = LatLng(89.9, 0.0)
        val other = LatLng(89.8, 1.0)
        val bearing = northPole.bearingTo(other)
        assertTrue("bearing should be valid 0-360", bearing >= 0.0 && bearing < 360.0)
    }

    @Test
    fun `RoamingDefaults planting speed defaults to bike`() {
        assertEquals("bike", RoamingDefaults().plantingSpeedProfileId)
        assertEquals("walk", RoamingDefaults().speedProfileId)
        assertEquals("walk", RoamingDefaults().speedProfileIdForKind())
        assertEquals(
            "bike",
            RoamingDefaults(kind = RoamingKind.PLANTING).speedProfileIdForKind(),
        )
    }

    @Test
    fun `AppFeature surfaces and default enabled sets are correct`() {
        assertEquals(setOf(FeatureSurface.WIDGET, FeatureSurface.MAP), AppFeature.PASTE_COORDINATES.surfaces)
        assertEquals(setOf(FeatureSurface.WIDGET, FeatureSurface.MAP), AppFeature.ROAMING.surfaces)
        assertEquals(setOf(FeatureSurface.WIDGET, FeatureSurface.MAP), AppFeature.CAPTURE_COORDINATES.surfaces)
        assertFalse(AppFeature.DEFAULT_MAP_ENABLED.contains(AppFeature.PASTE_COORDINATES))
        assertTrue(AppFeature.DEFAULT_MAP_ENABLED.contains(AppFeature.CAPTURE_COORDINATES))
        assertFalse(AppFeature.DEFAULT_WIDGET_ENABLED.contains(AppFeature.PASTE_COORDINATES))
        assertTrue(AppFeature.DEFAULT_WIDGET_ENABLED.contains(AppFeature.ROAMING))
        assertFalse(AppFeature.DEFAULT_WIDGET_ENABLED.contains(AppFeature.CAPTURE_COORDINATES))
        assertEquals(setOf(FeatureSurface.MAP), AppFeature.DRAW_LINE.surfaces)
        assertFalse(AppFeature.DEFAULT_MAP_ENABLED.contains(AppFeature.DRAW_LINE))
        assertFalse(AppFeature.DEFAULT_WIDGET_ENABLED.contains(AppFeature.DRAW_LINE))
        assertTrue(AppFeature.DEFAULT_MAP_ENABLED.contains(AppFeature.SEARCH))
    }

    // RoamingDefaults.toConfig

    @Test
    fun `toConfig maps followRoads to useRoadSnapping`() {
        val defaults = RoamingDefaults(followRoads = true)
        val config = defaults.toConfig(LatLng(35.0, 139.0))
        assertTrue(config.useRoadSnapping)
    }

    @Test
    fun `toConfig maps followRoads false to useRoadSnapping false`() {
        val defaults = RoamingDefaults(followRoads = false)
        val config = defaults.toConfig(LatLng(35.0, 139.0))
        assertFalse(config.useRoadSnapping)
    }

    @Test
    fun `toConfig copies all fields from defaults`() {
        val center = LatLng(35.6762, 139.6503)
        val defaults =
            RoamingDefaults(
                radiusMeters = 2000.0,
                distanceMeters = 500.0,
                speedProfileId = "bike",
                followRoads = false,
                returnToInitialLocation = true,
            )
        val config = defaults.toConfig(center)
        assertEquals(center, config.centerPosition)
        assertEquals(2000.0, config.radiusMeters, 0.001)
        assertEquals(500.0, config.distanceMeters, 0.001)
        assertEquals("bike", config.speedProfileId)
        assertFalse(config.useRoadSnapping)
        assertTrue(config.returnToInitialLocation)
        assertEquals(RoamingKind.WALK_AROUND, config.kind)
    }

    @Test
    fun `toConfig copies planting fields`() {
        val center = LatLng(35.6762, 139.6503)
        val defaults =
            RoamingDefaults(
                kind = RoamingKind.PLANTING,
                plantingStartRadiusMeters = 6.0,
                plantingEndRadiusMeters = 40.0,
                plantingInfiniteLoops = false,
                plantingLoopCount = 3,
            )
        val config = defaults.toConfig(center)
        assertEquals(RoamingKind.PLANTING, config.kind)
        assertEquals(6.0, config.plantingStartRadiusMeters, 0.001)
        assertEquals(40.0, config.plantingEndRadiusMeters, 0.001)
        assertFalse(config.plantingInfiniteLoops)
        assertEquals(3, config.plantingLoopCount)
        assertEquals("bike", config.speedProfileId)
    }

    @Test
    fun `toConfig planting uses plantingSpeedProfileId not walk-around speed`() {
        val defaults =
            RoamingDefaults(
                kind = RoamingKind.PLANTING,
                speedProfileId = "walk",
                plantingSpeedProfileId = "run",
            )
        val config = defaults.toConfig(LatLng(0.0, 0.0))
        assertEquals("run", config.speedProfileId)
    }

    @Test
    fun `RoamingKind parse falls back to walk around`() {
        assertEquals(RoamingKind.PLANTING, RoamingKind.parse("PLANTING"))
        assertEquals(RoamingKind.WALK_AROUND, RoamingKind.parse(null))
        assertEquals(RoamingKind.WALK_AROUND, RoamingKind.parse("nope"))
    }

    // sortedByAge

    @Test
    fun `sortedByAge newestFirst sorts descending by createdAt`() {
        val favorites =
            listOf(
                FavoriteLocation("1", "Old", LatLng(0.0, 0.0), 1000L),
                FavoriteLocation("2", "Newest", LatLng(0.0, 0.0), 3000L),
                FavoriteLocation("3", "Mid", LatLng(0.0, 0.0), 2000L),
            )
        val sorted = favorites.sortedByAge(newestFirst = true)
        assertEquals("Newest", sorted[0].name)
        assertEquals("Mid", sorted[1].name)
        assertEquals("Old", sorted[2].name)
    }

    @Test
    fun `sortedByAge oldestFirst sorts ascending by createdAt`() {
        val favorites =
            listOf(
                FavoriteLocation("1", "Old", LatLng(0.0, 0.0), 1000L),
                FavoriteLocation("2", "Newest", LatLng(0.0, 0.0), 3000L),
                FavoriteLocation("3", "Mid", LatLng(0.0, 0.0), 2000L),
            )
        val sorted = favorites.sortedByAge(newestFirst = false)
        assertEquals("Old", sorted[0].name)
        assertEquals("Mid", sorted[1].name)
        assertEquals("Newest", sorted[2].name)
    }
}
