package com.locationjoystick.feature.routes.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.location.MockLocationService
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.RouteReplayMode
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.core.model.Waypoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

private const val TAG = "RoutesViewModel"

@HiltViewModel
class RoutesViewModel
    @Inject
    constructor(
        private val routeRepository: RouteRepository,
        private val locationRepository: LocationRepository,
        private val settingsRepository: SettingsRepository,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        val uiState: StateFlow<RoutesUiState> =
            combine(
                routeRepository.getRoutes(),
                settingsRepository.getRoutesSortNewestFirst(),
            ) { routes, sortNewestFirst ->
                val sorted =
                    if (sortNewestFirst) {
                        routes.sortedByDescending { it.createdAt }
                    } else {
                        routes.sortedBy { it.createdAt }
                    }
                RoutesUiState(routes = sorted, isLoading = false, sortNewestFirst = sortNewestFirst)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = RoutesUiState(isLoading = true),
            )

        val playbackState: StateFlow<RoutePlaybackState> =
            combine(
                locationRepository.activeRouteId,
                locationRepository.mockLocationState,
                locationRepository.isReplayBackward,
            ) { activeRouteId, mockState, isBackward ->
                RoutePlaybackState(
                    activeRouteId = activeRouteId,
                    isPlaying = mockState == MockLocationState.RUNNING && activeRouteId != null,
                    isPaused = mockState == MockLocationState.PAUSED && activeRouteId != null,
                    isBackward = isBackward,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = RoutePlaybackState(),
            )

        fun toggleSort() {
            viewModelScope.launch {
                settingsRepository.setRoutesSortNewestFirst(!uiState.value.sortNewestFirst)
            }
        }

        fun deleteRoute(routeId: String) {
            viewModelScope.launch {
                routeRepository.deleteRoute(routeId)
            }
        }

        fun renameRoute(
            routeId: String,
            newName: String,
        ) {
            viewModelScope.launch {
                val route = uiState.value.routes.find { it.id == routeId } ?: return@launch
                routeRepository.updateRoute(
                    route.copy(name = newName, updatedAt = System.currentTimeMillis()),
                )
            }
        }

        fun startReplay(
            route: Route,
            mode: RouteReplayMode = RouteReplayMode.ONE_WAY,
        ) {
            viewModelScope.launch {
                val speedMs = settingsRepository.getActiveSpeedProfile().first().speedMetersPerSecond
                val isBackward = mode == RouteReplayMode.LOOP_REVERSE
                val isLooping = mode == RouteReplayMode.LOOP || mode == RouteReplayMode.LOOP_REVERSE
                val returnPosition =
                    if (mode == RouteReplayMode.RETURN_TO_LOCATION) locationRepository.currentPosition.value else null
                val intent =
                    Intent(context, MockLocationService::class.java).apply {
                        action = MockLocationService.ACTION_ROUTE_REPLAY_START
                        putExtra(MockLocationService.EXTRA_ROUTE_ID, route.id)
                        putExtra(MockLocationService.EXTRA_IS_BACKWARD, isBackward)
                        putExtra(MockLocationService.EXTRA_IS_LOOPING, isLooping)
                        putExtra(MockLocationService.EXTRA_SPEED_MS, speedMs)
                        if (returnPosition != null) {
                            putExtra(MockLocationService.EXTRA_RETURN_LAT, returnPosition.latitude)
                            putExtra(MockLocationService.EXTRA_RETURN_LON, returnPosition.longitude)
                        }
                    }
                context.startService(intent)
            }
        }

        fun pauseReplay() {
            val intent =
                Intent(context, MockLocationService::class.java).apply {
                    action = MockLocationService.ACTION_ROUTE_REPLAY_PAUSE
                }
            context.startService(intent)
        }

        fun resumeReplay() {
            viewModelScope.launch {
                val speedMs = settingsRepository.getActiveSpeedProfile().first().speedMetersPerSecond
                val intent =
                    Intent(context, MockLocationService::class.java).apply {
                        action = MockLocationService.ACTION_ROUTE_REPLAY_RESUME
                        putExtra(MockLocationService.EXTRA_SPEED_MS, speedMs)
                    }
                context.startService(intent)
            }
        }

        fun stopReplay() {
            val intent =
                Intent(context, MockLocationService::class.java).apply {
                    action = MockLocationService.ACTION_ROUTE_REPLAY_STOP
                }
            context.startService(intent)
        }

        fun exportRouteAsGpx(
            context: Context,
            route: Route,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val gpx = buildGpxString(route)
                    val dir = context.getExternalFilesDir("gpx") ?: return@launch
                    dir.mkdirs()
                    val filename = route.name.replace(" ", "_").replace(Regex("[^a-zA-Z0-9_]"), "")
                    val file = File(dir, "$filename.gpx")
                    file.writeText(gpx)

                    val uri =
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file,
                        )
                    val intent =
                        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "application/gpx+xml"
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    withContext(Dispatchers.Main) {
                        context.startActivity(android.content.Intent.createChooser(intent, "Share GPX"))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Export GPX failed", e)
                }
            }
        }

        private fun buildGpxString(route: Route): String =
            buildString {
                appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
                appendLine(
                    """<gpx version="${AppConstants.ExportConstants.GPX_VERSION}" creator="${AppConstants.ExportConstants.GPX_CREATOR}">""",
                )
                appendLine("  <trk><name>${route.name}</name><trkseg>")
                route.waypoints.forEach { wp ->
                    appendLine("""    <trkpt lat="${wp.position.latitude}" lon="${wp.position.longitude}"/>""")
                }
                appendLine("  </trkseg></trk>")
                append("</gpx>")
            }

        fun importRouteFromGpxAsync(
            uri: Uri,
            context: Context,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val route = importRouteFromGpx(uri)
                    routeRepository.insertRoute(route)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Route imported: ${route.name}", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "GPX import failed", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        private suspend fun importRouteFromGpx(uri: Uri): Route =
            withContext(Dispatchers.IO) {
                val gpxContent = readGpxContent(uri)
                val name = extractGpxName(gpxContent)
                val latLngs = parseGpxWaypoints(gpxContent)
                val waypoints =
                    latLngs.mapIndexed { index, latLng ->
                        Waypoint(
                            id = UUID.randomUUID().toString(),
                            position = latLng,
                            orderIndex = index,
                        )
                    }
                Route(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    waypoints = waypoints,
                    isLooping = false,
                    routeType = RouteType.STRAIGHT,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            }

        internal suspend fun readGpxContent(uri: Uri): String =
            withContext(Dispatchers.IO) {
                val descriptor = context.contentResolver.openAssetFileDescriptor(uri, "r")
                val fileSize = descriptor?.use { it.length }
                if (fileSize != null && fileSize > AppConstants.ExportConstants.MAX_GPX_IMPORT_SIZE_BYTES) {
                    throw IllegalArgumentException(
                        "GPX file is too large (${fileSize / 1024 / 1024} MB). Maximum allowed is " +
                            "${AppConstants.ExportConstants.MAX_GPX_IMPORT_SIZE_BYTES / 1024 / 1024} MB.",
                    )
                }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().readText()
                } ?: throw IllegalArgumentException("Cannot read GPX file")
            }

        internal fun extractGpxName(gpxContent: String): String {
            val nameRegex = Regex("<name>([^<]+)</name>")
            val match = nameRegex.find(gpxContent)
            return match?.groupValues?.get(1)?.takeIf { it.isNotEmpty() } ?: "Imported Route"
        }

        internal fun parseGpxWaypoints(gpxContent: String): List<LatLng> {
            val trkptRegex = Regex("""<trkpt\s+lat="([^"]+)"\s+lon="([^"]+)""")
            return trkptRegex
                .findAll(gpxContent)
                .map { match ->
                    val lat = match.groupValues[1].toDoubleOrNull() ?: return@map null
                    val lon = match.groupValues[2].toDoubleOrNull() ?: return@map null
                    LatLng(lat, lon)
                }.filterNotNull()
                .toList()
        }
    }
