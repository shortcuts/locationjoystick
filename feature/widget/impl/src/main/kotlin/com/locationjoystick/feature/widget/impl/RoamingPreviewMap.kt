package com.locationjoystick.feature.widget.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.map.geojson.buildLineGeoJson
import com.locationjoystick.core.map.geojson.buildPointsGeoJson
import com.locationjoystick.core.map.geojson.buildPositionGeoJson
import com.locationjoystick.core.map.maplibre.MapLibreLayerIds
import com.locationjoystick.core.map.maplibre.MapLibreSourceIds
import com.locationjoystick.core.map.maplibre.addEphemeralRouteLayers
import com.locationjoystick.core.map.maplibre.addLocationLayers
import com.locationjoystick.core.map.maplibre.applyZoomBounds
import com.locationjoystick.core.map.maplibre.rememberMapView
import com.locationjoystick.core.map.projection.projection
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MapTileSource
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.geometry.LatLng as MapLatLng

private const val FIT_PADDING_PX = 48

@Composable
internal fun BindMapViewLifecycle(mapView: MapView) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        mapView.onCreate(null)
        mapView.onStart()
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
}

@Composable
internal fun RoamingPreviewMap(
    points: List<LatLng>,
    currentPosition: LatLng?,
    tileSource: MapTileSource,
    modifier: Modifier = Modifier,
) {
    val proj = tileSource.projection
    val mapView = rememberMapView(overlay = true)
    BindMapViewLifecycle(mapView)
    val mapRef = remember { mutableStateOf<MapLibreMap?>(null) }
    val positionSource = remember { mutableStateOf<GeoJsonSource?>(null) }
    val routeSource = remember { mutableStateOf<GeoJsonSource?>(null) }
    val endpointsSource = remember { mutableStateOf<GeoJsonSource?>(null) }
    val fittedPoints = remember { mutableStateOf<List<LatLng>?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { _ ->
            mapView.apply {
                getMapAsync { map ->
                    mapRef.value = map
                    map.uiSettings.setAllGesturesEnabled(false)
                    map.uiSettings.isAttributionEnabled = false
                    map.uiSettings.isLogoEnabled = false
                    map.applyZoomBounds(tileSource)
                    map.setStyle(Style.Builder().fromUri(AppConstants.MapConstants.EMPTY_MAP_STYLE_URI)) { style ->
                        val layers =
                            style.addLocationLayers(
                                tileSource = tileSource,
                                osmSourceId = MapLibreSourceIds.PANEL_OSM,
                                osmLayerId = MapLibreLayerIds.PANEL_OSM,
                                osmPreviewSourceId = MapLibreSourceIds.PANEL_OSM_PREVIEW,
                                osmPreviewLayerId = MapLibreLayerIds.PANEL_OSM_PREVIEW,
                                lineWidth = 3f,
                            )
                        val ephemeral = style.addEphemeralRouteLayers()
                        positionSource.value = layers.positionSource
                        routeSource.value = ephemeral.routeSource
                        endpointsSource.value = ephemeral.endpointsSource
                    }
                }
            }
        },
        update = { _ ->
            val map = mapRef.value ?: return@AndroidView
            val route = routeSource.value ?: return@AndroidView
            val endpoints = endpointsSource.value ?: return@AndroidView
            val mapPoints = proj.toMap(points)
            route.setGeoJson(buildLineGeoJson(mapPoints))
            endpoints.setGeoJson(buildPointsGeoJson(mapPoints))
            val mapPosition = currentPosition?.let(proj::toMap)
            positionSource.value?.setGeoJson(buildPositionGeoJson(mapPosition))
            if (fittedPoints.value != points) {
                fittedPoints.value = points
                val bounds =
                    LatLngBounds
                        .Builder()
                        .apply { (mapPoints + listOfNotNull(mapPosition)).forEach { include(MapLatLng(it.latitude, it.longitude)) } }
                        .build()
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, FIT_PADDING_PX))
            }
        },
    )
}
