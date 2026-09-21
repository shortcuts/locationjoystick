package com.locationjoystick.feature.routes.impl

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.haversineDistance
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.component.LjButton
import com.locationjoystick.core.designsystem.component.LjCheckboxRow
import com.locationjoystick.core.designsystem.component.LjOutlinedButton
import com.locationjoystick.core.designsystem.component.LjPrimaryButton
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.designsystem.component.LjSegmentedControl
import com.locationjoystick.core.designsystem.component.readPlainText
import com.locationjoystick.core.location.rememberSpoofToggleState
import com.locationjoystick.core.map.geojson.buildSegmentsGeoJson
import com.locationjoystick.core.map.geojson.buildWaypointsGeoJson
import com.locationjoystick.core.map.maplibre.addCreatorLayers
import com.locationjoystick.core.map.maplibre.rememberMapView
import com.locationjoystick.core.overlay.OverlayService
import com.locationjoystick.feature.routes.impl.R
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.Locale
import org.maplibre.android.geometry.LatLng as MapLatLng

@Composable
fun PasteCoordinatesRoute(
    onRouteSaved: () -> Unit,
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    val viewModel: PasteCoordinatesViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spoofToggle = rememberSpoofToggleState()

    LaunchedEffect(state.saved) {
        if (state.saved) onRouteSaved()
    }

    PasteCoordinatesScreen(
        state = state,
        onPasteTextChange = viewModel::onPasteTextChange,
        onSwapLatLonChange = viewModel::onSwapLatLonChange,
        onLoadPoints = viewModel::loadPoints,
        onPointOrderChange = viewModel::onPointOrderChange,
        onBuildModeChange = viewModel::onBuildModeChange,
        onPlantingTravelChange = viewModel::onPlantingTravelChange,
        onPlantingRadiusTextChange = viewModel::onPlantingRadiusTextChange,
        onBuildPreview = viewModel::buildPreview,
        onSaveRoute = viewModel::saveRoute,
        onBack = onBack,
        isSpoofing = spoofToggle.isSpoofing,
        onToggleSpoofing = spoofToggle.onToggle,
        locationLabel = spoofToggle.locationLabel,
        bottomBar = bottomBar,
    )
}

@Preview(showBackground = true)
@Composable
private fun PasteCoordinatesScreenPreview() {
    PasteCoordinatesScreen(
        state = PasteCoordinatesUiState(),
        onPasteTextChange = {},
        onSwapLatLonChange = {},
        onLoadPoints = {},
        onPointOrderChange = {},
        onBuildModeChange = {},
        onPlantingTravelChange = {},
        onPlantingRadiusTextChange = {},
        onBuildPreview = {},
        onSaveRoute = {},
        onBack = {},
    )
}

