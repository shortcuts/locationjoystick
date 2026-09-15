package com.locationjoystick.feature.routes.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.buildPlantingWaypoints
import com.locationjoystick.core.common.util.clampPlantingRadius
import com.locationjoystick.core.common.util.flattenRouteLegs
import com.locationjoystick.core.common.util.orderByProximity
import com.locationjoystick.core.common.util.parsePastedCoordinates
import com.locationjoystick.core.common.util.plantingRings
import com.locationjoystick.core.common.util.stitchRingsWithConnectors
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.core.model.Waypoint
import com.locationjoystick.core.routing.OsrmClient
import com.locationjoystick.core.routing.RoutingErrorReporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class PastePointOrder { ORIGINAL, PROXIMITY }

enum class PasteBuildMode { AS_IS, WALKABLE, PLANTING }

enum class PlantingTravel { STRAIGHT, ROADS }

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
    val plantingTravel: PlantingTravel = PlantingTravel.STRAIGHT,
    val previewWaypoints: List<LatLng> = emptyList(),
    val isBuilding: Boolean = false,
    val loadError: String? = null,
    val buildError: String? = null,
    val saved: Boolean = false,
) {
    val canBuild: Boolean
        get() =
            when (buildMode) {
                PasteBuildMode.AS_IS, PasteBuildMode.WALKABLE -> cleanedPoints.size >= 2
                PasteBuildMode.PLANTING -> cleanedPoints.isNotEmpty()
            }

    val canSave: Boolean
        get() = previewWaypoints.size >= 2 && !isBuilding

    val resolvedRouteType: RouteType
        get() =
            when {
                buildMode == PasteBuildMode.WALKABLE -> RouteType.GUIDED
                buildMode == PasteBuildMode.PLANTING && plantingTravel == PlantingTravel.ROADS ->
                    RouteType.GUIDED
                else -> RouteType.STRAIGHT
            }
}

internal fun buildRouteFromPositions(
    name: String,
    positions: List<LatLng>,
    routeType: RouteType,
    nowMs: Long = System.currentTimeMillis(),
    id: String = UUID.randomUUID().toString(),
): Route {
    val waypoints =
        positions.mapIndexed { index, latLng ->
            Waypoint(
                id = UUID.randomUUID().toString(),
                position = latLng,
                orderIndex = index,
            )
        }
    return Route(
        id = id,
        name = name,
        waypoints = waypoints,
        isLooping = false,
        routeType = routeType,
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
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(PasteCoordinatesUiState())
        val uiState: StateFlow<PasteCoordinatesUiState> = _uiState.asStateFlow()

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

        fun onPlantingTravelChange(travel: PlantingTravel) {
            _uiState.update { it.copy(plantingTravel = travel, previewWaypoints = emptyList()) }
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
                        loadError = "No valid coordinates found in that text.",
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
                    if (state.buildMode == PasteBuildMode.PLANTING) {
                        "Need at least 1 point for Planting mode."
                    } else {
                        "Need at least 2 points for this mode."
                    }
                _uiState.update { it.copy(buildError = message) }
                return
            }
            viewModelScope.launch {
                _uiState.update { it.copy(isBuilding = true, buildError = null) }
                val ordered = orderedPoints(state)
                val preview =
                    when (state.buildMode) {
                        PasteBuildMode.AS_IS -> ordered
                        PasteBuildMode.WALKABLE -> buildWalkablePath(ordered)
                        PasteBuildMode.PLANTING -> buildPlantingPath(ordered, state)
                    }
                _uiState.update {
                    it.copy(
                        previewWaypoints = preview,
                        isBuilding = false,
                        buildError = if (preview.size < 2) "Could not build a route from those points." else null,
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
                        positions = state.previewWaypoints,
                        routeType = state.resolvedRouteType,
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
            if (state.plantingTravel == PlantingTravel.STRAIGHT) {
                return buildPlantingWaypoints(centers, radius)
            }
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
