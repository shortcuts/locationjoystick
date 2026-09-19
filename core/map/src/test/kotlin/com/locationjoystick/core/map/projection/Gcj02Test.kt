package com.locationjoystick.core.map.projection

import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MapTileSource
import com.locationjoystick.core.model.distanceTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class Gcj02Test {
    // Reference pairs from published WGS-84 / GCJ-02 datasets (Amap picker vs. raw GPS). The public
    // reverse-engineered polynomial reproduces the official (secret) shift to within a few metres,
    // so reference comparisons use a 10 m tolerance; the round-trip test below is what pins down the
    // precision of *our* inverse.
    private val referenceToleranceMeters = 10.0
    private val tiananmenWgs = LatLng(39.907333, 116.391230)
    private val tiananmenGcj = LatLng(39.908745, 116.397499)
    private val shanghaiWgs = LatLng(31.230416, 121.473701)
    private val shanghaiGcj = LatLng(31.228469, 121.478153)

    private fun assertWithinMeters(
        expected: LatLng,
        actual: LatLng,
        meters: Double,
    ) {
        val d = expected.distanceTo(actual)
        assertTrue("expected within $meters m but was ${"%.2f".format(d)} m", d <= meters)
    }

    @Test
    fun `fromWgs84 matches reference offset in Beijing`() {
        assertWithinMeters(tiananmenGcj, Gcj02.fromWgs84(tiananmenWgs), referenceToleranceMeters)
    }

    @Test
    fun `fromWgs84 matches reference offset in Shanghai`() {
        assertWithinMeters(shanghaiGcj, Gcj02.fromWgs84(shanghaiWgs), referenceToleranceMeters)
    }

    @Test
    fun `offset magnitude is in the expected few-hundred-metre range`() {
        val shift = tiananmenWgs.distanceTo(Gcj02.fromWgs84(tiananmenWgs))
        assertTrue("shift was $shift m", shift in 300.0..800.0)
    }

    @Test
    fun `toWgs84 inverts fromWgs84 to sub-centimetre precision`() {
        listOf(tiananmenWgs, shanghaiWgs, LatLng(22.543096, 114.057865), LatLng(45.75, 126.65)).forEach { wgs ->
            val roundTrip = Gcj02.toWgs84(Gcj02.fromWgs84(wgs))
            assertWithinMeters(wgs, roundTrip, 0.01)
        }
    }

    @Test
    fun `toWgs84 matches reference for a known GCJ point`() {
        assertWithinMeters(tiananmenWgs, Gcj02.toWgs84(tiananmenGcj), referenceToleranceMeters)
    }

    @Test
    fun `points outside China pass through unchanged`() {
        val paris = LatLng(48.8566, 2.3522)
        val tokyo = LatLng(35.6762, 139.6503)
        assertEquals(paris, Gcj02.fromWgs84(paris))
        assertEquals(paris, Gcj02.toWgs84(paris))
        assertEquals(tokyo, Gcj02.fromWgs84(tokyo))
        assertEquals(tokyo, Gcj02.toWgs84(tokyo))
    }

    @Test
    fun `projection selection follows the tile source datum`() {
        assertSame(MapProjection.Identity, MapTileSource.OSM.projection)
        assertSame(MapProjection.Gcj02Projection, MapTileSource.AMAP.projection)
    }

    @Test
    fun `identity projection returns the same list instance`() {
        val pts = listOf(tiananmenWgs, shanghaiWgs)
        assertSame(pts, MapProjection.Identity.toMap(pts))
    }

    @Test
    fun `gcj projection maps lists element-wise`() {
        val pts = listOf(tiananmenWgs, shanghaiWgs)
        val mapped = MapProjection.Gcj02Projection.toMap(pts)
        assertEquals(2, mapped.size)
        assertWithinMeters(tiananmenGcj, mapped[0], referenceToleranceMeters)
        assertWithinMeters(shanghaiGcj, mapped[1], referenceToleranceMeters)
    }

    @Test
    fun `fromName is lenient`() {
        assertEquals(MapTileSource.AMAP, MapTileSource.fromName("AMAP"))
        assertEquals(MapTileSource.OSM, MapTileSource.fromName("bogus"))
        assertEquals(MapTileSource.OSM, MapTileSource.fromName(null))
    }

    @Test
    fun `amap shards across four subdomains with xyz placeholders`() {
        val urls = MapTileSource.AMAP.tileUrlTemplates
        assertEquals(4, urls.size)
        urls.forEach { url ->
            assertTrue(url.contains("{x}") && url.contains("{y}") && url.contains("{z}"))
        }
        assertEquals(4, urls.toSet().size)
    }
}
