package com.locationjoystick.core.model

/** Geodetic datum a raster tile provider renders its map in. */
enum class MapCoordinateSystem {
    /** Standard GPS datum — what the app stores and what `LocationManager` receives. */
    WGS84,

    /** Chinese obfuscated datum used by Amap/Gaode and every mainland-China provider. */
    GCJ02,
}

/**
 * Raster base-map provider. Shared by every map view (main map, floating widget, favorites
 * picker, route creator) so a single Settings choice switches all of them together.
 *
 * [tileUrlTemplates] are XYZ templates in MapLibre `{z}/{x}/{y}` syntax; several entries mean
 * the provider shards across subdomains and MapLibre should round-robin between them.
 *
 * [minZoom]/[maxZoom] bound the zoom range the provider actually serves — the camera is clamped to
 * it so the user can never zoom into a blank map. [defaultCenter] is where a map opens when there
 * is no position yet; it should sit inside the provider's coverage area.
 */
enum class MapTileSource(
    val tileUrlTemplates: List<String>,
    val minZoom: Float,
    val maxZoom: Float,
    val coordinateSystem: MapCoordinateSystem,
    val defaultCenter: LatLng,
) {
    OSM(
        tileUrlTemplates = listOf("https://tile.openstreetmap.org/{z}/{x}/{y}.png"),
        minZoom = 0f,
        maxZoom = 19f,
        coordinateSystem = MapCoordinateSystem.WGS84,
        // Paris — the app's historical default.
        defaultCenter = LatLng(latitude = 48.8566, longitude = 2.3522),
    ),

    /**
     * Amap/Gaode road map (style 8 — Chinese labels). Community-known unofficial raster endpoint,
     * no API key; fast and reliable from mainland China where OSM tiles are throttled.
     *
     * Coverage is mainland China (plus coarse surroundings) at zoom 3–18 only — outside that the
     * server returns empty tiles, which is why the range is clamped and the default center is Beijing.
     */
    AMAP(
        tileUrlTemplates =
            (1..4).map { shard ->
                "https://webrd0$shard.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}"
            },
        minZoom = 3f,
        maxZoom = 18f,
        coordinateSystem = MapCoordinateSystem.GCJ02,
        // Beijing, centre of Tian'anmen Square (Monument to the People's Heroes), WGS-84.
        defaultCenter = LatLng(latitude = 39.9033, longitude = 116.3915),
    ),
    ;

    companion object {
        val DEFAULT = OSM

        /** Lenient parse for persisted/imported values — unknown names fall back to [DEFAULT]. */
        fun fromName(name: String?): MapTileSource = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
