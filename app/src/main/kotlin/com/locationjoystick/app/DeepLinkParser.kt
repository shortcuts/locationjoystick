package com.locationjoystick.app

import android.content.Intent

/**
 * Canonical deep link format: https://locationjoystick.shrtcts.fr/?lat=LAT&lon=LON
 * Custom scheme equivalent:   locationjoystick://open?lat=LAT&lon=LON
 * Android geo URI:            geo:LAT,LON
 *
 * Query parameter format (?lat and ?lon) is used for HTTPS and custom schemes.
 * Geo URI format (geo:LAT,LON) is parsed from the scheme-specific part.
 */
internal fun parseDeepLinkCoords(intent: Intent): Pair<Double, Double>? {
    val uri = intent.data ?: return null

    return when (uri.scheme) {
        "geo" -> parseGeoUri(uri.schemeSpecificPart)
        else -> parseQueryParameterCoords(uri)
    }
}

/**
 * Parse query parameters: ?lat=LAT&lon=LON
 */
private fun parseQueryParameterCoords(uri: android.net.Uri): Pair<Double, Double>? {
    val lat = uri.getQueryParameter("lat")?.toDoubleOrNull() ?: return null
    val lon = uri.getQueryParameter("lon")?.toDoubleOrNull() ?: return null
    if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
    return lat to lon
}

/**
 * Parse geo URI: LAT,LON or LAT,LON?param=value
 * Extracts the LAT,LON portion, ignoring optional parameters.
 */
private fun parseGeoUri(schemeSpecificPart: String): Pair<Double, Double>? {
    // Remove optional parameters (e.g., "lat,lon?z=10" -> "lat,lon")
    val coords = schemeSpecificPart.substringBefore("?")
    val parts = coords.split(",")
    if (parts.size < 2) return null
    val lat = parts[0].toDoubleOrNull() ?: return null
    val lon = parts[1].toDoubleOrNull() ?: return null
    if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
    return lat to lon
}
