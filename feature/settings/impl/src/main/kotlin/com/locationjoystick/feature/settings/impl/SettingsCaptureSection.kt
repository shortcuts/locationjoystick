package com.locationjoystick.feature.settings.impl

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.captureBrowserChoices
import com.locationjoystick.core.common.util.isCaptureDefaultBrowser
import com.locationjoystick.core.common.util.launchCaptureDefaultBrowser
import com.locationjoystick.core.common.util.launchCaptureMapsLinks
import com.locationjoystick.core.common.util.launchCaptureRestoreDefaultApps
import com.locationjoystick.core.common.util.resolvePreferredBrowserPackage
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjSpacing
import com.locationjoystick.core.designsystem.component.CapturePassThroughRow
import com.locationjoystick.core.designsystem.component.CaptureRestoreDialog
import com.locationjoystick.core.designsystem.component.CaptureSetupState
import com.locationjoystick.core.designsystem.component.CaptureSetupSteps
import com.locationjoystick.core.designsystem.component.CaptureToggleStep
import com.locationjoystick.core.designsystem.component.LjCard
import com.locationjoystick.core.designsystem.component.LjModalBottomSheet
import com.locationjoystick.core.designsystem.component.LjOutlinedButton
import com.locationjoystick.core.model.effectiveLaunchPackage
import com.locationjoystick.feature.settings.impl.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.locationjoystick.core.designsystem.R as DesignR

private const val LAUNCH_ICON_PX = 96

/**
 * Home of the Capture setup (default-browser role, supported links) plus the mode toggle, List/Jump
 * checkboxes, and pass-through browser picker — all sourced from `CaptureCoordinatesRepository`. The
 * Capture screen sends users here until setup is done; see docs/features/location-links.md (Opt-in Tier).
 */
@Composable
internal fun CaptureSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    launchableApps: List<InstalledApp>,
    modifier: Modifier = Modifier,
) {
    var showLaunchSheet by rememberSaveable { mutableStateOf(false) }
    val launchApp =
        effectiveLaunchPackage(uiState.launchAfterLinkPackage, uiState.launchAfterLinkChosen, uiState.guidedPath) { pkg ->
            launchableApps.any { it.packageName == pkg }
        }?.let { pkg -> launchableApps.find { it.packageName == pkg } }
    val context = LocalContext.current
    val browserChoices = remember(context) { captureBrowserChoices(context) }
    val preferredBrowserPackage = resolvePreferredBrowserPackage(uiState.capturePreviousBrowserPackage, context.packageName)
    val selectedBrowser = browserChoices.firstOrNull { it.packageName == preferredBrowserPackage } ?: browserChoices.firstOrNull()
    var isDefaultBrowser by remember { mutableStateOf(context.isCaptureDefaultBrowser()) }
    var showRestoreDialog by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) isDefaultBrowser = context.isCaptureDefaultBrowser()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(isDefaultBrowser, uiState.captureSetupReset) {
        onAction(SettingsAction.CaptureDefaultBrowserChecked(isDefaultBrowser))
    }
    // A reset reopens the gate while this app still holds the role.
    val setupDone = isDefaultBrowser && !uiState.captureSetupReset
    val setupState =
        CaptureSetupState(
            isDefaultBrowser = setupDone,
            passThroughBrowserName = selectedBrowser?.label ?: stringResource(R.string.settings_menus_capture_browser_automatic),
            browserChoices = browserChoices,
            selectedBrowserPackage = selectedBrowser?.packageName,
            onSelectBrowser = { pkg -> onAction(SettingsAction.SetCapturePreviousBrowserPackage(pkg)) },
            onRequestDefaultBrowser = {
                onAction(SettingsAction.ClearCaptureSetupReset)
                context.launchCaptureDefaultBrowser { pkg -> onAction(SettingsAction.SetCapturePreviousBrowserPackage(pkg)) }
            },
            onOpenMapsLinks = { context.launchCaptureMapsLinks() },
            onOpenSetupGuide = {
                context.startActivity(Intent(Intent.ACTION_VIEW, AppConstants.AppInfo.CAPTURE_GUIDE_URL.toUri()))
            },
        )

    if (showRestoreDialog) {
        CaptureRestoreDialog(
            onConfirm = {
                showRestoreDialog = false
                onAction(SettingsAction.RestoreCaptureDefaultBrowser)
                context.launchCaptureRestoreDefaultApps()
            },
            onDismiss = { showRestoreDialog = false },
        )
    }

    if (showLaunchSheet) {
        LaunchAppSheet(
            apps = launchableApps,
            selectedPackage = launchApp?.packageName,
            onSelect = { pkg ->
                showLaunchSheet = false
                onAction(SettingsAction.SetLaunchAfterLinkPackage(pkg))
            },
            onDismiss = { showLaunchSheet = false },
        )
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(LjSpacing.md)) {
        Text(
            stringResource(R.string.settings_menus_capture_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (setupDone) {
            CaptureToggleStep(
                captureModeEnabled = uiState.captureModeEnabled,
                captureEnabled = uiState.captureEnabled,
                jumpEnabled = uiState.jumpEnabled,
                onCaptureModeEnabledChange = { onAction(SettingsAction.SetCaptureModeEnabled(it)) },
                onCaptureEnabledChange = { onAction(SettingsAction.SetCaptureEnabled(it)) },
                onJumpEnabledChange = { onAction(SettingsAction.SetJumpEnabled(it)) },
            )
            LjCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(LjSpacing.md), verticalArrangement = Arrangement.spacedBy(LjSpacing.sm)) {
                    CapturePassThroughRow(state = setupState)
                    LjOutlinedButton(onClick = { showRestoreDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(DesignR.string.capture_restore_default_browser), modifier = Modifier.weight(1f))
                    }
                }
            }
        } else {
            CaptureSetupSteps(state = setupState)
        }
        LjCard(onClick = { showLaunchSheet = true }, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(LjSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LjSpacing.md),
            ) {
                AppIcon(launchApp?.packageName)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_menus_capture_launch_after_title),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        launchApp?.label ?: stringResource(R.string.settings_menus_capture_launch_after_none),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Icon(LjIcons.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AppIcon(packageName: String?) {
    val context = LocalContext.current
    val iconState =
        produceState<ImageBitmap?>(null, packageName) {
            value =
                packageName?.let {
                    withContext(Dispatchers.Default) {
                        runCatching {
                            context.packageManager
                                .getApplicationIcon(it)
                                .toBitmap(LAUNCH_ICON_PX, LAUNCH_ICON_PX)
                                .asImageBitmap()
                        }.getOrNull()
                    }
                }
        }
    val icon = iconState.value
    if (icon != null) {
        Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(40.dp))
    } else {
        Icon(
            LjIcons.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(40.dp).padding(LjSpacing.sm),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LaunchAppSheet(
    apps: List<InstalledApp>,
    selectedPackage: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    LjModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.settings_menus_capture_launch_after_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = LjSpacing.md, vertical = LjSpacing.sm),
        )
        LazyColumn {
            item {
                LaunchAppRow(null, stringResource(R.string.settings_menus_capture_launch_after_none), selectedPackage == null) {
                    onSelect(null)
                }
            }
            items(apps, key = { it.packageName }) { app ->
                LaunchAppRow(app, app.label, app.packageName == selectedPackage) { onSelect(app.packageName) }
            }
        }
    }
}

@Composable
private fun LaunchAppRow(
    app: InstalledApp?,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = LjSpacing.md, vertical = LjSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LjSpacing.md),
    ) {
        AppIcon(app?.packageName)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        RadioButton(selected = selected, onClick = null)
    }
}
