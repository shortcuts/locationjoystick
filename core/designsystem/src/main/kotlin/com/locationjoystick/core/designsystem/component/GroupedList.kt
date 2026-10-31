package com.locationjoystick.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.R
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState

/** Collapsed section keys (internal to [groupedItems]). `remember`d so leaving the screen resets it. */
@Composable
fun rememberCollapsedGroups(): SnapshotStateList<String> = remember { mutableStateListOf() }

/**
 * Drag-to-reorder state for a [groupedItems] list. Group headers drag among user groups (all
 * sections fold while dragging); rows drag within their own section. [groups] is what to render:
 * the input with in-flight drag changes applied.
 */
class GroupReorder<T> internal constructor(
    val groups: List<ListGroup<T>>,
    internal val state: ReorderableLazyListState,
    internal val dragging: MutableState<Boolean>,
    internal val commit: () -> Unit,
    internal val move: (key: Any, delta: Int) -> Boolean,
) {
    val isDraggingGroup: Boolean get() = dragging.value
}

/**
 * @param groups Sections from [groupForDisplay] (all items; do not pass a search-filtered list).
 * @param onCommit Called on drop with every item key in new display order.
 */
@Composable
fun <T> rememberGroupReorder(
    listState: LazyListState,
    groups: List<ListGroup<T>>,
    itemKey: (T) -> String,
    onCommit: (List<String>) -> Unit,
): GroupReorder<T> {
    var reordered by remember { mutableStateOf<List<ListGroup<T>>?>(null) }
    val dragging = remember { mutableStateOf(false) }
    // The persisted order flowing back replaces the optimistic drag state.
    LaunchedEffect(groups) { reordered = null }
    val effective = reordered ?: groups
    val current = rememberUpdatedState(effective)
    val reorderState =
        rememberReorderableLazyListState(listState) { from, to ->
            moveKey(current.value, itemKey, from.key, to.key)?.let { reordered = it }
        }
    return GroupReorder(
        groups = effective,
        state = reorderState,
        dragging = dragging,
        commit = { onCommit(current.value.flatMap { g -> g.items.map(itemKey) }) },
        move = { key, delta -> movedKeys(current.value, itemKey, key, delta)?.also(onCommit) != null },
    )
}

@Composable
private fun <T> Modifier.moveActions(
    reorder: GroupReorder<T>,
    key: Any,
): Modifier {
    val up = stringResource(R.string.grouped_list_move_up)
    val down = stringResource(R.string.grouped_list_move_down)
    return semantics {
        customActions =
            listOf(
                CustomAccessibilityAction(up) { reorder.move(key, -1) },
                CustomAccessibilityAction(down) { reorder.move(key, 1) },
            )
    }
}

// "u:"/"h:" keeps a user group and a same-named hot country distinct.
private fun <T> sectionKey(group: ListGroup<T>): String = (if (group.isUserGroup) "u:" else "h:") + group.name.orEmpty()

/**
 * The single grouped-list renderer for favorites and routes, on every surface. Emits a
 * collapsible header per section, then its rows.
 *
 * @param forceExpanded True while a search query is active, so matches are never hidden.
 * @param reorder When non-null, rows and user-group headers get a drag handle.
 * @param onRenameGroup When non-null, user groups show Rename in the header menu.
 * @param onDeleteGroup When non-null, user groups show Delete in the header menu.
 */
