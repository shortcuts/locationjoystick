package com.locationjoystick.feature.map.impl

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.captureBrowserChoices
import com.locationjoystick.core.common.util.isCaptureDefaultBrowser
import com.locationjoystick.core.common.util.launchCaptureDefaultBrowser
import com.locationjoystick.core.common.util.launchCaptureMapsLinks
import com.locationjoystick.core.common.util.launchCaptureRestoreDefaultApps
import com.locationjoystick.core.common.util.resolvePreferredBrowserPackage
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.designsystem.component.CaptureCoordinatesForm
import com.locationjoystick.core.designsystem.component.CaptureModeState
import com.locationjoystick.core.designsystem.component.CapturePointsState
import com.locationjoystick.core.designsystem.component.CaptureRouteSaveState
import com.locationjoystick.core.designsystem.component.CaptureSetupState
import com.locationjoystick.core.designsystem.component.LjCheckboxRow
import com.locationjoystick.core.designsystem.component.LjOverflowMenu
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.location.rememberSpoofToggleState
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.feature.map.impl.R

@Composable
fun CaptureCoordinatesRoute(
    onOpenDrawer: () -> Unit,
    viewModel: CaptureCoordinatesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val spoofToggle = rememberSpoofToggleState()
    val previousBrowserPackage by viewModel.previousBrowserPackage.collectAsStateWithLifecycle()
    var isDefaultBrowser by remember { mutableStateOf(context.isCaptureDefaultBrowser()) }
    var browserChoices by remember(context) { mutableStateOf(captureBrowserChoices(context)) }
    val preferredBrowserPackage = resolvePreferredBrowserPackage(previousBrowserPackage, context.packageName)
    val selectedBrowser =
        browserChoices.firstOrNull { it.packageName == preferredBrowserPackage } ?: browserChoices.firstOrNull()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    isDefaultBrowser = context.isCaptureDefaultBrowser()
                    browserChoices = captureBrowserChoices(context)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isDefaultBrowser, uiState.setupReset) { viewModel.onDefaultBrowserChecked(isDefaultBrowser) }

    CaptureCoordinatesScreen(
        uiState = uiState,
        captureSetup =
            CaptureSetupState(
                // A reset reopens the gate while this app still holds the role.
                isDefaultBrowser = isDefaultBrowser && !uiState.setupReset,
                passThroughBrowserName = selectedBrowser?.label ?: stringResource(R.string.capture_browser_automatic),
                browserChoices = browserChoices,
                selectedBrowserPackage = selectedBrowser?.packageName,
                onSelectBrowser = viewModel::rememberPreviousBrowser,
                onRequestDefaultBrowser = {
                    viewModel.clearSetupReset()
                    context.launchCaptureDefaultBrowser(viewModel::rememberPreviousBrowser)
                },
                onOpenMapsLinks = { context.launchCaptureMapsLinks() },
                onOpenSetupGuide = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppConstants.AppInfo.CAPTURE_GUIDE_URL)))
                },
            ),
        isSpoofing = spoofToggle.isSpoofing,
        onToggleSpoofing = spoofToggle.onToggle,
        locationLabel = spoofToggle.locationLabel,
        onOpenDrawer = onOpenDrawer,
        onConfirmRestore = {
            viewModel.restoreDefaultBrowser()
            context.launchCaptureRestoreDefaultApps()
        },
        onCaptureModeEnabledChange = viewModel::setCaptureModeEnabled,
        onCaptureEnabledChange = viewModel::setCaptureEnabled,
        onJumpEnabledChange = viewModel::setJumpEnabled,
        onRouteNameChange = viewModel::onRouteNameChange,
        onPointOrderChange = viewModel::onPointOrderChange,
        onSaveRoute = viewModel::saveRoute,
        onClearPoints = viewModel::clearPoints,
        onRemoveLast = viewModel::removeLast,
    )
}

