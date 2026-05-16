package com.locationjoystick.feature.routes.impl

import android.util.Log
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.designsystem.component.LjTopBar
import com.locationjoystick.core.model.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RouteDetailViewModel
    @Inject
    constructor(
        private val routeRepository: RouteRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        companion object {
            private const val TAG = "RouteDetailViewModel"
        }

        private val routeId: String = checkNotNull(savedStateHandle["routeId"])

        val route: StateFlow<Route?> =
            routeRepository.getRouteWithWaypoints(routeId).stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

        private val _nameError = MutableStateFlow(false)
        val nameError: StateFlow<Boolean> = _nameError.asStateFlow()

        fun removeWaypoint(waypointId: String) {
            viewModelScope.launch {
                routeRepository.removeWaypoint(waypointId)
            }
        }

        fun deleteRoute() {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    routeRepository.deleteRoute(routeId)
                } catch (e: Exception) {
                    Log.e(TAG, "delete failed", e)
                }
            }
        }

        suspend fun renameRoute(name: String) {
            if (name.isBlank()) {
                _nameError.value = true
                return
            }
            _nameError.value = false
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    routeRepository.renameRoute(routeId, name)
                } catch (e: Exception) {
                    Log.e(TAG, "rename failed", e)
                    _nameError.value = true
                }
            }
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailScreen(
    routeId: String,
    onNavigateBack: () -> Unit,
    onOpenDrawer: () -> Unit = {},
    viewModel: RouteDetailViewModel = hiltViewModel(),
) {
    val route by viewModel.route.collectAsStateWithLifecycle()
    val nameError by viewModel.nameError.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    var editedName by remember { mutableStateOf("") }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    if (route != null && editedName.isEmpty()) {
        editedName = route!!.name
    }

    // Save-on-back handler
    BackHandler {
        if (editedName != route?.name && editedName.isNotBlank()) {
            coroutineScope.launch {
                viewModel.renameRoute(editedName)
                onNavigateBack()
            }
        } else {
            onNavigateBack()
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Route?") },
            text = { Text("Are you sure you want to delete this route? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        coroutineScope.launch {
                            viewModel.deleteRoute()
                            onNavigateBack()
                        }
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LjTopBar(
            title = "Route Details",
            onNavigationClick = onOpenDrawer,
            actions = {
                IconButton(onClick = { showDeleteConfirmation = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete route")
                }
            },
        )

        Box(modifier = Modifier.fillMaxSize()) {
            if (route == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                ) {
                    // Editable name field
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = editedName,
                                onValueChange = { editedName = it },
                                label = { Text("Route name") },
                                isError = nameError,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            if (nameError) {
                                Text(
                                    "Name cannot be empty",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }

                    // Numbered waypoints
                    items(route!!.waypoints, key = { it.id }) { waypoint ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp, top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Waypoint ${waypoint.orderIndex + 1}",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    "${String.format("%.4f", waypoint.position.latitude)}, " +
                                        "${String.format("%.4f", waypoint.position.longitude)}",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodySmall,
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
