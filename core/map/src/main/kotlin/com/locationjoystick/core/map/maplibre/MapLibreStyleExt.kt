package com.locationjoystick.core.map.maplibre

import androidx.compose.ui.graphics.toArgb
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.designsystem.LjMapColors
import com.locationjoystick.core.map.geojson.emptyGeoJson
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

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
    val searchMarkerSource: GeoJsonSource? = null,
    val pendingTapSource: GeoJsonSource? = null,
)

/**
 * Adds OSM raster tile layer, position dot, walk-trace, walk-remaining, walk-endpoints,
 * and optionally a search-marker circle layer.
 *
 * @param osmSourceId Raster source ID — use [MapLibreSourceIds.PANEL_OSM] for the widget overlay
 *                    to avoid colliding with the main map.
 * @param osmLayerId  Raster layer ID — use [MapLibreLayerIds.PANEL_OSM] for the widget overlay.
 * @param lineWidth   Line width for trace layers. Main map uses 4f; widget overlay uses 3f.
 * @param includeSearchMarker When true, adds a search-result marker layer and returns its source.
 */
fun Style.addLocationLayers(
    osmSourceId: String = MapLibreSourceIds.OSM,
    osmLayerId: String = MapLibreLayerIds.OSM,
    lineWidth: Float = 4f,
    includeSearchMarker: Boolean = false,
): LocationLayerSources {
    addSource(
        RasterSource(
            osmSourceId,
            TileSet(AppConstants.MapConstants.TILESET_VERSION, AppConstants.MapConstants.OSM_TILE_URL).apply {
                maxZoom = AppConstants.MapConstants.OSM_MAX_ZOOM
            },
            256,
        ),
    )
    addLayer(RasterLayer(osmLayerId, osmSourceId))

    val tracedSrc = GeoJsonSource(MapLibreSourceIds.TRACE_TRACED, emptyGeoJson())
    addSource(tracedSrc)
    addLayer(
        LineLayer(MapLibreLayerIds.TRACE_TRACED, MapLibreSourceIds.TRACE_TRACED)
            .withProperties(
                PropertyFactory.lineColor(LjMapColors.RouteOrange.toArgb()),
                PropertyFactory.lineWidth(lineWidth),
                PropertyFactory.lineDasharray(arrayOf(2f, 2f)),
            ),
    )

    val remainingSrc = GeoJsonSource(MapLibreSourceIds.TRACE_REMAINING, emptyGeoJson())
    addSource(remainingSrc)
    addLayer(
        LineLayer(MapLibreLayerIds.TRACE_REMAINING, MapLibreSourceIds.TRACE_REMAINING)
            .withProperties(
                PropertyFactory.lineColor(LjMapColors.RouteOrange.toArgb()),
                PropertyFactory.lineWidth(lineWidth),
            ),
    )

    val endpointsSrc = GeoJsonSource(MapLibreSourceIds.ENDPOINTS, emptyGeoJson())
    addSource(endpointsSrc)
    addLayer(
        CircleLayer(MapLibreLayerIds.ENDPOINTS, MapLibreSourceIds.ENDPOINTS)
            .withProperties(
                PropertyFactory.circleRadius(AppConstants.MapConstants.ROUTE_POINT_RADIUS),
                PropertyFactory.circleColor(LjMapColors.RouteOrange.toArgb()),
                PropertyFactory.circleStrokeColor(LjMapColors.PointStroke.toArgb()),
                PropertyFactory.circleStrokeWidth(AppConstants.MapConstants.POINT_STROKE_WIDTH),
            ),
    )

    // Position dot on top — added last so it renders above trace and endpoint layers
    val positionSrc = GeoJsonSource(MapLibreSourceIds.POSITION, emptyGeoJson())
    addSource(positionSrc)
    addLayer(
        CircleLayer(MapLibreLayerIds.POSITION, MapLibreSourceIds.POSITION)
            .withProperties(
                PropertyFactory.circleRadius(AppConstants.MapConstants.POSITION_DOT_RADIUS),
                PropertyFactory.circleColor(LjMapColors.PositionBlue.toArgb()),
                PropertyFactory.circleStrokeColor(LjMapColors.PointStroke.toArgb()),
                PropertyFactory.circleStrokeWidth(AppConstants.MapConstants.POINT_STROKE_WIDTH),
            ),
    )

    var searchMarkerSrc: GeoJsonSource? = null
    if (includeSearchMarker) {
        searchMarkerSrc = GeoJsonSource(MapLibreSourceIds.SEARCH_MARKER, emptyGeoJson())
        addSource(searchMarkerSrc)
        addLayer(
            CircleLayer(MapLibreLayerIds.SEARCH_MARKER, MapLibreSourceIds.SEARCH_MARKER)
                .withProperties(
                    PropertyFactory.circleRadius(AppConstants.MapConstants.ROUTE_POINT_RADIUS),
                    PropertyFactory.circleColor(LjMapColors.RouteOrange.toArgb()),
                    PropertyFactory.circleStrokeColor(LjMapColors.PointStroke.toArgb()),
                    PropertyFactory.circleStrokeWidth(AppConstants.MapConstants.POINT_STROKE_WIDTH),
                ),
        )
    }

    val pendingTapSrc = GeoJsonSource(MapLibreSourceIds.PENDING_TAP, emptyGeoJson())
    addSource(pendingTapSrc)
    addLayer(
        CircleLayer(MapLibreLayerIds.PENDING_TAP, MapLibreSourceIds.PENDING_TAP)
            .withProperties(
                PropertyFactory.circleRadius(AppConstants.MapConstants.ROUTE_POINT_RADIUS),
                PropertyFactory.circleColor(LjMapColors.RouteOrange.toArgb()),
                PropertyFactory.circleStrokeColor(LjMapColors.PointStroke.toArgb()),
                PropertyFactory.circleStrokeWidth(AppConstants.MapConstants.POINT_STROKE_WIDTH),
            ),
    )

    return LocationLayerSources(
        positionSource = positionSrc,
        tracedSource = tracedSrc,
        remainingSource = remainingSrc,
        endpointsSource = endpointsSrc,
        searchMarkerSource = searchMarkerSrc,
        pendingTapSource = pendingTapSrc,
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
 * Adds OSM tiles, optional current-position dot, and a tap-marker layer (MapPickerScreen).
 *
 * @param currentPosGeoJson If non-null, a blue dot is added at that GeoJSON position.
 */
fun Style.addPickerLayers(currentPosGeoJson: String? = null): PickerLayerSources {
    addSource(
        RasterSource(
            MapLibreSourceIds.OSM,
            TileSet(AppConstants.MapConstants.TILESET_VERSION, AppConstants.MapConstants.OSM_TILE_URL).apply {
                maxZoom = AppConstants.MapConstants.OSM_MAX_ZOOM
            },
            256,
        ),
    )
    addLayer(RasterLayer(MapLibreLayerIds.OSM, MapLibreSourceIds.OSM))

    var currentPosSrc: GeoJsonSource? = null
    if (currentPosGeoJson != null) {
        currentPosSrc = GeoJsonSource(MapLibreSourceIds.CURRENT_POS, currentPosGeoJson)
        addSource(currentPosSrc)
        addLayer(
            CircleLayer(MapLibreLayerIds.CURRENT_POS, MapLibreSourceIds.CURRENT_POS)
                .withProperties(
                    PropertyFactory.circleRadius(AppConstants.MapConstants.POSITION_DOT_RADIUS),
                    PropertyFactory.circleColor(LjMapColors.PositionBlue.toArgb()),
                    PropertyFactory.circleStrokeColor(LjMapColors.PointStroke.toArgb()),
                    PropertyFactory.circleStrokeWidth(AppConstants.MapConstants.POINT_STROKE_WIDTH),
                ),
        )
    }

    val markerSrc = GeoJsonSource(MapLibreSourceIds.MARKER, emptyGeoJson())
    addSource(markerSrc)
    addLayer(
        CircleLayer(MapLibreLayerIds.MARKER, MapLibreSourceIds.MARKER)
            .withProperties(
                PropertyFactory.circleRadius(AppConstants.MapConstants.ROUTE_POINT_RADIUS),
                PropertyFactory.circleColor(LjMapColors.RouteOrange.toArgb()),
                PropertyFactory.circleStrokeColor(LjMapColors.PointStroke.toArgb()),
                PropertyFactory.circleStrokeWidth(AppConstants.MapConstants.POINT_STROKE_WIDTH),
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
 * Adds OSM tiles, optional current-position dot, route segment lines, and waypoint circles
 * (RouteCreatorScreen).
 *
 * @param currentPosGeoJson If non-null, a blue dot is added at that GeoJSON position.
 */
fun Style.addCreatorLayers(currentPosGeoJson: String? = null): CreatorLayerSources {
    addSource(
        RasterSource(
            MapLibreSourceIds.OSM,
            TileSet(AppConstants.MapConstants.TILESET_VERSION, AppConstants.MapConstants.OSM_TILE_URL).apply {
                maxZoom = AppConstants.MapConstants.OSM_MAX_ZOOM
            },
            256,
        ),
    )
    addLayer(RasterLayer(MapLibreLayerIds.OSM, MapLibreSourceIds.OSM))

    var currentPosSrc: GeoJsonSource? = null
    if (currentPosGeoJson != null) {
        currentPosSrc = GeoJsonSource(MapLibreSourceIds.CURRENT_POS, currentPosGeoJson)
        addSource(currentPosSrc)
        addLayer(
            CircleLayer(MapLibreLayerIds.CURRENT_POS, MapLibreSourceIds.CURRENT_POS)
                .withProperties(
                    PropertyFactory.circleRadius(AppConstants.MapConstants.POSITION_DOT_RADIUS),
                    PropertyFactory.circleColor(LjMapColors.PositionBlue.toArgb()),
                    PropertyFactory.circleStrokeColor(LjMapColors.PointStroke.toArgb()),
                    PropertyFactory.circleStrokeWidth(AppConstants.MapConstants.POINT_STROKE_WIDTH),
                ),
        )
    }

    val segSrc = GeoJsonSource(MapLibreSourceIds.ROUTE_SEGMENTS, emptyGeoJson())
    addSource(segSrc)
    addLayer(
        LineLayer(MapLibreLayerIds.ROUTE_SEGMENTS, MapLibreSourceIds.ROUTE_SEGMENTS)
            .withProperties(
                PropertyFactory.lineColor(LjMapColors.RouteOrange.toArgb()),
                PropertyFactory.lineWidth(3f),
            ),
    )

    val wpSrc = GeoJsonSource(MapLibreSourceIds.ROUTE_WAYPOINTS, emptyGeoJson())
    addSource(wpSrc)
    addLayer(
        CircleLayer(MapLibreLayerIds.ROUTE_WAYPOINTS, MapLibreSourceIds.ROUTE_WAYPOINTS)
            .withProperties(
                PropertyFactory.circleRadius(AppConstants.MapConstants.ROUTE_POINT_RADIUS),
                PropertyFactory.circleColor(LjMapColors.RouteOrange.toArgb()),
                PropertyFactory.circleStrokeColor(LjMapColors.PointStroke.toArgb()),
                PropertyFactory.circleStrokeWidth(AppConstants.MapConstants.POINT_STROKE_WIDTH),
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
                PropertyFactory.lineColor(LjMapColors.RouteOrange.toArgb()),
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
                PropertyFactory.circleRadius(AppConstants.MapConstants.ROUTE_POINT_RADIUS),
                PropertyFactory.circleColor(LjMapColors.RouteOrange.toArgb()),
                PropertyFactory.circleStrokeColor(LjMapColors.PointStroke.toArgb()),
                PropertyFactory.circleStrokeWidth(AppConstants.MapConstants.POINT_STROKE_WIDTH),
            ),
    )

    return EphemeralRouteLayerSources(routeSource = routeSrc, endpointsSource = endpointsSrc)
}
