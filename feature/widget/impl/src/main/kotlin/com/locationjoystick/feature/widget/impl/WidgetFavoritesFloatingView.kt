package com.locationjoystick.feature.widget.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.common.util.currentLocationShareText
import com.locationjoystick.core.common.util.shareCurrentLocationCoordinates
import com.locationjoystick.core.data.CooldownState
import com.locationjoystick.core.data.toBadgeText
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.component.FavoriteTargetDetail
import com.locationjoystick.core.designsystem.component.FavoritesList
import com.locationjoystick.core.designsystem.component.LjButton
import com.locationjoystick.core.designsystem.component.LjTextButton
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.SavedItemSortMode
import com.locationjoystick.feature.widget.impl.R

@Composable
internal fun FavoritesFloatingView(
    favorites: List<FavoriteLocation>,
    onDismiss: () -> Unit,
    onTeleport: (FavoriteLocation) -> Unit,
    onWalk: (FavoriteLocation) -> Unit,
    onWalkViaRoads: (FavoriteLocation) -> Unit,
    onRename: (FavoriteLocation, String) -> Unit,
    onDelete: (FavoriteLocation) -> Unit,
    cooldownStates: Map<String, CooldownState> = emptyMap(),
    currentPosition: LatLng? = null,
    onAddFromHere: ((name: String) -> Unit)? = null,
    hideTeleport: Boolean = false,
    onShareOpened: () -> Unit = onDismiss,
    sortMode: SavedItemSortMode = SavedItemSortMode.NEWEST_FIRST,
    onSortModeSelected: (SavedItemSortMode) -> Unit = {},
) {
    var showAddForm by remember { mutableStateOf(false) }
    var newFavName by remember { mutableStateOf("") }
    var selectedFavorite by remember { mutableStateOf<FavoriteLocation?>(null) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var renamingFavorite by remember { mutableStateOf<FavoriteLocation?>(null) }
    var deletingFavorite by remember { mutableStateOf<FavoriteLocation?>(null) }
    var renameText by remember { mutableStateOf("") }
    val context = LocalContext.current

    FloatingPickerShell(
        title = selectedFavorite?.name ?: stringResource(R.string.widget_panel_favorites_title),
        onDismiss = onDismiss,
        hasBack = selectedFavorite != null,
        onBack = { selectedFavorite = null },
        searchEnabled = selectedFavorite == null && favorites.isNotEmpty(),
        searchVisible = searchVisible,
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        onToggleSearch = {
            if (searchVisible) {
                searchVisible = false
                searchQuery = ""
            } else {
                searchVisible = true
            }
        },
        onShareCurrentLocation = {
            if (currentLocationShareText(currentPosition) == null) {
                shareCurrentLocationCoordinates(context, currentPosition)
            } else {
                // Close the overlay first: it sits above activities, so the share sheet is hidden
                // until this panel is gone.
                onShareOpened()
                shareCurrentLocationCoordinates(context, currentPosition)
            }
        },
        showShareCurrentLocation = selectedFavorite == null,
        sortMode = if (selectedFavorite == null) sortMode else null,
        onSortModeSelected = onSortModeSelected,
        searchLabel = stringResource(R.string.widget_search_favorites),
    ) {
        val selected = selectedFavorite
        if (selected != null) {
            FavoriteTargetDetail(
                favorite = selected,
                onSetLocation = {
                    onTeleport(selected)
                    selectedFavorite = null
                    onDismiss()
                },
                onGoToLocation = {
                    onWalk(selected)
                    selectedFavorite = null
                    onDismiss()
                },
                onGoToLocationViaRoads = {
                    onWalkViaRoads(selected)
                    selectedFavorite = null
                    onDismiss()
                },
                onDismiss = { selectedFavorite = null },
                hideTeleportFeatures = hideTeleport,
                showDismissButton = false,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = {
                        renameText = selected.name
                        renamingFavorite = selected
                    },
                ) {
                    Icon(LjIcons.Edit, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.widget_panel_content_rename))
                }
                TextButton(onClick = { deletingFavorite = selected }) {
                    Icon(LjIcons.Delete, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.widget_panel_content_delete))
                }
            }
        } else {
            FavoritesList(
                title = null,
                favorites = favorites,
                onSelect = { selectedFavorite = it },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(0.dp),
                enableSearch = false,
                filterQuery = searchQuery,
                cooldownBadgeText = { fav ->
                    (cooldownStates[fav.id] ?: CooldownState.Ready).toBadgeText(currentPosition, fav.position)
                },
            )
            if (onAddFromHere != null) {
                Spacer(Modifier.height(12.dp))
                if (showAddForm) {
                    val focusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    OutlinedTextField(
                        value = newFavName,
                        onValueChange = { newFavName = it },
                        label = { Text(stringResource(R.string.widget_panel_name)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions =
                            KeyboardActions(
                                onDone = {
                                    if (newFavName.isNotBlank()) {
                                        onAddFromHere(newFavName.trim())
                                        newFavName = ""
                                        showAddForm = false
                                    }
                                },
                            ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        LjTextButton(onClick = {
                            showAddForm = false
                            newFavName = ""
                        }) {
                            Text(stringResource(R.string.widget_panel_cancel))
                        }
                        Spacer(Modifier.width(8.dp))
                        LjButton(
                            onClick = {
                                if (newFavName.isNotBlank()) {
                                    onAddFromHere(newFavName.trim())
                                    newFavName = ""
                                    showAddForm = false
                                }
                            },
                        ) {
                            Text(stringResource(R.string.widget_panel_save))
                        }
                    }
                } else {
                    LjButton(
                        onClick = { showAddForm = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(LjIcons.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.widget_panel_add_from_current_location))
                    }
                }
            }
        }
    }
    renamingFavorite?.let { favorite ->
        AlertDialog(
            onDismissRequest = { renamingFavorite = null },
            title = { Text(stringResource(R.string.widget_panel_content_rename_favorite)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(stringResource(R.string.widget_panel_content_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameText.isNotBlank(),
                    onClick = {
                        val name = renameText.trim()
                        onRename(favorite, name)
                        selectedFavorite = favorite.copy(name = name)
                        renamingFavorite = null
                    },
                ) { Text(stringResource(R.string.widget_panel_content_save)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { renamingFavorite = null },
                ) { Text(stringResource(R.string.widget_panel_content_cancel)) }
            },
        )
    }
    deletingFavorite?.let { favorite ->
        AlertDialog(
            onDismissRequest = { deletingFavorite = null },
            title = { Text(stringResource(R.string.widget_panel_content_delete_favorite)) },
            text = { Text(favorite.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(favorite)
                        selectedFavorite = null
                        deletingFavorite = null
                    },
                ) { Text(stringResource(R.string.widget_panel_content_delete)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { deletingFavorite = null },
                ) { Text(stringResource(R.string.widget_panel_content_cancel)) }
            },
        )
    }
}
