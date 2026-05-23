package com.locationjoystick.core.map.maplibre

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.map.geojson.emptyGeoJson
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

// Canonical colours
private val COLOR_EPHEMERAL = Color(0xFF7B61FF).toArgb()
private val COLOR_POSITION = Color(0xFF1E88E5).toArgb()
private val COLOR_POSITION_STROKE = Color(0xFFFFFFFF).toArgb()
private val COLOR_ROUTE_LINE = Color(0xFFFF9800).toArgb()
private val COLOR_CURRENT_POS_CIRCLE = Color(0xFF1976D2).toArgb()
private val COLOR_WAYPOINT = Color(0xFF4CAF50).toArgb()
private val COLOR_SEGMENT = Color(0xFF2196F3).toArgb()
private val COLOR_MARKER = Color(0xFFFF5722).toArgb()

/**
 * Adds the OSM raster tile layer to the style.
 */
fun Style.Builder.addOsmRasterLayer(): Style.Builder {
    withSource(
        RasterSource(
            MapLibreSourceIds.OSM,
            TileSet(AppConstants.MapConstants.TILESET_VERSION, AppConstants.MapConstants.OSM_TILE_URL).apply {
                maxZoom =
                    AppConstants.MapConstants.OSM_MAX_ZOOM
            },
            256,
        ),
    )
    withLayer(RasterLayer(MapLibreLayerIds.OSM, MapLibreSourceIds.OSM))
    return this
}

/**
 * Data class holding the GeoJSON sources added by [Style.addLocationLayers].
 * Callers store these to update GeoJSON at runtime.
 */
data class LocationLayerSources(
    val positionSource: GeoJsonSource,
    val tracedSource: GeoJsonSource,
    val remainingSource: GeoJsonSource,
    val endpointsSource: GeoJsonSource,
)

/**
 * Adds position dot, walk-trace, walk-remaining, and walk-endpoints layers.
 * Position dot is added last so it renders on top of all other layers.
 */
fun Style.addLocationLayers(): LocationLayerSources {
    val tracedSrc = GeoJsonSource(MapLibreSourceIds.TRACE_TRACED, emptyGeoJson())
    addSource(tracedSrc)
    addLayer(
        LineLayer(MapLibreLayerIds.TRACE_TRACED, MapLibreSourceIds.TRACE_TRACED)
            .withProperties(
                PropertyFactory.lineColor(COLOR_ROUTE_LINE),
                PropertyFactory.lineWidth(4f),
                PropertyFactory.lineDasharray(arrayOf(2f, 2f)),
            ),
    )

    val remainingSrc = GeoJsonSource(MapLibreSourceIds.TRACE_REMAINING, emptyGeoJson())
    addSource(remainingSrc)
    addLayer(
        LineLayer(MapLibreLayerIds.TRACE_REMAINING, MapLibreSourceIds.TRACE_REMAINING)
            .withProperties(
                PropertyFactory.lineColor(COLOR_ROUTE_LINE),
                PropertyFactory.lineWidth(4f),
            ),
    )

    val endpointsSrc = GeoJsonSource(MapLibreSourceIds.ENDPOINTS, emptyGeoJson())
    addSource(endpointsSrc)
    addLayer(
        CircleLayer(MapLibreLayerIds.ENDPOINTS, MapLibreSourceIds.ENDPOINTS)
            .withProperties(
                PropertyFactory.circleRadius(8f),
                PropertyFactory.circleColor(COLOR_POSITION),
                PropertyFactory.circleStrokeColor(COLOR_POSITION_STROKE),
                PropertyFactory.circleStrokeWidth(2f),
            ),
    )

    // Position dot on top — added last
    val positionSrc = GeoJsonSource(MapLibreSourceIds.POSITION, emptyGeoJson())
    addSource(positionSrc)
    addLayer(
        CircleLayer(MapLibreLayerIds.POSITION, MapLibreSourceIds.POSITION)
            .withProperties(
                PropertyFactory.circleRadius(10f),
                PropertyFactory.circleColor(COLOR_POSITION),
                PropertyFactory.circleStrokeColor(COLOR_POSITION_STROKE),
                PropertyFactory.circleStrokeWidth(2f),
            ),
    )

    return LocationLayerSources(
        positionSource = positionSrc,
        tracedSource = tracedSrc,
        remainingSource = remainingSrc,
        endpointsSource = endpointsSrc,
    )
}

/**
 * Data class holding sources added by [Style.addPickerLayers].
 */
data class PickerLayerSources(
    val currentPosSource: GeoJsonSource?,
    val markerSource: GeoJsonSource,
)

/**
 * Adds optional current-position dot and a tap-marker layer (MapPickerScreen).
 *
 * @param currentPosGeoJson If non-null, a blue dot is added at that GeoJSON position.
 */
