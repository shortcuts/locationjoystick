package com.locationjoystick.core.map.maplibre

import com.locationjoystick.core.common.constants.AppConstants

/**
 * Canonical MapLibre source ID constants. Single source of truth — replaces the
 * 4 sets of `private const val` scattered across screen files.
 */
object MapLibreSourceIds {
    val OSM: String = AppConstants.MapConstants.OSM_SOURCE_ID
    const val POSITION = "position-source"
    const val TRACE_TRACED = "traced-source"
    const val TRACE_REMAINING = "remaining-source"
    const val ENDPOINTS = "walk-endpoints-source"
    const val ROUTE_SEGMENTS = "segments-source"
    const val ROUTE_WAYPOINTS = "waypoints-source"
    const val MARKER = "marker-source"
    const val CURRENT_POS = "current-pos-source"
    const val SEARCH_MARKER = "search-marker-source"
    val EPHEMERAL_ROUTE: String = AppConstants.MapConstants.EPHEMERAL_ROUTE_SOURCE_ID
    val EPHEMERAL_ENDPOINTS: String = AppConstants.MapConstants.EPHEMERAL_ENDPOINTS_SOURCE_ID
}

/**
 * Canonical MapLibre layer ID constants. Mirrors [MapLibreSourceIds].
 */
object MapLibreLayerIds {
    val OSM: String = AppConstants.MapConstants.OSM_LAYER_ID
    const val POSITION = "position-layer"
    const val TRACE_TRACED = "traced-layer"
    const val TRACE_REMAINING = "remaining-layer"
    const val ENDPOINTS = "walk-endpoints-layer"
    const val ROUTE_SEGMENTS = "segments-layer"
    const val ROUTE_WAYPOINTS = "waypoints-layer"
    const val MARKER = "marker-layer"
    const val CURRENT_POS = "current-pos-layer"
    const val SEARCH_MARKER = "search-marker-layer"
    val EPHEMERAL_ROUTE: String = AppConstants.MapConstants.EPHEMERAL_ROUTE_LAYER_ID
    val EPHEMERAL_ENDPOINTS: String = AppConstants.MapConstants.EPHEMERAL_ENDPOINTS_LAYER_ID
}
