package com.locationjoystick.feature.routes.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.core.ui.component.EmptyState

@Composable
fun RoutesRoute(
    onNavigateToDetail: (String) -> Unit,
    onNavigateToCreate: (RouteType) -> Unit,
    viewModel: RoutesViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    RoutesScreen(
        uiState = uiState,
        playbackState = playbackState,
        onNavigateToDetail = onNavigateToDetail,
        onNavigateToCreate = onNavigateToCreate,
        onDeleteRoute = viewModel::deleteRoute,
        onRenameRoute = viewModel::renameRoute,
        onExportRoute = { route -> viewModel.exportRouteAsGpx(context, route) },
        onStartReplay = { route, isBackward -> viewModel.startReplay(route, isBackward) },
        onPauseReplay = viewModel::pauseReplay,
        onResumeReplay = viewModel::resumeReplay,
        onStopReplay = viewModel::stopReplay,
    )
}

@Composable
internal fun RoutesScreen(
    uiState: RoutesUiState,
    playbackState: RoutePlaybackState,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToCreate: (RouteType) -> Unit,
    onDeleteRoute: (String) -> Unit,
    onRenameRoute: (String, String) -> Unit,
    onExportRoute: (com.locationjoystick.core.model.Route) -> Unit,
    onStartReplay: (com.locationjoystick.core.model.Route, Boolean) -> Unit,
    onPauseReplay: () -> Unit,
    onResumeReplay: () -> Unit,
    onStopReplay: () -> Unit,
) {
    var editingRoute by remember { mutableStateOf<com.locationjoystick.core.model.Route?>(null) }
    var deletingRoute by remember { mutableStateOf<com.locationjoystick.core.model.Route?>(null) }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            uiState.routes.isEmpty() -> {
                EmptyState(
                    icon = Icons.Rounded.Add,
                    message = "No routes yet",
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(
                        items = uiState.routes,
                        key = { it.id }
                    ) { route ->
                        RouteCard(
                            route = route,
                            playbackState = playbackState,
                            onTap = { onNavigateToDetail(route.id) },
                            onEdit = { editingRoute = it },
                            onDelete = { deletingRoute = it },
                            onExport = { onExportRoute(it) },
                            onStartReplay = onStartReplay,
                            onPauseReplay = onPauseReplay,
                            onResumeReplay = onResumeReplay,
                            onStopReplay = onStopReplay,
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExtendedFloatingActionButton(
                onClick = { onNavigateToCreate(RouteType.GUIDED) },
                icon = { Icon(Icons.Rounded.Map, null) },
                text = { Text("+ guided route") }
            )
            ExtendedFloatingActionButton(
                onClick = { onNavigateToCreate(RouteType.STRAIGHT) },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("+ route") }
            )
        }
    }

    editingRoute?.let { route ->
        RenameRouteDialog(
            routeName = route.name,
            onDismiss = { editingRoute = null },
            onSave = { newName ->
                onRenameRoute(route.id, newName)
                editingRoute = null
            }
        )
    }

    deletingRoute?.let { route ->
        DeleteConfirmDialog(
            name = route.name,
            onDismiss = { deletingRoute = null },
            onConfirm = {
                onDeleteRoute(route.id)
                deletingRoute = null
            }
        )
    }
}

@Composable
private fun RouteCard(
    route: com.locationjoystick.core.model.Route,
    playbackState: RoutePlaybackState,
    onTap: () -> Unit,
    onEdit: (com.locationjoystick.core.model.Route) -> Unit,
    onDelete: (com.locationjoystick.core.model.Route) -> Unit,
    onExport: (com.locationjoystick.core.model.Route) -> Unit,
    onStartReplay: (com.locationjoystick.core.model.Route, Boolean) -> Unit,
    onPauseReplay: () -> Unit,
    onResumeReplay: () -> Unit,
    onStopReplay: () -> Unit,
) {
    val isActiveRoute = playbackState.activeRouteId == route.id
    val isPlaying = isActiveRoute && playbackState.isPlaying
    val isPaused = isActiveRoute && playbackState.isPaused

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .clickable { onTap() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(route.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${route.waypoints.size} waypoints",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = { onEdit(route) }) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = { onExport(route) }) {
                Icon(Icons.Default.FileDownload, contentDescription = "Export GPX")
            }
            IconButton(onClick = { onDelete(route) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                isPlaying -> {
                    IconButton(onClick = onPauseReplay) {
                        Icon(Icons.Default.Pause, contentDescription = "Pause")
                    }
                    IconButton(onClick = onStopReplay) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop")
                    }
                }
                isPaused -> {
                    IconButton(onClick = onResumeReplay) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                    }
                    IconButton(onClick = onStopReplay) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop")
                    }
                }
                else -> {
                    IconButton(onClick = { onStartReplay(route, false) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play forward")
                    }
                    IconButton(onClick = { onStartReplay(route, true) }) {
                        Icon(Icons.Default.Replay, contentDescription = "Play backward")
                    }
                }
            }
        }
    }
}

@Composable
private fun RenameRouteDialog(
    routeName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(routeName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Route") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Route name") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotEmpty()) {
                        onSave(name)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DeleteConfirmDialog(
    name: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"$name\"?") },
        text = { Text("This action cannot be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
