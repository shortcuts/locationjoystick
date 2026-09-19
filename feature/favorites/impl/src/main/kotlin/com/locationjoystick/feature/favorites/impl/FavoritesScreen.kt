package com.locationjoystick.feature.favorites.impl

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locationjoystick.core.common.util.formatCapturedPoint
import com.locationjoystick.core.common.util.isValidLatLng
import com.locationjoystick.core.common.util.shareCurrentLocationCoordinates
import com.locationjoystick.core.common.util.toLocaleDoubleOrNull
import com.locationjoystick.core.data.CooldownState
import com.locationjoystick.core.data.toBadgeText
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.component.CooldownAdvisoryBadge
import com.locationjoystick.core.designsystem.component.DeleteItemType
import com.locationjoystick.core.designsystem.component.EmptyState
import com.locationjoystick.core.designsystem.component.ListSearchField
import com.locationjoystick.core.designsystem.component.LjActionSheetRow
import com.locationjoystick.core.designsystem.component.LjDeleteConfirmDialog
import com.locationjoystick.core.designsystem.component.LjListItemCard
import com.locationjoystick.core.designsystem.component.LjListItemCardSkeletonList
import com.locationjoystick.core.designsystem.component.LjOverflowMenuSectionLabel
import com.locationjoystick.core.designsystem.component.LjPrimaryButton
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.designsystem.component.LjTextButton
import com.locationjoystick.core.designsystem.component.SavedItemSortMenu
import com.locationjoystick.core.designsystem.component.WideContentClamp
import com.locationjoystick.core.designsystem.component.rememberLjSheetState
import com.locationjoystick.core.location.rememberSpoofToggleState
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.matchesSearch
import com.locationjoystick.feature.favorites.impl.R

