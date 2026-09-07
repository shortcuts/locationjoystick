package com.locationjoystick.core.designsystem.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Shared "Delete X?" confirmation, used anywhere a named item (route, favorite, ...) can be
 * permanently deleted. [itemType] fills the body text, e.g. "route" -> "This route will be...".
 */
@Composable
fun LjDeleteConfirmDialog(
    name: String,
    itemType: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"$name\"?") },
        text = { Text("This $itemType will be permanently deleted and cannot be undone.") },
        confirmButton = {
            LjTextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            LjTextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
