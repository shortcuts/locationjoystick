package com.locationjoystick.feature.settings.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import com.locationjoystick.core.designsystem.LjSpacing
import com.locationjoystick.core.designsystem.component.LjAlertDialog
import com.locationjoystick.core.designsystem.component.LjTextButton
import com.locationjoystick.feature.settings.impl.R

@Composable
internal fun ImportConfirmDialog(
    onReplace: () -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    LjAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_import_data)) },
        text = { Text(stringResource(R.string.settings_how_would_you_like_to_handle)) },
        confirmButton = {
            Row {
                LjTextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                LjTextButton(onClick = onAdd) { Text(stringResource(R.string.settings_add)) }
                LjTextButton(onClick = onReplace, destructive = true) { Text(stringResource(R.string.settings_replace)) }
            }
        },
        dismissButton = {},
    )
}

@Composable
internal fun ResetAllDataConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    LjAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_reset_all_data_2)) },
        text = { Text(stringResource(R.string.settings_all_favorites_routes_and_settings_will)) },
        confirmButton = {
            LjTextButton(onClick = onConfirm, destructive = true) {
                Text(stringResource(R.string.settings_reset))
            }
        },
        dismissButton = {
            LjTextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
internal fun EnterExportCodeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }

    LjAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_enter_export_code)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LjSpacing.sm)) {
                Text(
                    text = stringResource(R.string.settings_ask_the_sender_for_their_6),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(6) },
                    label = { Text(stringResource(R.string.settings_code)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (code.length == 6) onConfirm(code) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            LjTextButton(onClick = { onConfirm(code) }, enabled = code.length == 6) { Text(stringResource(R.string.settings_import)) }
        },
        dismissButton = {
            LjTextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
