package com.locationjoystick.feature.routes.impl

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MapTileSource
import com.locationjoystick.core.model.RecentSearch
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.core.model.Waypoint
import com.locationjoystick.core.routing.OsrmClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.sin

private fun Double.toRadians(): Double = Math.toRadians(this)

private const val TAG = "RouteCreatorViewModel"

data class CreatorState(
    /** Each placed point paired with its wait duration — only nonzero for [RouteType.TELEPORT]. */
    val placedWaypoints: List<Pair<LatLng, Int>> = emptyList(),
    val segments: List<List<LatLng>> = emptyList(),
    val totalDistanceMeters: Double = 0.0,
    val isLoadingSegment: Boolean = false,
    val osrmError: Boolean = false,
) {
    /** Positions only, in placement order. */
    val waypoints: List<LatLng> get() = placedWaypoints.map { it.first }
}

@HiltViewModel
class RouteCreatorViewModel
    @Inject
    constructor(
        private val routeRepository: RouteRepository,
        private val osrmClient: OsrmClient,
        private val locationRepository: LocationRepository,
        private val favoriteRepository: FavoriteRepository,
        private val settingsRepository: SettingsRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        val routeType: RouteType =
            RouteType.valueOf(
                savedStateHandle.get<String>("routeType") ?: RouteType.STRAIGHT.name,
            )

        private val _state = MutableStateFlow(CreatorState())
        val state: StateFlow<CreatorState> = _state.asStateFlow()

        val currentPosition: LatLng?
            get() = locationRepository.currentPosition.value

        val livePosition: StateFlow<LatLng?> = locationRepository.currentPosition

        val favorites: StateFlow<List<FavoriteLocation>> =
            favoriteRepository
                .getFavorites()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        val recentSearches: StateFlow<List<RecentSearch>> =
            settingsRepository
                .getRecentSearches()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        val mapTileSource: StateFlow<MapTileSource> =
            settingsRepository
                .getMapTileSource()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapTileSource.DEFAULT)

        fun addRecentSearch(
            displayName: String,
            lat: Double,
            lon: Double,
        ) {
            viewModelScope.launch { settingsRepository.addRecentSearch(displayName, lat, lon) }
        }

        fun addWaypoint(
            latLng: LatLng,
            waitSeconds: Int = 0,
        ) {
            val currentPlaced = _state.value.placedWaypoints
            val newPlaced = currentPlaced + (latLng to waitSeconds)

            if (newPlaced.size < 2) {
                _state.value =
                    _state.value.copy(
                        placedWaypoints = newPlaced,
                        segments = emptyList(),
                        totalDistanceMeters = 0.0,
                    )
                return
            }

            val lastWaypoint = currentPlaced.last().first

            if (routeType == RouteType.STRAIGHT || routeType == RouteType.TELEPORT) {
                val segment = listOf(lastWaypoint, latLng)
                val currentSegments = _state.value.segments + listOf(segment)
                val distance =
                    currentSegments.sumOf { seg ->
                        seg.zipWithNext().sumOf { (a, b) -> haversineMeters(a, b) }
                    }
                _state.value =
                    _state.value.copy(
                        placedWaypoints = newPlaced,
                        segments = currentSegments,
                        totalDistanceMeters = distance,
                    )
                return
            }

            _state.value = _state.value.copy(isLoadingSegment = true, osrmError = false)

            viewModelScope.launch {
                try {
                    val segmentResult = osrmClient.getRoute(OsrmClient.PROFILE_FOOT, listOf(lastWaypoint, latLng))
                    val segment =
                        segmentResult.getOrElse {
                            Log.e(TAG, "OSRM failed for guided route", it)
                            _state.value = _state.value.copy(isLoadingSegment = false, osrmError = true)
                            return@launch
                        }
                    val currentSegments = _state.value.segments + listOf(segment)
                    val distance =
                        currentSegments.sumOf { seg ->
                            seg.zipWithNext().sumOf { (a, b) -> haversineMeters(a, b) }
                        }
                    _state.value =
                        _state.value.copy(
                            placedWaypoints = newPlaced,
                            segments = currentSegments,
                            totalDistanceMeters = distance,
                            isLoadingSegment = false,
                            osrmError = false,
                        )
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching OSRM route", e)
                    _state.value = _state.value.copy(isLoadingSegment = false, osrmError = true)
                }
            }
        }

        fun undoLastWaypoint() {
            val current = _state.value
            if (current.placedWaypoints.isEmpty()) return

            val newPlaced = current.placedWaypoints.dropLast(1)
            val newSegments =
                if (current.segments.isNotEmpty()) {
                    current.segments.dropLast(1)
                } else {
                    emptyList()
                }

            val distance =
                newSegments.sumOf { seg ->
                    seg.zipWithNext().sumOf { (a, b) -> haversineMeters(a, b) }
                }

            _state.value =
                current.copy(
                    placedWaypoints = newPlaced,
                    segments = newSegments,
                    totalDistanceMeters = distance,
                )
        }

        fun saveRoute(name: String) {
            val current = _state.value
            if (current.placedWaypoints.size < 2) return

            val uuid = UUID.randomUUID().toString()
            // For GUIDED routes, persist the dense OSRM-interpolated segment points so replay
            // is immutable and never needs to re-fetch from OSRM.
            val waypointPositions =
                if (routeType == RouteType.GUIDED && current.segments.isNotEmpty()) {
                    current.segments.flatMapIndexed { i, seg -> if (i == 0) seg else seg.drop(1) }
                } else {
                    current.waypoints
                }
            val waypoints =
                waypointPositions.mapIndexed { idx, latLng ->
                    Waypoint(
                        id = UUID.randomUUID().toString(),
                        position = latLng,
                        orderIndex = idx,
                        waitSeconds = current.placedWaypoints.getOrNull(idx)?.second ?: 0,
                    )
                }

            val route =
                Route(
                    id = uuid,
                    name = name,
                    waypoints = waypoints,
                    isLooping = false,
                    routeType = routeType,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )

            viewModelScope.launch {
                routeRepository.insertRoute(route)
            }
        }

        private fun haversineMeters(
            a: LatLng,
            b: LatLng,
        ): Double {
            val earthRadius = AppConstants.LocationConstants.EARTH_RADIUS_METERS
            val dLat = (b.latitude - a.latitude).toRadians()
            val dLon = (b.longitude - a.longitude).toRadians()
            val sinDLat = sin(dLat / 2)
            val sinDLon = sin(dLon / 2)
            val h = sinDLat * sinDLat + cos(a.latitude.toRadians()) * cos(b.latitude.toRadians()) * sinDLon * sinDLon
            return 2 * earthRadius * kotlin.math.asin(kotlin.math.sqrt(h))
        }
    }
