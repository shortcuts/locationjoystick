package com.locationjoystick.app.privacy

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locationjoystick.app.R
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjSpacing
import com.locationjoystick.core.designsystem.component.LjButton
import com.locationjoystick.core.designsystem.component.LjOutlinedButton
import com.locationjoystick.core.designsystem.component.LjScaffold

@Composable
fun AppUpdateScreen(
    onBack: () -> Unit,
    viewModel: AppUpdateViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val op by viewModel.op.collectAsStateWithLifecycle()
    val confirm = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    LaunchedEffect(viewModel) { viewModel.confirmIntents.collect { confirm.launch(it) } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshInstallPermission() }

    LjScaffold(
        title = stringResource(R.string.app_update_title),
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
                    .padding(LjSpacing.md),
            verticalArrangement = Arrangement.spacedBy(LjSpacing.md),
        ) {
            Text(
                stringResource(R.string.app_update_current, AppConstants.AppInfo.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
            )
            LjButton(onClick = viewModel::update, enabled = !op.isBusy) { Text(stringResource(R.string.app_update_button)) }
            val note =
                when (val current = op) {
                    UpdateOp.Idle -> null
                    UpdateOp.NeedsInstallPermission -> stringResource(R.string.app_update_needs_permission)
                    UpdateOp.Preparing -> stringResource(R.string.app_update_preparing)
                    UpdateOp.UpToDate -> stringResource(R.string.app_update_up_to_date)
                    UpdateOp.Installing -> stringResource(R.string.privacy_installing)
                    is UpdateOp.Failed -> stringResource(R.string.app_update_failed, current.detail)
                }
            note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (op == UpdateOp.NeedsInstallPermission) {
                LjOutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()))
                }) { Text(stringResource(R.string.privacy_open_settings)) }
            }
        }
    }
}
