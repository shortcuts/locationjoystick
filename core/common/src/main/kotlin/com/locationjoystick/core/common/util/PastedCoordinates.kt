package com.locationjoystick.core.common.util

import com.locationjoystick.core.model.LatLng

private val KEYCAP_EMOJI = Regex("[0-9]\\uFE0F?\\u20E3|\\uD83D\\uDD1F")
private val TYPOD_DOT_SEPARATOR = Regex("""([-+]?\d+\.\d+)\s*\.\s*([-+]?\d+\.\d+)""")
private val NON_COORD_CHARS = Regex("""[^0-9\-+.,\s\[\](){}]""")
private val LEADING_NUMBERING = Regex("""^\s*\d+(?:(?:\.(?!\d)|[)\-:])+\s*|\s+)""")
private val NUMERIC_TOKEN = Regex("""[-+]?[0-9]*\.?[0-9]+""")
private val DMS_HINT = Regex("""[°º˚]""")
private val LETTER = Regex("""[A-Za-z]""")
private val NS = setOf('N', 'S')
private val EW = setOf('E', 'W')

/** Google Maps-style `37°34'11.4"N 127°00'17.9"E` (degree mark required so decimals stay on the other path). */
private val DMS_COMPONENT =
    Regex(
        "(?i)" +
            """(?:([NSEW])\s*)?""" +
            """(\d{1,3}(?:\.\d+)?)\s*[°º˚]\s*""" +
            """(?:(\d{1,2}(?:\.\d+)?)\s*[′'’]?\s*""" +
            """(?:(\d{1,2}(?:\.\d+)?)\s*[″"”]?\s*)?)?""" +
            """([NSEW])?(?!\d)""",
    )

/**
 * Parses messy pasted coordinate text (emoji, numbered lists, typo'd dots, DMS) into lat/lon points.
 *
 * Per-line cleanup accepts mixed coordinate lists. Does not replace [parseRawLatLng], which stays the strict
 * single-pair parser used by search (decimal or one DMS pair).
 */
fun parsePastedCoordinates(
    text: String,
    swapLatLon: Boolean = false,
): List<LatLng> {
    if (text.isBlank()) return emptyList()
    return text.split(Regex("""[\r\n]+""")).mapNotNull { rawLine ->
        parsePastedCoordinateLine(rawLine, swapLatLon)
    }
}

/**
 * Clipboard paste into the coordinates box. Empty field is replaced. Existing text gets a new
 * line so a second copy from another app appends instead of wiping the first point.
 */
fun mergeClipboardIntoPasteText(
    current: String,
    clipboard: String,
): String {
    val clip = clipboard.trim()
    if (clip.isEmpty()) return current
    if (current.isBlank()) return clip
    return current.trimEnd() + "\n" + clip
}

/**
 * Parses one pasted line or a single typed pair. Tries degree-minute-second first so `°` / N/S/E/W
 * are not stripped and misread as the first two numbers.
 */
internal fun parsePastedCoordinateLine(
    rawLine: String,
    swapLatLon: Boolean = false,
): LatLng? {
    var s = rawLine.trim()
    if (s.isEmpty()) return null
    s = KEYCAP_EMOJI.replace(s, "")
    parseDmsCoordinatePair(s, swapLatLon)?.let { return it }
    if (DMS_HINT.containsMatchIn(s)) return null
    // Reject prose before cleanup so "route 2 ends in 1 hour" cannot become coordinate 2,1.
    if (LETTER.containsMatchIn(s)) return null
    s = TYPOD_DOT_SEPARATOR.replace(s, "$1,$2")
    s = NON_COORD_CHARS.replace(s, "")
    s = s.trim()
    s = LEADING_NUMBERING.replaceFirst(s, "")
    s = s.trim()
    val tokens = NUMERIC_TOKEN.findAll(s).map { it.value }.toList()
    if (tokens.size < 2) return null
    var lat = tokens[0].toDoubleOrNull() ?: return null
    var lon = tokens[1].toDoubleOrNull() ?: return null
    if (swapLatLon) {
        val tmp = lat
        lat = lon
        lon = tmp
    }
    if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
    return LatLng(lat, lon)
}

internal fun parseDmsCoordinatePair(
    text: String,
    swapLatLon: Boolean = false,
): LatLng? {
    val matches = DMS_COMPONENT.findAll(text).take(2).toList()
    if (matches.size < 2) return null
    val first = dmsMatchToValue(matches[0]) ?: return null
    val second = dmsMatchToValue(matches[1]) ?: return null
    val (latValue, lonValue) = assignDmsAxes(first, second, swapLatLon) ?: return null
    if (latValue !in -90.0..90.0 || lonValue !in -180.0..180.0) return null
    return LatLng(latValue, lonValue)
}

private data class DmsValue(
    val magnitude: Double,
    val hemisphere: Char?,
)

private fun dmsMatchToValue(match: MatchResult): DmsValue? {
    val deg = match.groupValues[2].toDoubleOrNull() ?: return null
    val min = match.groupValues[3].toDoubleOrNull() ?: 0.0
    val sec = match.groupValues[4].toDoubleOrNull() ?: 0.0
    if (min < 0.0 || min >= 60.0 || sec < 0.0 || sec >= 60.0) return null
    val pre = match.groupValues[1].uppercase().firstOrNull()
    val post = match.groupValues[5].uppercase().firstOrNull()
    if (pre != null && post != null && pre != post) return null
    val hemisphere = pre ?: post
    if (hemisphere != null && hemisphere !in NS && hemisphere !in EW) return null
    return DmsValue(deg + min / 60.0 + sec / 3600.0, hemisphere)
}

private fun assignDmsAxes(
    first: DmsValue,
    second: DmsValue,
    swapLatLon: Boolean,
): Pair<Double, Double>? {
    val h1 = first.hemisphere
    val h2 = second.hemisphere
    val firstIsNs = h1 != null && h1 in NS
    val firstIsEw = h1 != null && h1 in EW
    val secondIsNs = h2 != null && h2 in NS
    val secondIsEw = h2 != null && h2 in EW
    val latHem: Char?
    val lonHem: Char?
    val latMag: Double
    val lonMag: Double
    when {
        firstIsNs && secondIsEw -> {
            latMag = first.magnitude
            lonMag = second.magnitude
            latHem = h1
            lonHem = h2
        }
        firstIsEw && secondIsNs -> {
            latMag = second.magnitude
            lonMag = first.magnitude
            latHem = h2
            lonHem = h1
        }
        h1 == null && h2 == null -> {
            if (swapLatLon) {
                latMag = second.magnitude
                lonMag = first.magnitude
            } else {
                latMag = first.magnitude
                lonMag = second.magnitude
            }
            latHem = null
            lonHem = null
        }
        else -> return null
    }
    return Pair(applyHemisphere(latMag, latHem), applyHemisphere(lonMag, lonHem))
}

private fun applyHemisphere(
    magnitude: Double,
    hemisphere: Char?,
): Double {
    val sign =
        when (hemisphere) {
            'S', 'W' -> -1.0
            else -> 1.0
        }
    return kotlin.math.abs(magnitude) * sign
}