@Composable
fun FavoritesRoute(
    viewModel: FavoritesViewModel,
    onNavigateToMapPicker: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cooldownStates by viewModel.cooldownStates.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val spoofToggle = rememberSpoofToggleState()

    FavoritesScreen(
        uiState = uiState,
        cooldownStates = cooldownStates,
        snackbarHostState = snackbarHostState,
        onTeleport = viewModel::teleportTo,
        onSetPendingDeleteId = viewModel::setPendingDeleteId,
        onConfirmDelete = viewModel::confirmDelete,
        onAddFavorite = viewModel::addFavorite,
        onAddFavoriteFromPaste = { name, pasteText -> viewModel.addFavoriteFromPaste(name, pasteText) },
        onUpdateFavorite = viewModel::updateFavorite,
        onNavigateToMapPicker = onNavigateToMapPicker,
        onOpenDrawer = onOpenDrawer,
        isSpoofing = spoofToggle.isSpoofing,
        onToggleSpoofing = spoofToggle.onToggle,
        locationLabel = spoofToggle.locationLabel,
        onSortModeSelected = viewModel::setSortMode,
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
        onAddFavoriteFromPaste = { _, _ -> false },
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
    onAddFavoriteFromPaste: (String, String) -> Boolean = { _, _ -> false },
    onUpdateFavorite: (String, String, Double, Double) -> Unit,
    cooldownStates: Map<String, CooldownState> = emptyMap(),
    onNavigateToMapPicker: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    isSpoofing: Boolean = false,
    onToggleSpoofing: () -> Unit = {},
    locationLabel: String? = null,
    onSortModeSelected: (com.locationjoystick.core.model.SavedItemSortMode) -> Unit = {},
    getCurrentPosition: () -> com.locationjoystick.core.model.LatLng? = { null },
    bottomBar: @Composable () -> Unit = {},
) {
    var showAddSheet by remember { mutableStateOf(false) }
    var showAddOptionsSheet by remember { mutableStateOf(false) }
    var showPasteSheet by rememberSaveable { mutableStateOf(false) }
    var prefillLat by remember { mutableStateOf("") }
    var prefillLon by remember { mutableStateOf("") }
    var editingFavorite by remember { mutableStateOf<com.locationjoystick.core.model.FavoriteLocation?>(null) }

    var searchQuery by remember { mutableStateOf("") }

    LjScaffold(
        title = stringResource(R.string.favorites_screen_title),
        isSpoofing = isSpoofing,
        onToggleSpoofing = onToggleSpoofing,
        locationLabel = locationLabel,
        onNavigationClick = onOpenDrawer,
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        actions = {
            val context = LocalContext.current
            SavedItemSortMenu(selected = uiState.sortMode, onSelected = onSortModeSelected)
            IconButton(
                onClick = { shareCurrentLocationCoordinates(context, getCurrentPosition()) },
            ) {
                Icon(LjIcons.Share, contentDescription = stringResource(R.string.favorites_screen_share_current_location))
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddOptionsSheet = true }) {
                Icon(LjIcons.Add, contentDescription = stringResource(R.string.favorites_screen_add_favorite_cd))
            }
        },
    ) { scaffoldPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding),
        ) {
            WideContentClamp(modifier = Modifier.fillMaxSize()) {
                if (uiState.favorites.isNotEmpty()) {
                    ListSearchField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        label = stringResource(R.string.favorites_screen_search_favorites),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                val filteredFavorites =
                    remember(uiState.favorites, searchQuery) {
                        uiState.favorites.filter { it.matchesSearch(searchQuery) }
                    }

                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        uiState.isLoading -> {
                            LjListItemCardSkeletonList()
                        }

                        uiState.favorites.isEmpty() -> {
                            EmptyState(
                                icon = LjIcons.LocationOn,
                                message = stringResource(R.string.favorites_no_saved_locations_yet),
                                modifier = Modifier.align(Alignment.Center),
                                action = {
                                    LjPrimaryButton(
                                        text = stringResource(R.string.favorites_add_a_favorite),
                                        onClick = { showAddOptionsSheet = true },
                                    )
                                },
                            )
                        }

                        filteredFavorites.isEmpty() -> {
                            EmptyState(
                                icon = LjIcons.Search,
                                message = stringResource(R.string.favorites_no_favorites_match_your_search),
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }

                        else -> {
                            val grouped = remember(filteredFavorites) { filteredFavorites.groupBy { it.category } }
                            val orderedKeys =
                                remember(grouped) { grouped.keys.sortedWith(compareBy({ it == null }, { it ?: "" })) }

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                orderedKeys.forEach { category ->
                                    if (category != null) {
                                        item(key = "header_$category") {
                                            Text(
                                                text = category,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                    items(
                                        items = grouped.getValue(category),
                                        key = { it.id },
                                    ) { favorite ->
                                        FavoriteCard(
                                            modifier = Modifier.animateItem(),
                                            favorite = favorite,
                                            cooldownState = cooldownStates[favorite.id] ?: CooldownState.Ready,
                                            currentPosition = getCurrentPosition(),
                                            onRowClick = { onTeleport(favorite) },
                                            onEdit = { editingFavorite = it },
                                            onDelete = { onSetPendingDeleteId(it.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddOptionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddOptionsSheet = false },
            sheetState = rememberLjSheetState(),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.favorites_screen_add_a_favorite), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                LjActionSheetRow(
                    icon = LjIcons.Map,
                    title = stringResource(R.string.favorites_screen_from_map),
                    onClick = {
                        showAddOptionsSheet = false
                        onNavigateToMapPicker()
                    },
                )
                LjActionSheetRow(
                    icon = LjIcons.Add,
                    title = stringResource(R.string.favorites_screen_from_coordinates),
                    onClick = {
                        showAddOptionsSheet = false
                        prefillLat = ""
                        prefillLon = ""
                        showAddSheet = true
                    },
                )
                LjActionSheetRow(
                    icon = LjIcons.ContentPaste,
                    title = stringResource(R.string.favorites_screen_paste_coordinates),
                    onClick = {
                        showAddOptionsSheet = false
                        showPasteSheet = true
                    },
                )
                LjActionSheetRow(
                    icon = LjIcons.LocationOn,
                    title = stringResource(R.string.favorites_screen_use_current_location),
                    onClick = {
                        showAddOptionsSheet = false
                        val pos = getCurrentPosition()
                        prefillLat = pos?.latitude?.toString() ?: ""
                        prefillLon = pos?.longitude?.toString() ?: ""
                        showAddSheet = true
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = rememberLjSheetState(),
            containerColor = MaterialTheme.colorScheme.surface,
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

    if (showPasteSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPasteSheet = false },
            sheetState = rememberLjSheetState(),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            PasteFavoriteSheet(
                onDismiss = { showPasteSheet = false },
                onAdd = { name, pasteText ->
                    if (onAddFavoriteFromPaste(name, pasteText)) {
                        showPasteSheet = false
                        true
                    } else {
                        false
                    }
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
            LjDeleteConfirmDialog(
                name = favorite.name,
                itemType = DeleteItemType.FAVORITE,
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
    cooldownState: CooldownState,
    currentPosition: LatLng?,
    onRowClick: (com.locationjoystick.core.model.FavoriteLocation) -> Unit,
    onEdit: (com.locationjoystick.core.model.FavoriteLocation) -> Unit,
    onDelete: (com.locationjoystick.core.model.FavoriteLocation) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val coordText = formatCapturedPoint(favorite.position)
    val copiedCoordinatesMessage = stringResource(R.string.favorites_screen_copied_coordinates, coordText)

    LjListItemCard(
        modifier = modifier,
        onClick = { onRowClick(favorite) },
        trailing = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(LjIcons.MoreVert, contentDescription = stringResource(R.string.favorites_screen_more_options_cd))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.favorites_screen_edit)) },
                        onClick = {
                            onEdit(favorite)
                            menuExpanded = false
                        },
                        leadingIcon = { Icon(LjIcons.Edit, null) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.favorites_screen_copy_coordinates)) },
                        onClick = {
                            clipboard.setText(AnnotatedString(coordText))
                            Toast.makeText(context, copiedCoordinatesMessage, Toast.LENGTH_SHORT).show()
                            menuExpanded = false
                        },
                        leadingIcon = { Icon(LjIcons.ContentCopy, null) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.favorites_screen_send_as_message)) },
                        onClick = {
                            val shareIntent =
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, coordText)
                                }
                            context.startActivity(Intent.createChooser(shareIntent, null))
                            menuExpanded = false
                        },
                        leadingIcon = { Icon(LjIcons.Share, null) },
                    )
                    LjOverflowMenuSectionLabel(stringResource(R.string.favorites_screen_danger))
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.favorites_screen_delete), color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            onDelete(favorite)
                            menuExpanded = false
                        },
                        leadingIcon = {
                            Icon(
                                LjIcons.Delete,
                                null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                    )
                }
            }
        },
    ) {
        Text(favorite.name, style = MaterialTheme.typography.titleMedium)
        Text(
            "${String.format("%.4f", favorite.position.latitude)}, ${String.format("%.4f", favorite.position.longitude)}",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(6.dp))
        CooldownAdvisoryBadge(cooldownState.toBadgeText(currentPosition, favorite.position))
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
    val latVal = lat.toLocaleDoubleOrNull()
    val lonVal = lon.toLocaleDoubleOrNull()
    val isValid = name.isNotEmpty() && isValidLatLng(latVal, lonVal)

    WideContentClamp(
        modifier = Modifier.fillMaxWidth(),
        contentModifier = Modifier.fillMaxWidth().padding(16.dp).imePadding(),
    ) {
        Text(
            stringResource(R.string.favorites_screen_add_favorite_location),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.common_name)) },
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
                label = { Text(stringResource(R.string.favorites_screen_latitude)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            OutlinedTextField(
                value = lon,
                onValueChange = { lon = it },
                label = { Text(stringResource(R.string.favorites_screen_longitude)) },
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
            LjTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
            TextButton(
                onClick = { onAdd(name, latVal!!, lonVal!!) },
                enabled = isValid,
            ) {
                Text(stringResource(R.string.favorites_screen_save))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PasteFavoriteSheetPreview() {
    PasteFavoriteSheet(
        onDismiss = {},
        onAdd = { _, _ -> true },
    )
}

@Composable
private fun PasteFavoriteSheet(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Boolean,
) {
    var name by rememberSaveable { mutableStateOf("") }
    val invalidMessage = stringResource(R.string.favorites_no_coordinates)
    var pasteText by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .imePadding(),
    ) {
        Text(
            stringResource(R.string.favorites_screen_add_favorite_location),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.favorites_screen_name)) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
        )

        OutlinedTextField(
            value = pasteText,
            onValueChange = {
                pasteText = it
                error = null
            },
            label = { Text(stringResource(R.string.favorites_screen_coordinates)) },
            placeholder = { Text(stringResource(R.string.favorite_coordinate_example)) },
            supportingText = {
                Text(error ?: stringResource(R.string.favorites_decimal_or_degrees_with_n_s_and_e_w))
            },
            isError = error != null,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            trailingIcon = {
                IconButton(
                    onClick = {
                        val text = clipboard.getText()?.text
                        if (!text.isNullOrBlank()) {
                            pasteText = text
                            error = null
                        }
                    },
                ) {
                    Icon(LjIcons.ContentPaste, contentDescription = stringResource(R.string.favorites_screen_paste_from_clipboard))
                }
            },
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.favorites_screen_cancel))
            }
            TextButton(
                onClick = {
                    if (!onAdd(name, pasteText)) {
                        error = invalidMessage
                    }
                },
                enabled = name.isNotEmpty() && pasteText.isNotBlank(),
            ) {
                Text(stringResource(R.string.common_save))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditFavoriteDialog(
    favorite: com.locationjoystick.core.model.FavoriteLocation,
    onDismiss: () -> Unit,
    onSave: (String, Double, Double) -> Unit,
) {
    var name by remember(favorite) { mutableStateOf(favorite.name) }
    var lat by remember(favorite) { mutableStateOf(favorite.position.latitude.toString()) }
    var lon by remember(favorite) { mutableStateOf(favorite.position.longitude.toString()) }
    val latVal = lat.toLocaleDoubleOrNull()
    val lonVal = lon.toLocaleDoubleOrNull()
    val isValid = name.isNotEmpty() && isValidLatLng(latVal, lonVal)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberLjSheetState()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.favorites_screen_edit_favorite), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.common_name)) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
            )
            OutlinedTextField(
                value = lat,
                onValueChange = { lat = it },
                label = { Text(stringResource(R.string.favorites_screen_latitude_2)) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
            )
            OutlinedTextField(
                value = lon,
                onValueChange = { lon = it },
                label = { Text(stringResource(R.string.favorites_screen_longitude_2)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                LjTextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
                LjTextButton(
                    onClick = { onSave(name, latVal!!, lonVal!!) },
                    enabled = isValid,
                ) {
                    Text(stringResource(R.string.common_save))
                }
            }
        }
    }
}
