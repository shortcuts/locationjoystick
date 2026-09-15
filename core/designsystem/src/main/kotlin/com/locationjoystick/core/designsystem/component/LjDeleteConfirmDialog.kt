package com.locationjoystick.core.designsystem.component

import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.locationjoystick.core.designsystem.R

/**
 * What kind of item is being deleted, so [LjDeleteConfirmDialog] can name it in the body text.
 */
enum class DeleteItemType(
    @StringRes val labelRes: Int,
) {
    FAVORITE(R.string.delete_confirm_dialog_item_favorite),
    ROUTE(R.string.delete_confirm_dialog_item_route),
}

/**
 * Shared "Delete X?" confirmation, used anywhere a named item (route, favorite, ...) can be
 * permanently deleted. [itemType] fills the body text, e.g. FAVORITE -> "This favorite will be...".
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
        text = { Text(stringResource(R.string.delete_confirm_dialog_message, stringResource(itemType.labelRes))) },
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
