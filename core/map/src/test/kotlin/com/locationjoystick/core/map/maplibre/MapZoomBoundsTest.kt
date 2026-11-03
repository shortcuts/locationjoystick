package com.locationjoystick.core.map.maplibre

import com.locationjoystick.core.model.MapTileSource
import org.junit.Assert.assertEquals
import org.junit.Test
import org.maplibre.android.constants.MapLibreConstants

class MapZoomBoundsTest {
    @Test
    fun `OSM keeps MapLibre default camera range`() {
        val bounds = cameraZoomBounds(MapTileSource.OSM)
        assertEquals(MapLibreConstants.MINIMUM_ZOOM.toFloat(), bounds.start, 0f)
        assertEquals(MapLibreConstants.MAXIMUM_ZOOM.toFloat(), bounds.endInclusive, 0f)
    }

    @Test
    fun `OSM tile range is still capped at 19`() {
        assertEquals(19f, MapTileSource.OSM.maxZoom, 0f)
    }

    @Test
    fun `AMAP camera clamped to served tile range`() {
        assertEquals(3f..18f, cameraZoomBounds(MapTileSource.AMAP))
    }

    @Test
    fun `SATELLITE keeps MapLibre default camera range and serves tiles to z14`() {
        val bounds = cameraZoomBounds(MapTileSource.SATELLITE)
        assertEquals(MapLibreConstants.MINIMUM_ZOOM.toFloat(), bounds.start, 0f)
        assertEquals(MapLibreConstants.MAXIMUM_ZOOM.toFloat(), bounds.endInclusive, 0f)
        assertEquals(14f, MapTileSource.SATELLITE.maxZoom, 0f)
    }
}
