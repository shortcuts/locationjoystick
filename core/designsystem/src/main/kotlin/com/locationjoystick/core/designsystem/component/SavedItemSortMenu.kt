package com.locationjoystick.core.designsystem.component

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.R
import com.locationjoystick.core.model.SavedItemSortMode

@Composable
fun SavedItemSortMenu(
    selected: SavedItemSortMode,
    onSelected: (SavedItemSortMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(LjIcons.SwapVert, contentDescription = stringResource(R.string.saved_item_sort_menu_sort))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        SavedItemSortMode.entries.forEach { mode ->
            DropdownMenuItem(
                text = { Text(mode.label) },
                leadingIcon = {
                    if (mode == selected) Icon(LjIcons.Check, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onSelected(mode)
                },
            )
        }
    }
}

private val SavedItemSortMode.label: String
    @Composable
    get() =
        when (this) {
            SavedItemSortMode.NAME_ASCENDING -> stringResource(R.string.saved_sort_name_ascending)
            SavedItemSortMode.NAME_DESCENDING -> stringResource(R.string.saved_sort_name_descending)
            SavedItemSortMode.NEWEST_FIRST -> stringResource(R.string.saved_sort_newest)
            SavedItemSortMode.OLDEST_FIRST -> stringResource(R.string.saved_sort_oldest)
        }