fun Style.addPickerLayers(currentPosGeoJson: String? = null): PickerLayerSources {
    var currentPosSrc: GeoJsonSource? = null
    if (currentPosGeoJson != null) {
        currentPosSrc = GeoJsonSource(MapLibreSourceIds.CURRENT_POS, currentPosGeoJson)
        addSource(currentPosSrc)
        addLayer(
            CircleLayer(MapLibreLayerIds.CURRENT_POS, MapLibreSourceIds.CURRENT_POS)
                .withProperties(
                    PropertyFactory.circleRadius(9f),
                    PropertyFactory.circleColor(COLOR_CURRENT_POS_CIRCLE),
                    PropertyFactory.circleStrokeColor(COLOR_POSITION_STROKE),
                    PropertyFactory.circleStrokeWidth(2f),
                ),
        )
    }

    val markerSrc = GeoJsonSource(MapLibreSourceIds.MARKER, emptyGeoJson())
    addSource(markerSrc)
    addLayer(
        CircleLayer(MapLibreLayerIds.MARKER, MapLibreSourceIds.MARKER)
            .withProperties(
                PropertyFactory.circleRadius(10f),
                PropertyFactory.circleColor(COLOR_MARKER),
                PropertyFactory.circleStrokeColor(COLOR_POSITION_STROKE),
                PropertyFactory.circleStrokeWidth(2f),
            ),
    )

    return PickerLayerSources(
        currentPosSource = currentPosSrc,
        markerSource = markerSrc,
    )
}

/**
 * Data class holding sources added by [Style.addCreatorLayers].
 */
data class CreatorLayerSources(
    val currentPosSource: GeoJsonSource?,
    val segmentsSource: GeoJsonSource,
    val waypointsSource: GeoJsonSource,
)

/**
 * Adds optional current-position dot, route segment lines, and waypoint circles
 * (RouteCreatorScreen).
 *
 * @param currentPosGeoJson If non-null, a blue dot is added at that GeoJSON position.
 */
fun Style.addCreatorLayers(currentPosGeoJson: String? = null): CreatorLayerSources {
    var currentPosSrc: GeoJsonSource? = null
    if (currentPosGeoJson != null) {
        currentPosSrc = GeoJsonSource(MapLibreSourceIds.CURRENT_POS, currentPosGeoJson)
        addSource(currentPosSrc)
        addLayer(
            CircleLayer(MapLibreLayerIds.CURRENT_POS, MapLibreSourceIds.CURRENT_POS)
                .withProperties(
                    PropertyFactory.circleRadius(9f),
                    PropertyFactory.circleColor(COLOR_CURRENT_POS_CIRCLE),
                    PropertyFactory.circleStrokeColor(COLOR_POSITION_STROKE),
                    PropertyFactory.circleStrokeWidth(2f),
                ),
        )
    }

    val segSrc = GeoJsonSource(MapLibreSourceIds.ROUTE_SEGMENTS, emptyGeoJson())
    addSource(segSrc)
    addLayer(
        LineLayer(MapLibreLayerIds.ROUTE_SEGMENTS, MapLibreSourceIds.ROUTE_SEGMENTS)
            .withProperties(
                PropertyFactory.lineColor(COLOR_SEGMENT),
                PropertyFactory.lineWidth(3f),
            ),
    )

    val wpSrc = GeoJsonSource(MapLibreSourceIds.ROUTE_WAYPOINTS, emptyGeoJson())
    addSource(wpSrc)
    addLayer(
        CircleLayer(MapLibreLayerIds.ROUTE_WAYPOINTS, MapLibreSourceIds.ROUTE_WAYPOINTS)
            .withProperties(
                PropertyFactory.circleRadius(8f),
                PropertyFactory.circleColor(COLOR_WAYPOINT),
                PropertyFactory.circleStrokeColor(COLOR_POSITION_STROKE),
                PropertyFactory.circleStrokeWidth(2f),
            ),
    )

    return CreatorLayerSources(
        currentPosSource = currentPosSrc,
        segmentsSource = segSrc,
        waypointsSource = wpSrc,
    )
}

/**
 * Data class holding sources added by [Style.addEphemeralRouteLayers].
 */
data class EphemeralRouteLayerSources(
    val routeSource: GeoJsonSource,
    val endpointsSource: GeoJsonSource,
)

/**
 * Adds the ephemeral-route dashed line and its endpoint circles.
 * The line is inserted below [MapLibreLayerIds.TRACE_TRACED] so traced segments
 * always render on top.
 */
fun Style.addEphemeralRouteLayers(): EphemeralRouteLayerSources {
    val routeSrc = GeoJsonSource(MapLibreSourceIds.EPHEMERAL_ROUTE, emptyGeoJson())
    addSource(routeSrc)
    addLayerBelow(
        LineLayer(MapLibreLayerIds.EPHEMERAL_ROUTE, MapLibreSourceIds.EPHEMERAL_ROUTE)
            .withProperties(
                PropertyFactory.lineColor(COLOR_EPHEMERAL),
                PropertyFactory.lineWidth(3f),
                PropertyFactory.lineDasharray(arrayOf(4f, 4f)),
            ),
        MapLibreLayerIds.TRACE_TRACED,
    )

    val endpointsSrc = GeoJsonSource(MapLibreSourceIds.EPHEMERAL_ENDPOINTS, emptyGeoJson())
    addSource(endpointsSrc)
    addLayer(
        CircleLayer(MapLibreLayerIds.EPHEMERAL_ENDPOINTS, MapLibreSourceIds.EPHEMERAL_ENDPOINTS)
            .withProperties(
                PropertyFactory.circleRadius(6f),
                PropertyFactory.circleColor(COLOR_EPHEMERAL),
                PropertyFactory.circleStrokeColor(COLOR_POSITION_STROKE),
                PropertyFactory.circleStrokeWidth(2f),
            ),
    )

    return EphemeralRouteLayerSources(routeSource = routeSrc, endpointsSource = endpointsSrc)
}
