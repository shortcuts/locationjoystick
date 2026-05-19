package com.locationjoystick.feature.favorites.impl

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locationjoystick.core.designsystem.component.EmptyState
import com.locationjoystick.core.designsystem.component.LjScaffold

@Composable
fun FavoritesRoute(
    viewModel: FavoritesViewModel,
    onNavigateToMapPicker: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    FavoritesScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onTeleport = viewModel::teleportTo,
        onSetPendingDeleteId = viewModel::setPendingDeleteId,
        onConfirmDelete = viewModel::confirmDelete,
        onAddFavorite = viewModel::addFavorite,
        onUpdateFavorite = viewModel::updateFavorite,
        onNavigateToMapPicker = onNavigateToMapPicker,
        onOpenDrawer = onOpenDrawer,
        getCurrentPosition = { viewModel.currentPosition },
        bottomBar = bottomBar,
    )
}

@Preview(showBackground = true)
@Composable
private fun FavoritesScreenPreview() {
    FavoritesScreen(
        uiState = FavoritesUiState(),
        snackbarHostState = SnackbarHostState(),
        onTeleport = {},
        onSetPendingDeleteId = {},
        onConfirmDelete = {},
        onAddFavorite = { _, _, _ -> },
        onUpdateFavorite = { _, _, _, _ -> },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FavoritesScreen(
    uiState: FavoritesUiState,
    snackbarHostState: SnackbarHostState,
    onTeleport: (com.locationjoystick.core.model.FavoriteLocation) -> Unit,
    onSetPendingDeleteId: (String?) -> Unit,
    onConfirmDelete: () -> Unit,
    onAddFavorite: (String, Double, Double) -> Unit,
    onUpdateFavorite: (String, String, Double, Double) -> Unit,
    onNavigateToMapPicker: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    getCurrentPosition: () -> com.locationjoystick.core.model.LatLng? = { null },
    bottomBar: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    var showAddSheet by remember { mutableStateOf(false) }
    var prefillLat by remember { mutableStateOf("") }
    var prefillLon by remember { mutableStateOf("") }
    var editingFavorite by remember { mutableStateOf<com.locationjoystick.core.model.FavoriteLocation?>(null) }

    var showAddMenu by remember { mutableStateOf(false) }

    LjScaffold(
        title = "Lj",
        onNavigationClick = onOpenDrawer,
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        actions = {
            IconButton(onClick = { showAddMenu = !showAddMenu }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Add options")
            }
            DropdownMenu(
                expanded = showAddMenu,
                onDismissRequest = { showAddMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text("from map") },
                    onClick = {
                        onNavigateToMapPicker()
                        showAddMenu = false
                    },
                    leadingIcon = { Icon(Icons.Rounded.Map, null) },
                )
                DropdownMenuItem(
                    text = { Text("from coordinates") },
                    onClick = {
                        prefillLat = ""
                        prefillLon = ""
                        showAddSheet = true
                        showAddMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.Add, null) },
                )
                DropdownMenuItem(
                    text = { Text("from current location") },
                    onClick = {
                        val pos = getCurrentPosition()
                        prefillLat = pos?.latitude?.toString() ?: ""
                        prefillLon = pos?.longitude?.toString() ?: ""
                        showAddSheet = true
                        showAddMenu = false
                    },
                    leadingIcon = { Icon(Icons.Rounded.LocationOn, null) },
                )
            }
        },
    ) { scaffoldPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding),
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                uiState.favorites.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Rounded.LocationOn,
                        message = "No saved locations yet",
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        items(
                            items = uiState.favorites,
                            key = { it.id },
                        ) { favorite ->
                            FavoriteCard(
                                favorite = favorite,
                                onRowClick = {
                                    val coords = "${String.format(
                                        "%.4f",
                                        favorite.position.latitude,
                                    )}, ${String.format("%.4f", favorite.position.longitude)}"
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("coordinates", coords))
                                },
                                onEdit = { editingFavorite = it },
                                onDelete = { onSetPendingDeleteId(it.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
        ) {
            AddFavoriteSheet(
                initialLat = prefillLat,
                initialLon = prefillLon,
                onDismiss = { showAddSheet = false },
                onAdd = { name, lat, lon ->
                    onAddFavorite(name, lat, lon)
                    showAddSheet = false
                },
            )
        }
    }

    editingFavorite?.let { favorite ->
        EditFavoriteDialog(
            favorite = favorite,
            onDismiss = { editingFavorite = null },
            onSave = { name, lat, lon ->
                onUpdateFavorite(favorite.id, name, lat, lon)
                editingFavorite = null
            },
        )
    }

    uiState.pendingDeleteId?.let { favoriteId ->
        val favorite = uiState.favorites.find { it.id == favoriteId }
        if (favorite != null) {
            DeleteConfirmDialog(
                name = favorite.name,
                onDismiss = { onSetPendingDeleteId(null) },
                onConfirm = {
                    onConfirmDelete()
                    onSetPendingDeleteId(null)
                },
            )
        }
    }
}

@Composable
private fun FavoriteCard(
    favorite: com.locationjoystick.core.model.FavoriteLocation,
    onRowClick: (com.locationjoystick.core.model.FavoriteLocation) -> Unit,
    onEdit: (com.locationjoystick.core.model.FavoriteLocation) -> Unit,
    onDelete: (com.locationjoystick.core.model.FavoriteLocation) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .clickable { onRowClick(favorite) }
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(favorite.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${String.format("%.4f", favorite.position.latitude)}, ${String.format("%.4f", favorite.position.longitude)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More options")
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = {
                        onEdit(favorite)
                        menuExpanded = false
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        onDelete(favorite)
                        menuExpanded = false
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                )
            }
        }
    }
}

@Composable
private fun AddFavoriteSheet(
    onDismiss: () -> Unit,
    onAdd: (String, Double, Double) -> Unit,
    initialLat: String = "",
    initialLon: String = "",
) {
    var name by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf(initialLat) }
    var lon by remember { mutableStateOf(initialLon) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .imePadding(),
    ) {
        Text(
            "Add Favorite Location",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = lat,
                onValueChange = { lat = it },
                label = { Text("Latitude") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            OutlinedTextField(
                value = lon,
                onValueChange = { lon = it },
                label = { Text("Longitude") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            TextButton(
                onClick = {
                    val latVal = lat.toDoubleOrNull()
                    val lonVal = lon.toDoubleOrNull()
                    if (name.isNotEmpty() && latVal != null && lonVal != null) {
                        onAdd(name, latVal, lonVal)
                    }
                },
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun EditFavoriteDialog(
    favorite: com.locationjoystick.core.model.FavoriteLocation,
    onDismiss: () -> Unit,
    onSave: (String, Double, Double) -> Unit,
) {
    var name by remember(favorite) { mutableStateOf(favorite.name) }
    var lat by remember(favorite) { mutableStateOf(favorite.position.latitude.toString()) }
    var lon by remember(favorite) { mutableStateOf(favorite.position.longitude.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Favorite") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = lat,
                    onValueChange = { lat = it },
                    label = { Text("Latitude") },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = lon,
                    onValueChange = { lon = it },
                    label = { Text("Longitude") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val latVal = lat.toDoubleOrNull()
                    val lonVal = lon.toDoubleOrNull()
                    if (name.isNotEmpty() && latVal != null && lonVal != null) {
                        onSave(name, latVal, lonVal)
                    }
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
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
        },
    )
}
