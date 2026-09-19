package com.locationjoystick.core.common.util

import com.locationjoystick.core.model.LatLng

enum class CaptureLinkDecision {
    CAPTURE,
    JUMP,
    CAPTURE_AND_JUMP,
    FORWARD,
}

object CaptureBrowserPackages {
    const val CHROME = "com.android.chrome"
    const val GOOGLE_MAPS = "com.google.android.apps.maps"
}

fun decideCaptureLink(
    captureModeEnabled: Boolean,
    captureEnabled: Boolean,
    jumpEnabled: Boolean,
    coords: Pair<Double, Double>?,
): CaptureLinkDecision =
    when {
        !captureModeEnabled -> CaptureLinkDecision.FORWARD
        !captureEnabled && !jumpEnabled -> CaptureLinkDecision.FORWARD
        coords != null && captureEnabled && jumpEnabled -> CaptureLinkDecision.CAPTURE_AND_JUMP
        coords != null && captureEnabled -> CaptureLinkDecision.CAPTURE
        coords != null && jumpEnabled -> CaptureLinkDecision.JUMP
        else -> CaptureLinkDecision.FORWARD
    }

fun isGoogleMapsWebLink(url: String): Boolean {
    val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
    val host = uri.host?.lowercase().orEmpty()
    val path = uri.path.orEmpty()
    return host == "maps.app.goo.gl" ||
        host == "maps.google.com" ||
        host == "www.google.com" &&
        path.startsWith("/maps") ||
        host == "google.com" &&
        path.startsWith("/maps") ||
        host == "goo.gl" &&
        path.startsWith("/maps")
}

fun appendCapturedPoint(
    existing: List<LatLng>,
    incoming: LatLng,
): List<LatLng> {
    if (existing.lastOrNull() == incoming) return existing
    return existing + incoming
}

fun resolvePreferredBrowserPackage(
    savedPackage: String?,
    selfPackage: String,
): String {
    val trimmed = savedPackage?.trim().orEmpty()
    return if (trimmed.isNotEmpty() && trimmed != selfPackage) trimmed else CaptureBrowserPackages.CHROME
}

fun pickForwardBrowserPackage(
    candidates: List<String>,
    selfPackage: String,
    preferred: String,
): String? {
    val others = candidates.filter { it != selfPackage }
    if (preferred in others) return preferred
    return others.firstOrNull()
}

fun formatCapturedPoint(point: LatLng): String = String.format(java.util.Locale.US, "%.6f, %.6f", point.latitude, point.longitude)

fun formatCapturedPointsForClipboard(points: List<LatLng>): String = points.joinToString("\n") { formatCapturedPoint(it) }

/** Capture-order list, or nearest-neighbor order used by paste-coordinates "Optimize proximity". */
fun orderedCapturedPoints(
    points: List<LatLng>,
    optimizeProximity: Boolean,
): List<LatLng> = if (optimizeProximity) orderByProximity(points) else points
