package com.locationjoystick.app

import android.content.Intent
import com.locationjoystick.core.model.GroupInvite

internal fun parseGroupInvite(intent: Intent): GroupInvite? {
    val uri = intent.data ?: return null
    if (uri.scheme != "locationjoystick" || uri.host != "group") return null
    val host = uri.getQueryParameter("host") ?: return null
    val portStr = uri.getQueryParameter("port") ?: return null
    val port = portStr.toIntOrNull() ?: return null
    val id = uri.getQueryParameter("id") ?: return null
    return GroupInvite(host = host, port = port, groupId = id)
}

/**
 * Canonical deep link format: https://locationjoystick.shrtcts.fr/?lat=LAT&lon=LON
 * Custom scheme equivalent:   locationjoystick://open?lat=LAT&lon=LON
 * Android geo URI:            geo:LAT,LON
 * Google Maps web/app links:  https://maps.google.com/maps?q=LAT,LON
 *                             https://www.google.com/maps/search/?api=1&query=LAT,LON
 *                             https://www.google.com/maps/@LAT,LON,Zoomz
 *                             google.navigation:q=LAT,LON
 *
 * These are caught so links from other apps (e.g. "view on map" buttons in games) can be
 * intercepted by this app when the user picks it from the share/open chooser.
 */
internal fun parseDeepLinkCoords(intent: Intent): Pair<Double, Double>? {
    val uri = intent.data ?: return null
    return parseCoordsFromUri(uri)
}

private fun parseCoordsFromUri(uri: android.net.Uri): Pair<Double, Double>? =
    when {
        uri.scheme == "geo" -> parseGeoUri(uri.schemeSpecificPart)
        uri.scheme == "google.navigation" -> parseLatLonString(uri.schemeSpecificPart.removePrefix("q="))
        isGoogleMapsUri(uri) -> parseGoogleMapsUri(uri)
        else -> parseQueryParameterCoords(uri)
    }

private val URL_REGEX = Regex("""https?://\S+""")

/**
 * Extracts the first http(s) URL out of free-form shared text (e.g. Google Maps "Share" button,
 * which sends `ACTION_SEND` text/plain containing a place name plus a maps URL).
 */
internal fun extractUrlFromText(text: String): String? = URL_REGEX.find(text)?.value

internal fun parseUrlCoords(url: String): Pair<Double, Double>? = parseCoordsFromUri(android.net.Uri.parse(url))

private fun isGoogleMapsUri(uri: android.net.Uri): Boolean {
    val host = uri.host ?: return false
    return host == "maps.google.com" ||
        (host == "www.google.com" && uri.path?.startsWith("/maps") == true)
}

/**
 * Handles the various Google Maps URL shapes: `?q=`, `?daddr=`, `?destination=`,
 * `?query=` (api=1 search/dir links), and the `/maps/@LAT,LON,Zoomz` path form.
 */
private fun parseGoogleMapsUri(uri: android.net.Uri): Pair<Double, Double>? {
    val queryParam =
        uri.getQueryParameter("q")
            ?: uri.getQueryParameter("daddr")
            ?: uri.getQueryParameter("destination")
            ?: uri.getQueryParameter("query")
    queryParam?.let { parseLatLonString(it) }?.let { return it }

    val atSegment = uri.pathSegments.firstOrNull { it.startsWith("@") } ?: return null
    return parseLatLonString(atSegment.removePrefix("@"))
}

/**
 * Parse a "LAT,LON" prefix out of a string, ignoring any trailing label/params
 * (e.g. "LAT,LON(Label)" or "LAT,LON,15z").
 */
private fun parseLatLonString(value: String): Pair<Double, Double>? {
    val parts = value.substringBefore("(").split(",")
    if (parts.size < 2) return null
    val lat = parts[0].toDoubleOrNull() ?: return null
    val lon = parts[1].toDoubleOrNull() ?: return null
    if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
    return lat to lon
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
 * Parse geo URI: LAT,LON or LAT,LON?q=LAT,LON(Label)
 *
 * Some apps emit `geo:0,0?q=LAT,LON(Label)` for "view on map"-style buttons — the base
 * coordinate is a placeholder, and the real target is in the `q` param. Prefer `q` when present.
 */
private fun parseGeoUri(schemeSpecificPart: String): Pair<Double, Double>? {
    val qIndex = schemeSpecificPart.indexOf("?q=")
    if (qIndex != -1) {
        parseLatLonString(schemeSpecificPart.substring(qIndex + "?q=".length))?.let { return it }
    }
    return parseLatLonString(schemeSpecificPart.substringBefore("?"))
}
