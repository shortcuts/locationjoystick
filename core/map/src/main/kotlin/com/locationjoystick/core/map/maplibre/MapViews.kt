package com.locationjoystick.core.map.maplibre

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView

/**
 * Creates a [MapView] after [MapTileHttp.install], the only supported MapLibre init path.
 *
 * [overlay] uses TextureView (`textureMode`) so the map composites inside a
 * `TYPE_APPLICATION_OVERLAY` window. GLSurfaceView punches a transparent hole through
 * overlay windows; the in-app map keeps the default GL surface.
 */
fun createMapView(
    context: Context,
    overlay: Boolean = false,
): MapView {
    MapTileHttp.install(context)
    if (!overlay) return MapView(context)
    val options =
        MapLibreMapOptions
            .createFromAttributes(context)
            .textureMode(true)
            .translucentTextureSurface(false)
    return MapView(context, options)
}

@Composable
fun rememberMapView(overlay: Boolean = false): MapView {
    val context = LocalContext.current
    return remember(context, overlay) { createMapView(context, overlay) }
}
