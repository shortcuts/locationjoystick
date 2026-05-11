package com.locationjoystick.feature.routes.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.model.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RouteDetailViewModel @Inject constructor(
    private val routeRepository: RouteRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val routeId: String = checkNotNull(savedStateHandle["routeId"])

    val route: StateFlow<Route?> = routeRepository.getRouteWithWaypoints(routeId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    fun removeWaypoint(waypointId: String) {
        viewModelScope.launch {
            routeRepository.removeWaypoint(waypointId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailScreen(
    routeId: String,
    viewModel: RouteDetailViewModel = hiltViewModel(),
) {
    val route by viewModel.route.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(route?.name ?: "Route Details") },
        )

        Box(modifier = Modifier.fillMaxSize()) {
            if (route == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    items(route!!.waypoints, key = { it.id }) { waypoint ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Waypoint ${waypoint.orderIndex + 1}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "${String.format("%.4f", waypoint.position.latitude)}, " +
                                        "${String.format("%.4f", waypoint.position.longitude)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(onClick = { viewModel.removeWaypoint(waypoint.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove waypoint")
                            }
                        }
                    }
                }
            }
        }
    }
}
