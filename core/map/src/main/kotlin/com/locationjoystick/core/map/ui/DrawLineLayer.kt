package com.locationjoystick.core.map.ui

import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.locationjoystick.core.common.util.DrawLineState
import com.locationjoystick.core.common.util.extended
import com.locationjoystick.core.common.util.finished
import com.locationjoystick.core.common.util.started
import com.locationjoystick.core.map.projection.MapProjection
import com.locationjoystick.core.model.LatLng
import org.maplibre.android.maps.MapLibreMap

/**
 * Transparent layer over a map that records one finger drag as a WGS-84 line. It sits above the
 * map view, so the map never sees the touches and pan, zoom and long-press stay untouched.
 * Compose it only while draw mode is on; [onLine] gets the simplified line on release.
 */
@Composable
fun DrawLineLayer(
    map: MapLibreMap?,
    projection: MapProjection,
    onLine: (List<LatLng>) -> Unit,
    modifier: Modifier = Modifier,
    strokeColor: Color = MaterialTheme.colorScheme.primary,
    strokeWidthPx: Float = 8f,
) {
    var state: DrawLineState by remember { mutableStateOf(DrawLineState.Armed) }
    val screenPoints = remember { mutableStateListOf<Offset>() }
    val currentProjection by rememberUpdatedState(projection)
    val currentOnLine by rememberUpdatedState(onLine)

    fun wgs(at: Offset): LatLng? {
        val latLng = map?.projection?.fromScreenLocation(PointF(at.x, at.y)) ?: return null
        return currentProjection.fromMap(LatLng(latLng.latitude, latLng.longitude))
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(map) {
                    detectDragGestures(
                        onDragStart = { at ->
                            val point = wgs(at) ?: return@detectDragGestures
                            state = state.started(point)
                            screenPoints.clear()
                            screenPoints.add(at)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val before = state
                            val point = wgs(change.position) ?: return@detectDragGestures
                            state = before.extended(point)
                            if (state !== before) screenPoints.add(change.position)
                        },
                        onDragEnd = {
                            val (next, line) = state.finished()
                            state = next
                            screenPoints.clear()
                            line?.let(currentOnLine)
                        },
                        onDragCancel = {
                            state = DrawLineState.Armed
                            screenPoints.clear()
                        },
                    )
                },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (screenPoints.size < 2) return@Canvas
            val path =
                Path().apply {
                    moveTo(screenPoints.first().x, screenPoints.first().y)
                    screenPoints.drop(1).forEach { lineTo(it.x, it.y) }
                }
            drawPath(path, strokeColor, style = Stroke(strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
