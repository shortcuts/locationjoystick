package com.locationjoystick.app.privacy

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locationjoystick.app.R
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjSpacing
import com.locationjoystick.core.designsystem.component.LjAlertDialog
import com.locationjoystick.core.designsystem.component.LjButton
import com.locationjoystick.core.designsystem.component.LjOutlinedButton
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.designsystem.component.LjSelectionMode
import com.locationjoystick.core.designsystem.component.LjSelectionRow

@Composable
fun PrivacyModeScreen(
    onBack: () -> Unit,
    viewModel: PrivacyModeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var name by rememberSaveable(state.record?.name) { mutableStateOf(state.record?.name.orEmpty()) }

    val uninstall =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // FIRST_USER: the system reports it when the clone is already gone.
            if (it.resultCode == Activity.RESULT_OK || it.resultCode == Activity.RESULT_FIRST_USER) viewModel.onRemoved()
        }
    val confirm = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    LaunchedEffect(viewModel) { viewModel.confirmIntents.collect { confirm.launch(it) } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshInstallPermission() }

    LjScaffold(
        title = stringResource(R.string.privacy_title),
        isSpoofing = false,
        onToggleSpoofing = {},
        showSpoofToggle = false,
        onNavigationClick = onBack,
        navigationIcon = LjIcons.ArrowBack,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(LjSpacing.md),
            verticalArrangement = Arrangement.spacedBy(LjSpacing.md),
        ) {
            LjSelectionRow(
                mode = LjSelectionMode.Toggle,
                checked = state.flags.enabled,
                onCheckedChange = viewModel::setEnabled,
                title = stringResource(R.string.privacy_title),
                description = stringResource(R.string.privacy_switch_description),
            )
            if (state.flags.enabled) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.privacy_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val canStart = isValidCloneName(name) && !state.op.isBusy
                val hasClone = state.record != null
                Row(horizontalArrangement = Arrangement.spacedBy(LjSpacing.sm)) {
                    LjButton(onClick = { viewModel.start(name) }, enabled = canStart) {
                        Text(stringResource(if (hasClone) R.string.privacy_update else R.string.privacy_start))
                    }
                    state.record?.let { record ->
                        LjOutlinedButton(
                            onClick = {
                                uninstall.launch(
                                    Intent(Intent.ACTION_UNINSTALL_PACKAGE, "package:${record.packageId}".toUri())
                                        .putExtra(Intent.EXTRA_RETURN_RESULT, true),
                                )
                            },
                            enabled = !state.op.isBusy,
                        ) { Text(stringResource(R.string.privacy_remove)) }
                    }
                }
                OpNote(state.op) {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()),
                    )
                }
            }
        }
    }

    if (state.showDisclosure) {
        LjAlertDialog(
            onDismissRequest = viewModel::dismissDisclosure,
            title = { Text(stringResource(R.string.privacy_title)) },
            text = { Text(stringResource(R.string.privacy_disclosure)) },
            confirmButton = { TextButton(onClick = viewModel::confirmDisclosure) { Text(stringResource(R.string.privacy_understand)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissDisclosure) { Text(stringResource(R.string.privacy_cancel)) } },
        )
    }
}

@Composable
private fun OpNote(
    op: CloneOp,
    openInstallSettings: () -> Unit,
) {
    val note =
        when (op) {
            CloneOp.Idle -> null
            CloneOp.NeedsInstallPermission -> stringResource(R.string.privacy_needs_permission)
            CloneOp.Building -> stringResource(R.string.privacy_building)
            is CloneOp.Installing -> stringResource(R.string.privacy_installing)
            is CloneOp.Installed -> stringResource(R.string.privacy_installed)
            is CloneOp.Failed -> stringResource(R.string.privacy_failed, op.detail)
        } ?: return
    Text(note, style = MaterialTheme.typography.bodyMedium)
    if (op == CloneOp.NeedsInstallPermission) {
        LjOutlinedButton(onClick = openInstallSettings) { Text(stringResource(R.string.privacy_open_settings)) }
    }
}
