package com.locationjoystick.feature.routes.impl

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.buildPlantingWaypoints
import com.locationjoystick.core.common.util.clampPlantingRadius
import com.locationjoystick.core.common.util.flattenRouteLegs
import com.locationjoystick.core.common.util.orderByProximity
import com.locationjoystick.core.common.util.parsePastedCoordinates
import com.locationjoystick.core.common.util.parseTeleportBetweenDelaySeconds
import com.locationjoystick.core.common.util.plantingRings
import com.locationjoystick.core.common.util.stitchRingsWithConnectors
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MapTileSource
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.core.model.Waypoint
import com.locationjoystick.core.routing.OsrmClient
import com.locationjoystick.core.routing.RoutingErrorReporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class PastePointOrder { ORIGINAL, PROXIMITY }

// Keep in sync with RouteType: every route type the paste builder can create needs a mode here (see resolvedRouteType).
// Planting is orthogonal to the mode: see PasteCoordinatesUiState.plantingEnabled.
enum class PasteBuildMode { AS_IS, WALKABLE, TELEPORT }

data class PasteCoordinatesUiState(
    val pasteText: String = "",
    val swapLatLon: Boolean = false,
    val cleanedPoints: List<LatLng> = emptyList(),
    val pointOrder: PastePointOrder = PastePointOrder.ORIGINAL,
    val buildMode: PasteBuildMode = PasteBuildMode.AS_IS,
    val plantingRadiusMeters: Double = AppConstants.RouteConstants.PLANTING_DEFAULT_RADIUS_METERS,
    val plantingRadiusText: String =
        AppConstants.RouteConstants.PLANTING_DEFAULT_RADIUS_METERS
            .toInt()
            .toString(),
    val plantingEnabled: Boolean = false,
    val teleportDelayText: String = AppConstants.RouteConstants.TELEPORT_BETWEEN_DEFAULT_DELAY_SECONDS.toString(),
    val hideTeleportFeatures: Boolean = false,
    val showPlanting: Boolean = true,
    val previewWaypoints: List<LatLng> = emptyList(),
    val isBuilding: Boolean = false,
    val loadError: String? = null,
    val buildError: String? = null,
    val saved: Boolean = false,
) {
    val canBuild: Boolean
        get() =
            if (plantingEnabled) cleanedPoints.isNotEmpty() else cleanedPoints.size >= 2

    val canSave: Boolean
        get() = previewWaypoints.size >= 2 && !isBuilding

    val resolvedRouteType: RouteType
        get() =
            when (buildMode) {
                PasteBuildMode.TELEPORT -> RouteType.TELEPORT
                PasteBuildMode.WALKABLE -> RouteType.GUIDED
                PasteBuildMode.AS_IS -> RouteType.STRAIGHT
            }

    val teleportWaitSeconds: Int
        get() = parseTeleportBetweenDelaySeconds(teleportDelayText)
}

internal fun buildRouteFromPositions(
    name: String,
    positions: List<LatLng>,
    routeType: RouteType,
    waitSeconds: Int = 0,
    plantingRadiusMeters: Double? = null,
    nowMs: Long = System.currentTimeMillis(),
    id: String = UUID.randomUUID().toString(),
): Route {
    val waypoints =
        positions.mapIndexed { index, latLng ->
            Waypoint(
                id = UUID.randomUUID().toString(),
                position = latLng,
                orderIndex = index,
                waitSeconds = waitSeconds,
            )
        }
    return Route(
        id = id,
        name = name,
        waypoints = waypoints,
        isLooping = false,
        routeType = routeType,
        plantingRadiusMeters = plantingRadiusMeters,
        createdAt = nowMs,
        updatedAt = nowMs,
    )
}

