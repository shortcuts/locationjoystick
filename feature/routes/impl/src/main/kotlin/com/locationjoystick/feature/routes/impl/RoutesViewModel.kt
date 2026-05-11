package com.locationjoystick.feature.routes.impl

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.location.MockLocationService
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

private const val TAG = "RoutesViewModel"

@HiltViewModel
class RoutesViewModel @Inject constructor(
    private val routeRepository: RouteRepository,
    private val locationRepository: LocationRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val uiState: StateFlow<RoutesUiState> = routeRepository.getRoutes()
        .map { routes -> RoutesUiState(routes = routes, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RoutesUiState(isLoading = true)
        )

    val playbackState: StateFlow<RoutePlaybackState> = combine(
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
        initialValue = RoutePlaybackState()
    )

    fun deleteRoute(routeId: String) {
        viewModelScope.launch {
            routeRepository.deleteRoute(routeId)
        }
    }

    fun renameRoute(routeId: String, newName: String) {
        viewModelScope.launch {
            val route = uiState.value.routes.find { it.id == routeId } ?: return@launch
            routeRepository.updateRoute(
                route.copy(name = newName, updatedAt = System.currentTimeMillis())
            )
        }
    }

    fun startReplay(route: Route, fromFirstWaypoint: Boolean = false, speedMs: Double = 1.4) {
        if (fromFirstWaypoint && route.waypoints.isNotEmpty()) {
            viewModelScope.launch {
                locationRepository.updatePosition(route.waypoints.first().position)
            }
        }
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_ROUTE_REPLAY_START
            putExtra(MockLocationService.EXTRA_ROUTE_ID, route.id)
            putExtra(MockLocationService.EXTRA_IS_BACKWARD, false)
            putExtra(MockLocationService.EXTRA_SPEED_MS, speedMs)
        }
        context.startService(intent)
    }

    fun pauseReplay() {
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_ROUTE_REPLAY_PAUSE
        }
        context.startService(intent)
    }

    fun resumeReplay(speedMs: Double = 1.4) {
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_ROUTE_REPLAY_RESUME
            putExtra(MockLocationService.EXTRA_SPEED_MS, speedMs)
        }
        context.startService(intent)
    }

    fun stopReplay() {
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_ROUTE_REPLAY_STOP
        }
        context.startService(intent)
    }

    fun exportRouteAsGpx(context: Context, route: Route) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val gpx = buildGpxString(route)
                val dir = context.getExternalFilesDir("gpx") ?: return@launch
                dir.mkdirs()
                val filename = route.name.replace(" ", "_").replace(Regex("[^a-zA-Z0-9_]"), "")
                val file = File(dir, "$filename.gpx")
                file.writeText(gpx)

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
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

    private fun buildGpxString(route: Route): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<gpx version="1.1" creator="locationjoystick">""")
        appendLine("  <trk><name>${route.name}</name><trkseg>")
        route.waypoints.forEach { wp ->
            appendLine("""    <trkpt lat="${wp.position.latitude}" lon="${wp.position.longitude}"/>""")
        }
        appendLine("  </trkseg></trk>")
        append("</gpx>")
    }
}
