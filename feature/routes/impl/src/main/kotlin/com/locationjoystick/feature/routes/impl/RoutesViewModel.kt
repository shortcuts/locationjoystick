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
import com.locationjoystick.core.common.util.parseGpxRoutes
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.data.TeleportUseCase
import com.locationjoystick.core.location.MockLocationService
import com.locationjoystick.core.location.RouteStartConfig
import com.locationjoystick.core.location.StartRouteReplayUseCase
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.core.model.SavedItemSortMode
import com.locationjoystick.core.model.Waypoint
import com.locationjoystick.core.model.sortedBySavedItemMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        private val teleportUseCase: TeleportUseCase,
        private val startRouteReplayUseCase: StartRouteReplayUseCase,
        @param:ApplicationContext private val context: Context,
    ) : ViewModel() {
        val uiState: StateFlow<RoutesUiState> =
            combine(
                routeRepository.getRoutes(),
                settingsRepository.getRoutesSortMode(),
                settingsRepository.getHideTeleportFeatures(),
                locationRepository.isRoadRouteFetchInFlight,
            ) { routes, sortMode, hideTeleportFeatures, isRoadRouteFetchInFlight ->
                RoutesUiState(
                    routes = routes.sortedBySavedItemMode(sortMode) { it.name },
                    isLoading = false,
                    sortMode = sortMode,
                    hideTeleportFeatures = hideTeleportFeatures,
                    isRoadRouteFetchInFlight = isRoadRouteFetchInFlight,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = RoutesUiState(isLoading = true),
            )

        private val _errorMessage = MutableStateFlow<String?>(null)
        val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

        fun clearError() {
            _errorMessage.value = null
        }

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

        fun setSortMode(mode: SavedItemSortMode) {
            viewModelScope.launch {
                settingsRepository.setRoutesSortMode(mode)
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
            config: RouteStartConfig = RouteStartConfig(),
        ) {
            viewModelScope.launch {
                startRouteReplayUseCase.execute(routeId = route.id, config = config)
            }
        }

        fun teleportTo(position: LatLng) {
            viewModelScope.launch { teleportUseCase.execute(position) }
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
                        context.startActivity(
                            android.content.Intent.createChooser(intent, context.getString(R.string.route_creator_share_gpx)),
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Export GPX failed", e)
                    _errorMessage.value = context.getString(R.string.route_creator_export_failed, e.message.orEmpty())
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
                    val outcome = importRoutesFromGpx(uri)
                    outcome.routes.forEach { routeRepository.insertRoute(it) }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, buildGpxImportMessage(context, outcome), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "GPX import failed", e)
                    withContext(Dispatchers.Main) {
                        Toast
                            .makeText(
                                context,
                                context.getString(R.string.route_creator_import_failed, e.message.orEmpty()),
                                Toast.LENGTH_SHORT,
                            ).show()
                    }
                }
            }
        }

        /** One [Route] per `<trk>`/`<rte>` element in the GPX file — a file may describe multiple routes. */
        private suspend fun importRoutesFromGpx(uri: Uri): GpxImportOutcome =
            withContext(Dispatchers.IO) {
                val gpxContent = readGpxContent(uri)
                val gpxRoutes = parseGpxRoutes(gpxContent)
                if (gpxRoutes.isEmpty()) throw IllegalArgumentException("No routes found in GPX file")
                val (importable, oversized) =
                    gpxRoutes.partition { it.waypoints.size <= AppConstants.ExportConstants.MAX_GPX_ROUTE_WAYPOINTS }
                if (importable.isEmpty()) {
                    val maxPoints = AppConstants.ExportConstants.MAX_GPX_ROUTE_WAYPOINTS
                    throw IllegalArgumentException(
                        "Every route in this file has more than $maxPoints points and was skipped",
                    )
                }
                val routes =
                    importable.map { gpxRoute ->
                        val waypoints =
                            gpxRoute.waypoints.mapIndexed { index, latLng ->
                                Waypoint(
                                    id = UUID.randomUUID().toString(),
                                    position = latLng,
                                    orderIndex = index,
                                )
                            }
                        Route(
                            id = UUID.randomUUID().toString(),
                            name = gpxRoute.name,
                            waypoints = waypoints,
                            isLooping = false,
                            routeType = RouteType.STRAIGHT,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                        )
                    }
                GpxImportOutcome(routes, oversized.size)
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
    }

internal data class GpxImportOutcome(
    val routes: List<Route>,
    val skippedOversized: Int,
)

internal fun buildGpxImportMessage(
    context: Context,
    outcome: GpxImportOutcome,
): String {
    val base =
        if (outcome.routes.size == 1) {
            context.getString(R.string.route_creator_route_imported, outcome.routes.first().name)
        } else {
            context.getString(R.string.route_creator_routes_imported, outcome.routes.size)
        }
    if (outcome.skippedOversized <= 0) return base
    val skippedRes =
        if (outcome.skippedOversized == 1) {
            R.string.route_creator_skipped_oversized_one
        } else {
            R.string.route_creator_skipped_oversized_other
        }
    return "$base. ${context.getString(skippedRes, outcome.skippedOversized)}"
}
