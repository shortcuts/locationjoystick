package com.locationjoystick.feature.map.impl

import androidx.annotation.StringRes
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

private fun SaveResult.settle(event: SaveEvent): SaveResult = after(event) ?: this

enum class CapturePointOrder { ORIGINAL, PROXIMITY }

sealed interface SaveResult {
    data object NotSaved : SaveResult

    data object Saved : SaveResult

    data class Failed(
        @StringRes val error: Int,
    ) : SaveResult
}

sealed interface SaveEvent {
    data object Edited : SaveEvent

    data object Succeeded : SaveEvent

    data class Rejected(
        @StringRes val error: Int,
    ) : SaveEvent
}

/** Returns the next result, or null when [event] is illegal in this state. */
fun SaveResult.after(event: SaveEvent): SaveResult? =
    when (event) {
        SaveEvent.Edited -> if (this == SaveResult.NotSaved) null else SaveResult.NotSaved
        SaveEvent.Succeeded -> SaveResult.Saved
        is SaveEvent.Rejected -> SaveResult.Failed(event.error)
    }

val SaveResult.isSaved: Boolean get() = this is SaveResult.Saved
val SaveResult.errorRes: Int? get() = (this as? SaveResult.Failed)?.error

data class CaptureCoordinatesUiState(
    val captureModeEnabled: Boolean = false,
    val captureEnabled: Boolean = false,
    val jumpEnabled: Boolean = false,
    val setupReset: Boolean = false,
    val points: List<LatLng> = emptyList(),
    val pointOrder: CapturePointOrder = CapturePointOrder.PROXIMITY,
    val routeName: String = "",
    val saveResult: SaveResult = SaveResult.NotSaved,
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
                captureRepository.setupReset.collect { reset ->
                    _uiState.update { it.copy(setupReset = reset) }
                }
            }
        }

        val previousBrowserPackage: StateFlow<String?> =
            captureRepository.previousBrowserPackage
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        fun rememberPreviousBrowser(packageName: String?) {
            val trimmed = packageName?.trim().orEmpty()
            if (trimmed.isEmpty()) return
            viewModelScope.launch { captureRepository.setPreviousBrowserPackage(trimmed) }
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

        fun onRouteNameChange(name: String) {
            _uiState.update { it.copy(routeName = name, saveResult = it.saveResult.settle(SaveEvent.Edited)) }
        }

        fun onPointOrderChange(order: CapturePointOrder) {
            _uiState.update { it.copy(pointOrder = order, saveResult = it.saveResult.settle(SaveEvent.Edited)) }
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
                _uiState.update { it.copy(saveResult = it.saveResult.settle(SaveEvent.Rejected(R.string.capture_invalid_route))) }
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
                _uiState.update { it.copy(saveResult = it.saveResult.settle(SaveEvent.Succeeded)) }
            }
        }
    }