@HiltViewModel
class PasteCoordinatesViewModel
    @Inject
    constructor(
        private val routeRepository: RouteRepository,
        private val osrmClient: OsrmClient,
        private val routingErrorReporter: RoutingErrorReporter,
        private val settingsRepository: SettingsRepository,
        @param:ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(PasteCoordinatesUiState())
        val uiState: StateFlow<PasteCoordinatesUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                settingsRepository.getHideTeleportFeatures().collect { hide ->
                    _uiState.update { it.copy(hideTeleportFeatures = hide) }
                }
            }
            viewModelScope.launch {
                settingsRepository.getGuidedPath().collect { path ->
                    // Hidden Planting must not stay armed: the toggle is the only way to turn it off.
                    _uiState.update {
                        it.copy(
                            showPlanting = path.showsPlanting,
                            plantingEnabled = it.plantingEnabled && path.showsPlanting,
                        )
                    }
                }
            }
            // ponytail: a user typing exactly the default text before the first DataStore read is overwritten.
            viewModelScope.launch {
                val radius = settingsRepository.getPlantingRadius().first()
                val defaultText = PasteCoordinatesUiState().plantingRadiusText
                _uiState.update {
                    if (it.plantingRadiusText != defaultText) {
                        it
                    } else {
                        it.copy(plantingRadiusMeters = radius, plantingRadiusText = radius.toInt().toString())
                    }
                }
            }
        }

        val mapTileSource: StateFlow<MapTileSource> =
            settingsRepository
                .getMapTileSource()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapTileSource.DEFAULT)

        fun onPasteTextChange(text: String) {
            _uiState.update { it.copy(pasteText = text, loadError = null) }
        }

        fun onSwapLatLonChange(swap: Boolean) {
            _uiState.update { it.copy(swapLatLon = swap) }
        }

        fun onPointOrderChange(order: PastePointOrder) {
            _uiState.update { it.copy(pointOrder = order, previewWaypoints = emptyList()) }
        }

        fun onBuildModeChange(mode: PasteBuildMode) {
            _uiState.update { it.copy(buildMode = mode, previewWaypoints = emptyList(), buildError = null) }
        }

        fun onPlantingEnabledChange(enabled: Boolean) {
            _uiState.update { it.copy(plantingEnabled = enabled, previewWaypoints = emptyList(), buildError = null) }
        }

        fun onTeleportDelayTextChange(text: String) {
            _uiState.update { it.copy(teleportDelayText = text.filter(Char::isDigit).take(3)) }
        }

        fun onPlantingRadiusTextChange(text: String) {
            val parsed = text.toDoubleOrNull()
            _uiState.update { state ->
                state.copy(
                    plantingRadiusText = text,
                    plantingRadiusMeters =
                        if (parsed != null) {
                            clampPlantingRadius(parsed)
                        } else {
                            state.plantingRadiusMeters
                        },
                    previewWaypoints = emptyList(),
                )
            }
        }

        fun loadPoints() {
            val state = _uiState.value
            val points = parsePastedCoordinates(state.pasteText, state.swapLatLon)
            if (points.isEmpty()) {
                _uiState.update {
                    it.copy(
                        cleanedPoints = emptyList(),
                        previewWaypoints = emptyList(),
                        loadError = context.getString(R.string.route_paste_no_valid_coordinates),
                    )
                }
                return
            }
            _uiState.update {
                it.copy(
                    cleanedPoints = points,
                    previewWaypoints = emptyList(),
                    loadError = null,
                    buildError = null,
                )
            }
        }

        fun buildPreview() {
            val state = _uiState.value
            if (!state.canBuild) {
                val message =
                    if (state.plantingEnabled) {
                        context.getString(R.string.route_paste_need_one_point_planting)
                    } else {
                        context.getString(R.string.route_paste_need_two_points)
                    }
                _uiState.update { it.copy(buildError = message) }
                return
            }
            viewModelScope.launch {
                _uiState.update { it.copy(isBuilding = true, buildError = null) }
                val ordered = orderedPoints(state)
                val preview =
                    when {
                        state.plantingEnabled -> buildPlantingPath(ordered, state)
                        state.buildMode == PasteBuildMode.WALKABLE -> buildWalkablePath(ordered)
                        else -> ordered
                    }
                _uiState.update {
                    it.copy(
                        previewWaypoints = preview,
                        isBuilding = false,
                        buildError =
                            if (preview.size < 2) {
                                context.getString(R.string.route_paste_could_not_build_route)
                            } else {
                                null
                            },
                    )
                }
            }
        }

        fun saveRoute(name: String) {
            val trimmed = name.trim()
            val state = _uiState.value
            if (trimmed.isEmpty() || !state.canSave) return
            viewModelScope.launch {
                val route =
                    buildRouteFromPositions(
                        name = trimmed,
                        // Planting saves the stop centers; replay expands the circles at start.
                        positions = if (state.plantingEnabled) orderedPoints(state) else state.previewWaypoints,
                        routeType = state.resolvedRouteType,
                        waitSeconds = if (state.buildMode == PasteBuildMode.TELEPORT) state.teleportWaitSeconds else 0,
                        plantingRadiusMeters = if (state.plantingEnabled) clampPlantingRadius(state.plantingRadiusMeters) else null,
                    )
                routeRepository.insertRoute(route)
                _uiState.update { it.copy(saved = true) }
            }
        }

        private fun orderedPoints(state: PasteCoordinatesUiState): List<LatLng> =
            if (state.pointOrder == PastePointOrder.PROXIMITY) {
                orderByProximity(state.cleanedPoints)
            } else {
                state.cleanedPoints
            }

        private suspend fun buildWalkablePath(points: List<LatLng>): List<LatLng> {
            val legs = mutableListOf<List<LatLng>>()
            var fallbackCount = 0
            val totalLegs = points.size - 1
            for (index in 0 until totalLegs) {
                val from = points[index]
                val to = points[index + 1]
                val leg =
                    osrmClient.resolveRoute(
                        OsrmClient.PROFILE_FOOT,
                        from,
                        to,
                        followRoads = true,
                    ) {
                        fallbackCount++
                    }
                legs += if (leg.size >= 2) leg else listOf(from, to)
            }
            routingErrorReporter.reportRoadFollowingFallbacks(fallbackCount, totalLegs)
            return flattenRouteLegs(legs)
        }

        private suspend fun buildPlantingPath(
            centers: List<LatLng>,
            state: PasteCoordinatesUiState,
        ): List<LatLng> {
            val radius = clampPlantingRadius(state.plantingRadiusMeters)
            // Preview only: the saved route keeps the centers. TELEPORT hops between circles with no connector.
            if (state.buildMode == PasteBuildMode.TELEPORT) return plantingRings(centers, radius).flatten()
            if (state.buildMode == PasteBuildMode.AS_IS) return buildPlantingWaypoints(centers, radius)
            val rings = plantingRings(centers, radius)
            if (rings.size <= 1) return rings.firstOrNull().orEmpty()
            var fallbackCount = 0
            val connectors =
                rings.zipWithNext { from, to ->
                    osrmClient.resolveRoute(
                        OsrmClient.PROFILE_FOOT,
                        from.last(),
                        to.first(),
                        followRoads = true,
                    ) {
                        fallbackCount++
                    }
                }
            routingErrorReporter.reportRoadFollowingFallbacks(fallbackCount, connectors.size)
            return stitchRingsWithConnectors(rings, connectors)
        }
    }