@Composable
internal fun CaptureCoordinatesScreen(
    uiState: CaptureCoordinatesUiState,
    captureSetup: CaptureSetupState,
    isSpoofing: Boolean,
    onToggleSpoofing: () -> Unit,
    locationLabel: String?,
    onOpenDrawer: () -> Unit,
    onConfirmRestore: () -> Unit,
    onCaptureModeEnabledChange: (Boolean) -> Unit,
    onCaptureEnabledChange: (Boolean) -> Unit,
    onJumpEnabledChange: (Boolean) -> Unit,
    onRouteNameChange: (String) -> Unit,
    onPointOrderChange: (CapturePointOrder) -> Unit,
    onSaveRoute: () -> Unit,
    onClearPoints: () -> Unit,
    onRemoveLast: () -> Unit,
) {
    var showRestoreDialog by rememberSaveable { mutableStateOf(false) }
    if (showRestoreDialog) {
        RestoreDefaultBrowserDialog(
            onConfirm = {
                showRestoreDialog = false
                onConfirmRestore()
            },
            onDismiss = { showRestoreDialog = false },
        )
    }
    LjScaffold(
        title = stringResource(R.string.capture_coordinates_screen_capture),
        isSpoofing = isSpoofing,
        onToggleSpoofing = onToggleSpoofing,
        locationLabel = locationLabel,
        onNavigationClick = onOpenDrawer,
        actions = {
            LjOverflowMenu { dismiss ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.capture_menu_setup_guide)) },
                    onClick = {
                        dismiss()
                        captureSetup.onOpenSetupGuide()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.capture_menu_restore_default_browser)) },
                    onClick = {
                        dismiss()
                        showRestoreDialog = true
                    },
                )
            }
        },
    ) { paddingValues ->
        CaptureCoordinatesForm(
            captureMode =
                CaptureModeState(
                    captureModeEnabled = uiState.captureModeEnabled,
                    captureEnabled = uiState.captureEnabled,
                    jumpEnabled = uiState.jumpEnabled,
                    onCaptureModeEnabledChange = onCaptureModeEnabledChange,
                    onCaptureEnabledChange = onCaptureEnabledChange,
                    onJumpEnabledChange = onJumpEnabledChange,
                ),
            captureSetup = captureSetup,
            capturePoints =
                CapturePointsState(
                    points = uiState.points,
                    onClearPoints = onClearPoints,
                    onRemoveLast = onRemoveLast,
                    optimizeProximity = uiState.pointOrder == CapturePointOrder.PROXIMITY,
                    onOptimizeProximityChange = { optimize ->
                        onPointOrderChange(if (optimize) CapturePointOrder.PROXIMITY else CapturePointOrder.ORIGINAL)
                    },
                    orderedPoints = uiState.orderedPoints,
                ),
            routeSave =
                CaptureRouteSaveState(
                    routeName = uiState.routeName,
                    saved = uiState.saved,
                    saveError = uiState.saveError?.let { stringResource(it) },
                    canSave = uiState.canSave,
                    onRouteNameChange = onRouteNameChange,
                    onSaveRoute = onSaveRoute,
                ),
            showTitle = false,
            showClose = false,
            modifier = Modifier.padding(paddingValues),
        )
    }
}

@Composable
private fun RestoreDefaultBrowserDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var understood by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.capture_restore_dialog_title)) },
        text = {
            LjCheckboxRow(
                checked = understood,
                onCheckedChange = { understood = it },
                title = stringResource(R.string.capture_restore_dialog_checkbox),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = understood) {
                Text(stringResource(R.string.capture_restore_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.capture_restore_dialog_cancel)) }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun CaptureCoordinatesScreenPreview() {
    LjTheme {
        CaptureCoordinatesScreen(
            uiState =
                CaptureCoordinatesUiState(
                    captureModeEnabled = true,
                    captureEnabled = true,
                    points = listOf(LatLng(36.977695, 128.363905), LatLng(36.982194, 128.370129)),
                    routeName = "Mushrooms",
                ),
            captureSetup =
                CaptureSetupState(
                    isDefaultBrowser = true,
                    passThroughBrowserName = "Chrome",
                    browserChoices = emptyList(),
                    selectedBrowserPackage = null,
                    onSelectBrowser = {},
                    onRequestDefaultBrowser = {},
                    onOpenMapsLinks = {},
                    onOpenSetupGuide = {},
                ),
            isSpoofing = false,
            onToggleSpoofing = {},
            locationLabel = null,
            onOpenDrawer = {},
            onConfirmRestore = {},
            onCaptureModeEnabledChange = {},
            onCaptureEnabledChange = {},
            onJumpEnabledChange = {},
            onRouteNameChange = {},
            onPointOrderChange = {},
            onSaveRoute = {},
            onClearPoints = {},
            onRemoveLast = {},
        )
    }
}
