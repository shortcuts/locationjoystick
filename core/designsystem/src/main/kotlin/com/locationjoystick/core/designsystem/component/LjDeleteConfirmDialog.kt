package com.locationjoystick.core.designsystem.component

import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.locationjoystick.core.designsystem.R

/**
 * What kind of item is being deleted, so [LjDeleteConfirmDialog] can show the right body text.
 * Each value owns its own full-sentence message string rather than a noun plugged into a shared
 * template, since some languages change more than the noun depending on the item type.
 */
enum class DeleteItemType(
    @param:StringRes val messageRes: Int,
) {
    FAVORITE(R.string.delete_confirm_dialog_message_favorite),
    ROUTE(R.string.delete_confirm_dialog_message_route),
}

/**
 * Shared "Delete X?" confirmation, used anywhere a named item (route, favorite, ...) can be
 * permanently deleted. [itemType] selects the body text.
 */
@Composable
fun LjDeleteConfirmDialog(
    name: String,
    itemType: DeleteItemType,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_confirm_dialog_title, name)) },
        text = { Text(stringResource(itemType.messageRes)) },
        confirmButton = {
            LjTextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete_confirm_dialog_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            LjTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.delete_confirm_dialog_cancel))
            }
        },
    )
}
