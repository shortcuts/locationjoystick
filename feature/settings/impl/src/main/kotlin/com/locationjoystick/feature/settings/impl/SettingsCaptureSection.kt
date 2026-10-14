package com.locationjoystick.feature.settings.impl

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.locationjoystick.core.designsystem.component.CapturePassThroughRow
import com.locationjoystick.core.designsystem.component.CaptureRestoreDialog
import com.locationjoystick.core.designsystem.component.CaptureSetupState
import com.locationjoystick.core.designsystem.component.CaptureSetupSteps
import com.locationjoystick.core.designsystem.component.CaptureToggleStep
import com.locationjoystick.core.designsystem.component.LjOutlinedButton
import com.locationjoystick.feature.settings.impl.R
import com.locationjoystick.core.designsystem.R as DesignR

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
    var launchPickerExpanded by remember { mutableStateOf(false) }
    val launchApp = launchableApps.find { it.packageName == uiState.launchAfterLinkPackage }
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
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppConstants.AppInfo.CAPTURE_GUIDE_URL)))
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

    Column(modifier = modifier) {
        SettingsSectionHeader(stringResource(R.string.settings_menus_capture))
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.settings_menus_capture_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        if (setupDone) {
            CaptureToggleStep(
                captureModeEnabled = uiState.captureModeEnabled,
                captureEnabled = uiState.captureEnabled,
                jumpEnabled = uiState.jumpEnabled,
                onCaptureModeEnabledChange = { onAction(SettingsAction.SetCaptureModeEnabled(it)) },
                onCaptureEnabledChange = { onAction(SettingsAction.SetCaptureEnabled(it)) },
                onJumpEnabledChange = { onAction(SettingsAction.SetJumpEnabled(it)) },
            )
            CapturePassThroughRow(state = setupState)
            LjOutlinedButton(onClick = { showRestoreDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(DesignR.string.capture_restore_default_browser), modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        } else {
            CaptureSetupSteps(state = setupState)
        }
        Box {
            LjOutlinedButton(onClick = { launchPickerExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(
                        R.string.settings_menus_capture_launch_after,
                        launchApp?.label ?: stringResource(R.string.settings_menus_capture_launch_after_none),
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
            DropdownMenu(expanded = launchPickerExpanded, onDismissRequest = { launchPickerExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_menus_capture_launch_after_none)) },
                    onClick = {
                        onAction(SettingsAction.SetLaunchAfterLinkPackage(null))
                        launchPickerExpanded = false
                    },
                )
                launchableApps.forEach { app ->
                    DropdownMenuItem(
                        text = { Text(app.label) },
                        onClick = {
                            onAction(SettingsAction.SetLaunchAfterLinkPackage(app.packageName))
                            launchPickerExpanded = false
                        },
                    )
                }
            }
        }
    }
}