fun <T> LazyListScope.groupedItems(
    groups: List<ListGroup<T>>,
    collapsed: SnapshotStateList<String>,
    forceExpanded: Boolean,
    itemKey: (T) -> String,
    textColor: Color = Color.Unspecified,
    reorder: GroupReorder<T>? = null,
    onRenameGroup: ((String) -> Unit)? = null,
    onDeleteGroup: ((String) -> Unit)? = null,
    itemContent: @Composable LazyItemScope.(T) -> Unit,
) {
    val showHeaders = groups.size > 1 || groups.firstOrNull()?.name != null
    groups.forEach { group ->
        val key = sectionKey(group)
        val headerKey = group.headerKey()
        val expanded = (forceExpanded || key !in collapsed) && reorder?.isDraggingGroup != true
        if (showHeaders) {
            item(key = headerKey) {
                val header: @Composable (Modifier?) -> Unit = { handle ->
                    GroupHeader(
                        title = group.name ?: stringResource(R.string.grouped_list_ungrouped),
                        count = group.items.size,
                        expanded = expanded,
                        textColor = textColor,
                        onToggle = { if (key in collapsed) collapsed.remove(key) else collapsed.add(key) },
                        onRename = if (group.isUserGroup && onRenameGroup != null) ({ onRenameGroup(group.name.orEmpty()) }) else null,
                        onDelete = if (group.isUserGroup && onDeleteGroup != null) ({ onDeleteGroup(group.name.orEmpty()) }) else null,
                        dragHandle = handle,
                    )
                }
                if (reorder != null && group.isUserGroup) {
                    ReorderableItem(reorder.state, key = headerKey) {
                        header(
                            Modifier
                                .draggableHandle(
                                    onDragStarted = { reorder.dragging.value = true },
                                    onDragStopped = {
                                        reorder.commit()
                                        reorder.dragging.value = false
                                    },
                                ).moveActions(reorder, headerKey),
                        )
                    }
                } else {
                    header(null)
                }
            }
        }
        if (expanded) {
            items(items = group.items, key = itemKey) { row ->
                if (reorder == null) {
                    itemContent(row)
                } else {
                    val scope = this
                    ReorderableItem(reorder.state, key = itemKey(row)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                LjIcons.DragHandle,
                                contentDescription = stringResource(R.string.grouped_list_drag_cd),
                                modifier =
                                    Modifier
                                        .draggableHandle(onDragStopped = reorder.commit)
                                        .moveActions(reorder, itemKey(row))
                                        .minimumInteractiveComponentSize(),
                            )
                            Box(Modifier.weight(1f)) { scope.itemContent(row) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    textColor: Color,
    onToggle: () -> Unit,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
    dragHandle: Modifier? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dragHandle != null) {
            Icon(
                LjIcons.DragHandle,
                contentDescription = stringResource(R.string.grouped_list_drag_group_cd, title),
                modifier = dragHandle.minimumInteractiveComponentSize(),
            )
        }
        Icon(
            if (expanded) LjIcons.ExpandLess else LjIcons.ExpandMore,
            contentDescription =
                stringResource(
                    if (expanded) R.string.grouped_list_collapse_cd else R.string.grouped_list_expand_cd,
                    title,
                ),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "$title ($count)",
            style = MaterialTheme.typography.labelLarge,
            color = if (textColor == Color.Unspecified) MaterialTheme.colorScheme.primary else textColor,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        if (onRename != null || onDelete != null) {
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                    Icon(LjIcons.MoreVert, contentDescription = stringResource(R.string.grouped_list_group_options_cd, title))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    if (onRename != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.grouped_list_rename_group)) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            },
                            leadingIcon = { Icon(LjIcons.Edit, null) },
                        )
                    }
                    if (onDelete != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.grouped_list_delete_group), color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                            leadingIcon = { Icon(LjIcons.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        )
                    }
                }
            }
        }
    }
}

/** Overflow-menu entry that opens the move-to-group dialog. */
@Composable
fun MoveToGroupMenuItem(onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.grouped_list_move_to_group)) },
        onClick = onClick,
        leadingIcon = { Icon(LjIcons.Folder, null) },
    )
}

/** Which dialog is open for a grouped list screen. */
class GroupDialogState<T> {
    var moving by mutableStateOf<T?>(null)
    var renaming by mutableStateOf<String?>(null)
    var deleting by mutableStateOf<String?>(null)
}

/**
 * Move / rename / delete-group dialogs, shared by the Favorites and Routes screens.
 *
 * @param groupNames User group names that exist now.
 * @param currentGroupOf Group of the item being moved, pre-selected in the picker.
 */
@Composable
fun <T> GroupDialogs(
    state: GroupDialogState<T>,
    groupNames: List<String>,
    currentGroupOf: (T) -> String?,
    onMove: (T, String?) -> Unit,
    onRename: (from: String, to: String) -> Unit,
    onDelete: (String) -> Unit,
) {
    state.moving?.let { item ->
        MoveToGroupDialog(
            groupNames = groupNames,
            current = currentGroupOf(item),
            onSelect = {
                onMove(item, it)
                state.moving = null
            },
            onDismiss = { state.moving = null },
        )
    }
    state.renaming?.let { from ->
        GroupNameDialog(
            initial = from,
            onConfirm = {
                if (it != from) onRename(from, it)
                state.renaming = null
            },
            onDismiss = { state.renaming = null },
        )
    }
    state.deleting?.let { name ->
        LjDeleteConfirmDialog(
            name = name,
            itemType = DeleteItemType.GROUP,
            onDismiss = { state.deleting = null },
            onConfirm = {
                onDelete(name)
                state.deleting = null
            },
        )
    }
}

@Composable
private fun MoveToGroupDialog(
    groupNames: List<String>,
    current: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    LjAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.grouped_list_move_to_group)) },
        text = {
            Column {
                (listOf<String?>(null) + groupNames).forEach { name ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(name) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            LjIcons.Folder,
                            contentDescription = null,
                            tint = if (name == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            name ?: stringResource(R.string.grouped_list_ungrouped),
                            modifier = Modifier.padding(start = 12.dp),
                            color = if (name == current) MaterialTheme.colorScheme.primary else Color.Unspecified,
                        )
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.grouped_list_new_group)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            LjTextButton(onClick = { onSelect(newName.trim()) }, enabled = newName.isNotBlank()) {
                Text(stringResource(R.string.grouped_list_create_and_move))
            }
        },
        dismissButton = {
            LjTextButton(onClick = onDismiss) { Text(stringResource(R.string.delete_confirm_dialog_cancel)) }
        },
    )
}

@Composable
private fun GroupNameDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    LjAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.grouped_list_rename_group)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.grouped_list_group_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            LjTextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.grouped_list_save))
            }
        },
        dismissButton = {
            LjTextButton(onClick = onDismiss) { Text(stringResource(R.string.delete_confirm_dialog_cancel)) }
        },
    )
}
