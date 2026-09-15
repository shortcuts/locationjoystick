package com.locationjoystick.core.common.util

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.LatLng
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import javax.xml.parsers.DocumentBuilderFactory

/** One parsed `<trk>` or `<rte>` element, with its own name and points — never merged across elements. */
data class GpxImportedRoute(
    val name: String,
    val waypoints: List<LatLng>,
)

/**
 * Parses every `<trk>` and `<rte>` element in [gpxContent] into its own [GpxImportedRoute] — a GPX
 * file (e.g. from the GPS Joystick app) commonly bundles multiple distinct routes, and merging them
 * into a single route silently discards that structure (see issue #21).
 *
 * Unnamed segments use [unnamedFallback], numbered when the file has more than one segment.
 */
fun parseGpxRoutes(
    gpxContent: String,
    unnamedFallback: String = "Imported Route",
): List<GpxImportedRoute> {
    val doc =
        DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .parse(gpxContent.byteInputStream())
    val segments = mutableListOf<Pair<String?, List<LatLng>>>()
    segments += collectGpxSegments(doc.getElementsByTagName("trk"), "trkpt")
    segments += collectGpxSegments(doc.getElementsByTagName("rte"), "rtept")
    var withPoints = segments.filter { it.second.isNotEmpty() }
    if (withPoints.isEmpty()) {
        // Some GPX generators emit bare top-level <wpt> points with no
        // <trk>/<rte> wrapper — treat them all as a single route (see issue #27).
        val barePoints = collectGpxPoints(doc.documentElement, "wpt")
        if (barePoints.isNotEmpty()) withPoints = listOf(null to barePoints)
    }
    val fallback = unnamedFallback.ifBlank { "Imported Route" }
    return withPoints.mapIndexed { index, (name, points) ->
        val resolvedName =
            name?.takeIf { it.isNotBlank() }
                ?: if (withPoints.size > 1) "$fallback ${index + 1}" else fallback
        GpxImportedRoute(resolvedName, points)
    }
}

fun isGpxMimeType(mimeType: String?): Boolean {
    val mime = mimeType?.lowercase()?.substringBefore(';')?.trim() ?: return false
    return mime == "application/gpx+xml" || mime == "application/gpx"
}

fun uriPathLooksLikeGpx(uriString: String?): Boolean {
    val path = uriString?.substringBefore('?')?.substringBefore('#') ?: return false
    return path.endsWith(".gpx", ignoreCase = true)
}

fun displayNameLooksLikeGpx(displayName: String?): Boolean {
    val name = displayName?.substringAfterLast('/')?.substringBefore('?') ?: return false
    return name.endsWith(".gpx", ignoreCase = true)
}

fun isGpxOpenCandidate(
    mimeType: String?,
    uriString: String?,
    displayName: String?,
): Boolean = isGpxMimeType(mimeType) || uriPathLooksLikeGpx(uriString) || displayNameLooksLikeGpx(displayName)

/** Chat-app content URIs often omit .gpx; sniff the file body instead of trusting the MIME. */
fun looksLikeGpxContent(content: String): Boolean = content.take(4096).contains("<gpx", ignoreCase = true)

fun suggestedNameFromDisplayName(displayName: String?): String {
    val stem =
        displayName
            ?.substringAfterLast('/')
            ?.substringBefore('?')
            ?.replace(Regex("\\.gpx$", RegexOption.IGNORE_CASE), "")
            ?.trim()
            .orEmpty()
    return stem.ifBlank { "Imported Route" }
}

/**
 * First named-stop path that is small enough to play. Oversized tracks are skipped the same way
 * Routes → Import GPX skips them.
 */
fun selectGpxRouteForOpen(
    routes: List<GpxImportedRoute>,
    maxWaypoints: Int = AppConstants.ExportConstants.MAX_GPX_ROUTE_WAYPOINTS,
): GpxImportedRoute {
    val playable = routes.filter { it.waypoints.size in 1..maxWaypoints }
    if (playable.isEmpty()) {
        val maxPoints = maxWaypoints
        if (routes.any { it.waypoints.size > maxPoints }) {
            throw IllegalArgumentException(
                "Every route in this file has more than $maxPoints points and was skipped",
            )
        }
        throw IllegalArgumentException("No routes found in GPX file")
    }
    return playable.first()
}

fun loadGpxForOpen(
    gpxContent: String,
    displayName: String?,
): GpxImportedRoute {
    if (!looksLikeGpxContent(gpxContent)) {
        throw IllegalArgumentException("Not a GPX file")
    }
    val routes = parseGpxRoutes(gpxContent, unnamedFallback = suggestedNameFromDisplayName(displayName))
    return selectGpxRouteForOpen(routes)
}

private fun collectGpxSegments(
    elements: NodeList,
    pointTag: String,
): List<Pair<String?, List<LatLng>>> =
    (0 until elements.length).map { i ->
        val element = elements.item(i) as Element
        val nameNodes = element.getElementsByTagName("name")
        val name = if (nameNodes.length > 0) nameNodes.item(0).textContent else null
        name to collectGpxPoints(element, pointTag)
    }

private fun collectGpxPoints(
    element: Element,
    tagName: String,
): List<LatLng> {
    val nodes = element.getElementsByTagName(tagName)
    val points = mutableListOf<LatLng>()
    for (i in 0 until nodes.length) {
        val node = nodes.item(i)
        val lat =
            node.attributes
                ?.getNamedItem("lat")
                ?.nodeValue
                ?.toDoubleOrNull() ?: continue
        val lon =
            node.attributes
                ?.getNamedItem("lon")
                ?.nodeValue
                ?.toDoubleOrNull() ?: continue
        points.add(LatLng(lat, lon))
    }
    return points
}
