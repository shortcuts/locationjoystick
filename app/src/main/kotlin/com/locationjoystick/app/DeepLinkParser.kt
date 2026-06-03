package com.locationjoystick.app

import android.content.Intent

/**
 * Canonical deep link format: https://locationjoystick.shrtcts.fr/?lat=LAT&lon=LON
 * Custom scheme equivalent:   locationjoystick://open?lat=LAT&lon=LON
 *
 * Both are handled by reading ?lat and ?lon query parameters.
 */
internal fun parseDeepLinkCoords(intent: Intent): Pair<Double, Double>? {
    val uri = intent.data ?: return null
    val lat = uri.getQueryParameter("lat")?.toDoubleOrNull() ?: return null
    val lon = uri.getQueryParameter("lon")?.toDoubleOrNull() ?: return null
    if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
    return lat to lon
}
