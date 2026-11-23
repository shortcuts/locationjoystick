package com.locationjoystick.core.common.util

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.LatLng

/** Lifecycle of the freehand line tool. Only [Drawing] holds points. */
sealed interface DrawLineState {
    data object Off : DrawLineState

    data object Armed : DrawLineState

    data class Drawing(
        val points: List<LatLng>,
    ) : DrawLineState
}

val DrawLineState.isOn: Boolean get() = this !is DrawLineState.Off

/** Button tap: switches the tool on or off. A line in progress is dropped. */
fun DrawLineState.toggled(): DrawLineState = if (this is DrawLineState.Off) DrawLineState.Armed else DrawLineState.Off

/** Finger down. Ignored unless the tool waits for a line. */
fun DrawLineState.started(point: LatLng): DrawLineState = if (this is DrawLineState.Armed) DrawLineState.Drawing(listOf(point)) else this

/** Finger moved. Keeps the point only when it is far enough from the last kept one. */
fun DrawLineState.extended(point: LatLng): DrawLineState =
    if (this is DrawLineState.Drawing && haversineDistance(points.last(), point) >= AppConstants.DrawConstants.MIN_POINT_SPACING_METERS) {
        DrawLineState.Drawing(points + point)
    } else {
        this
    }

/** Finger up. Returns the tool to [DrawLineState.Armed] and the finished line, or null when it is too short. */
fun DrawLineState.finished(): Pair<DrawLineState, List<LatLng>?> =
    if (this is DrawLineState.Drawing) DrawLineState.Armed to finishDrawnLine(points) else this to null

/** Simplifies the raw points and caps them at the route limit. Null under 2 points. */
fun finishDrawnLine(points: List<LatLng>): List<LatLng>? {
    val simplified = rdpSimplify(points, AppConstants.DrawConstants.SIMPLIFY_EPSILON_METERS)
    if (simplified.size < 2) return null
    val max = AppConstants.ExportConstants.MAX_GPX_ROUTE_WAYPOINTS
    if (simplified.size <= max) return simplified
    return List(max) { simplified[(it * (simplified.size - 1L) / (max - 1)).toInt()] }
}
