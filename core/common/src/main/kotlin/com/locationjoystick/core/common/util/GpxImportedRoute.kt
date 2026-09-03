package com.locationjoystick.core.common.util

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
 * Shared by the Routes screen's "Import GPX" and the Settings screen's "Import from GPS Joystick"
 * (see issue #63 — newer GPS Joystick versions export GPX instead of its old Realm database format).
 */
fun parseGpxRoutes(gpxContent: String): List<GpxImportedRoute> {
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
        // Some GPX generators (e.g. pokedex100) emit bare top-level <wpt> points with no
        // <trk>/<rte> wrapper — treat them all as a single route (see issue #27).
        val barePoints = collectGpxPoints(doc.documentElement, "wpt")
        if (barePoints.isNotEmpty()) withPoints = listOf(null to barePoints)
    }
    return withPoints.mapIndexed { index, (name, points) ->
        val resolvedName =
            name?.takeIf { it.isNotBlank() }
                ?: if (withPoints.size > 1) "Imported Route ${index + 1}" else "Imported Route"
        GpxImportedRoute(resolvedName, points)
    }
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
