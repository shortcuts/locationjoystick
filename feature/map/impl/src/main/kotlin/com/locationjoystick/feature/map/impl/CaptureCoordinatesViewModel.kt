package com.locationjoystick.feature.map.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.common.util.orderedCapturedPoints
import com.locationjoystick.core.data.CaptureCoordinatesRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.core.model.Waypoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class CapturePointOrder { ORIGINAL, PROXIMITY }

data class CaptureCoordinatesUiState(
    val captureModeEnabled: Boolean = false,
    val captureEnabled: Boolean = false,
    val jumpEnabled: Boolean = false,
    val previousBrowserPackage: String? = null,
    val points: List<LatLng> = emptyList(),
    val pointOrder: CapturePointOrder = CapturePointOrder.PROXIMITY,
    val routeName: String = "",
    val saved: Boolean = false,
    val saveError: Int? = null,
) {
    val canSave: Boolean
        get() = points.size >= 2 && routeName.trim().isNotEmpty()

    val orderedPoints: List<LatLng>
        get() = orderedCapturedPoints(points, pointOrder == CapturePointOrder.PROXIMITY)
}

@HiltViewModel
class CaptureCoordinatesViewModel
    @Inject
    constructor(
        private val captureRepository: CaptureCoordinatesRepository,
        private val routeRepository: RouteRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(CaptureCoordinatesUiState())
        val uiState: StateFlow<CaptureCoordinatesUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                captureRepository.captureModeEnabled.collect { enabled ->
                    _uiState.update { it.copy(captureModeEnabled = enabled) }
                }
            }
            viewModelScope.launch {
                captureRepository.captureEnabled.collect { enabled ->
                    _uiState.update { it.copy(captureEnabled = enabled) }
                }
            }
            viewModelScope.launch {
                captureRepository.points.collect { points ->
                    _uiState.update { it.copy(points = points) }
                }
            }
            viewModelScope.launch {
                captureRepository.jumpEnabled.collect { enabled ->
                    _uiState.update { it.copy(jumpEnabled = enabled) }
                }
            }
            viewModelScope.launch {
                captureRepository.previousBrowserPackage.collect { packageName ->
                    _uiState.update { it.copy(previousBrowserPackage = packageName) }
                }
            }
        }

        fun setCaptureModeEnabled(enabled: Boolean) {
            viewModelScope.launch { captureRepository.setCaptureModeEnabled(enabled) }
        }

        fun setCaptureEnabled(enabled: Boolean) {
            viewModelScope.launch { captureRepository.setCaptureEnabled(enabled) }
        }

        fun setJumpEnabled(enabled: Boolean) {
            viewModelScope.launch { captureRepository.setJumpEnabled(enabled) }
        }

        fun rememberPreviousBrowser(packageName: String?) {
            val trimmed = packageName?.trim().orEmpty()
            if (trimmed.isEmpty()) return
            viewModelScope.launch { captureRepository.setPreviousBrowserPackage(trimmed) }
        }

        fun onRouteNameChange(name: String) {
            _uiState.update { it.copy(routeName = name, saved = false, saveError = null) }
        }

        fun onPointOrderChange(order: CapturePointOrder) {
            _uiState.update { it.copy(pointOrder = order, saved = false, saveError = null) }
        }

        fun clearPoints() {
            viewModelScope.launch { captureRepository.clearPoints() }
        }

        fun removeLast() {
            viewModelScope.launch { captureRepository.removeLast() }
        }

        fun saveRoute() {
            val state = _uiState.value
            val name = state.routeName.trim()
            if (name.isEmpty() || state.points.size < 2) {
                _uiState.update { it.copy(saveError = R.string.capture_invalid_route) }
                return
            }
            viewModelScope.launch {
                val now = System.currentTimeMillis()
                val route =
                    Route(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        waypoints =
                            state.orderedPoints.mapIndexed { index, latLng ->
                                Waypoint(
                                    id = UUID.randomUUID().toString(),
                                    position = latLng,
                                    orderIndex = index,
                                )
                            },
                        isLooping = false,
                        routeType = RouteType.STRAIGHT,
                        createdAt = now,
                        updatedAt = now,
                    )
                routeRepository.insertRoute(route)
                _uiState.update { it.copy(saved = true, saveError = null) }
            }
        }
    }