@Composable
internal fun PasteCoordinatesScreen(
    state: PasteCoordinatesUiState,
    onPasteTextChange: (String) -> Unit,
    onSwapLatLonChange: (Boolean) -> Unit,
    onLoadPoints: () -> Unit,
    onPointOrderChange: (PastePointOrder) -> Unit,
    onBuildModeChange: (PasteBuildMode) -> Unit,
    onPlantingTravelChange: (PlantingTravel) -> Unit,
    onPlantingRadiusTextChange: (String) -> Unit,
    onBuildPreview: () -> Unit,
    onSaveRoute: (String) -> Unit,
    onBack: () -> Unit,
    isSpoofing: Boolean = false,
    onToggleSpoofing: () -> Unit = {},
    locationLabel: String? = null,
    bottomBar: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val emptyClipboardMessage = stringResource(R.string.paste_empty_clipboard)
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSaveDialog by remember { mutableStateOf(false) }
    var mapReady by remember { mutableStateOf(false) }
    var clipboardMessage by remember { mutableStateOf<String?>(null) }

    val mapView = rememberMapView()
    val mapRef = remember { mutableStateOf<MapLibreMap?>(null) }
    val segmentsSource = remember { mutableStateOf<GeoJsonSource?>(null) }
    val waypointsSource = remember { mutableStateOf<GeoJsonSource?>(null) }

    LaunchedEffect(clipboardMessage) {
        val message = clipboardMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        clipboardMessage = null
    }

    LaunchedEffect(showSaveDialog) {
        context.sendBroadcast(
            Intent(
                if (showSaveDialog) {
                    OverlayService.ACTION_OVERLAY_HIDE
                } else {
                    OverlayService.ACTION_OVERLAY_SHOW
                },
            ),
        )
    }

    DisposableEffect(lifecycleOwner) {
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
            mapView.onDestroy()
        }
    }

    LaunchedEffect(state.cleanedPoints, state.previewWaypoints, mapReady) {
        val map = mapRef.value ?: return@LaunchedEffect
        if (!mapReady) return@LaunchedEffect
        val fitPoints = state.previewWaypoints.ifEmpty { state.cleanedPoints }
        if (fitPoints.isEmpty()) return@LaunchedEffect
        if (fitPoints.size == 1) {
            map.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    MapLatLng(fitPoints[0].latitude, fitPoints[0].longitude),
                    AppConstants.MapConstants.DEFAULT_ZOOM,
                ),
            )
        } else {
            val bounds =
                LatLngBounds
                    .Builder()
                    .apply {
                        fitPoints.forEach { include(MapLatLng(it.latitude, it.longitude)) }
                    }.build()
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 72))
        }
    }

    LjScaffold(
        title = stringResource(R.string.paste_coordinates_screen_paste_coordinates),
        isSpoofing = isSpoofing,
        onToggleSpoofing = onToggleSpoofing,
        locationLabel = locationLabel,
        onNavigationClick = onBack,
        navigationIcon = LjIcons.ArrowBack,
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            AndroidView(
                factory = { _ ->
                    mapView.apply {
                        getMapAsync { map ->
                            mapRef.value = map
                            map.uiSettings.isAttributionEnabled = false
                            map.uiSettings.isLogoEnabled = false
                            map.cameraPosition =
                                CameraPosition
                                    .Builder()
                                    .target(
                                        MapLatLng(
                                            AppConstants.MapConstants.DEFAULT_LAT,
                                            AppConstants.MapConstants.DEFAULT_LON,
                                        ),
                                    ).zoom(AppConstants.MapConstants.DEFAULT_ZOOM)
                                    .build()
                            map.setStyle(Style.Builder().fromUri(AppConstants.MapConstants.EMPTY_MAP_STYLE_URI)) { style ->
                                val layers = style.addCreatorLayers()
                                segmentsSource.value = layers.segmentsSource
                                waypointsSource.value = layers.waypointsSource
                                mapReady = true
                            }
                        }
                    }
                },
                update = { _ ->
                    val segSrc = segmentsSource.value ?: return@AndroidView
                    val wpSrc = waypointsSource.value ?: return@AndroidView
                    val preview = state.previewWaypoints
                    segSrc.setGeoJson(
                        if (preview.size >= 2) {
                            buildSegmentsGeoJson(listOf(preview))
                        } else {
                            buildSegmentsGeoJson(emptyList())
                        },
                    )
                    wpSrc.setGeoJson(buildWaypointsGeoJson(state.cleanedPoints))
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp),
            )

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = state.pasteText,
                    onValueChange = onPasteTextChange,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                    label = { Text(stringResource(R.string.paste_coordinates_screen_coordinates)) },
                    placeholder = {
                        Text(
                            stringResource(R.string.paste_coordinates_screen_paste_coordinates_here_one_per_line) +
                                "64.147609, -21.922327\n" +
                                "37°34'11.4\"N 127°00'17.9\"E",
                        )
                    },
                )
                LjCheckboxRow(
                    checked = state.swapLatLon,
                    onCheckedChange = onSwapLatLonChange,
                    title = stringResource(R.string.paste_coordinates_screen_swap_lat_lon_order),
                    description = stringResource(R.string.paste_coordinates_screen_use_this_when_the_first_number_is_longitude),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LjOutlinedButton(
                        onClick = {
                            scope.launch {
                                val text = clipboard.readPlainText()
                                if (text.isNullOrBlank()) {
                                    clipboardMessage = emptyClipboardMessage
                                } else {
                                    onPasteTextChange(text)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.paste_coordinates_screen_paste_from_clipboard))
                    }
                    LjButton(
                        onClick = onLoadPoints,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.paste_coordinates_screen_load_points))
                    }
                }
                if (state.loadError != null) {
                    Text(state.loadError, color = MaterialTheme.colorScheme.error)
                }
                if (state.cleanedPoints.isNotEmpty()) {
                    Text(
                        pluralStringResource(
                            R.plurals.paste_coordinates_screen_points_loaded,
                            state.cleanedPoints.size,
                            state.cleanedPoints.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Text(stringResource(R.string.paste_coordinates_screen_point_order), style = MaterialTheme.typography.titleSmall)
                LjSegmentedControl(
                    options =
                        listOf(
                            PastePointOrder.ORIGINAL to stringResource(R.string.paste_keep_original),
                            PastePointOrder.PROXIMITY to stringResource(R.string.paste_optimize_proximity),
                        ),
                    selected = state.pointOrder,
                    onSelect = onPointOrderChange,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(stringResource(R.string.paste_coordinates_screen_build_mode), style = MaterialTheme.typography.titleSmall)
                LjSegmentedControl(
                    options =
                        listOf(
                            PasteBuildMode.AS_IS to stringResource(R.string.paste_as_is),
                            PasteBuildMode.WALKABLE to stringResource(R.string.paste_walkable_path),
                            PasteBuildMode.PLANTING to stringResource(R.string.paste_planting_mode),
                        ),
                    selected = state.buildMode,
                    onSelect = onBuildModeChange,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (state.buildMode == PasteBuildMode.PLANTING) {
                    OutlinedTextField(
                        value = state.plantingRadiusText,
                        onValueChange = onPlantingRadiusTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.paste_coordinates_screen_circle_radius_meters)) },
                        supportingText = {
                            Text(
                                stringResource(
                                    R.string.paste_coordinates_screen_default_35_m_circles_stay_geometric_only_travel_between_them,
                                ),
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                    Text(stringResource(R.string.paste_coordinates_screen_between_circles), style = MaterialTheme.typography.titleSmall)
                    LjSegmentedControl(
                        options =
                            listOf(
                                PlantingTravel.STRAIGHT to stringResource(R.string.paste_straight),
                                PlantingTravel.ROADS to stringResource(R.string.paste_via_roads),
                            ),
                        selected = state.plantingTravel,
                        onSelect = onPlantingTravelChange,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                LjPrimaryButton(
                    text = if (state.isBuilding) stringResource(R.string.paste_building) else stringResource(R.string.paste_build_preview),
                    onClick = onBuildPreview,
                    enabled = state.canBuild && !state.isBuilding,
                )
                if (state.isBuilding) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                if (state.buildError != null) {
                    Text(state.buildError, color = MaterialTheme.colorScheme.error)
                }
                if (state.previewWaypoints.size >= 2) {
                    val distanceMeters =
                        state.previewWaypoints.zipWithNext().sumOf { (from, to) ->
                            haversineDistance(from, to)
                        }
                    val distanceLabel =
                        if (distanceMeters >= 1000) {
                            String.format(Locale.US, "%.2f km", distanceMeters / 1000.0)
                        } else {
                            "${distanceMeters.toInt()} m"
                        }
                    Text(
                        pluralStringResource(
                            R.plurals.paste_coordinates_screen_waypoints_summary,
                            state.previewWaypoints.size,
                            state.previewWaypoints.size,
                            distanceLabel,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LjPrimaryButton(
                        text = stringResource(R.string.paste_save_route),
                        onClick = { showSaveDialog = true },
                        enabled = state.canSave,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (showSaveDialog) {
        PasteSaveRouteDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name ->
                onSaveRoute(name)
                showSaveDialog = false
            },
        )
    }
}

@Composable
private fun PasteSaveRouteDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.paste_coordinates_screen_save_route)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.paste_coordinates_screen_route_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name.trim())
                    }
                },
            ) {
                Text(stringResource(R.string.paste_coordinates_screen_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.paste_coordinates_screen_cancel))
            }
        },
    )
}
