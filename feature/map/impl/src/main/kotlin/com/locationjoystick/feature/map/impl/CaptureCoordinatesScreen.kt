package com.locationjoystick.feature.map.impl

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.locationjoystick.core.common.util.captureBrowserChoices
import com.locationjoystick.core.common.util.isCaptureDefaultBrowser
import com.locationjoystick.core.common.util.launchCaptureDefaultBrowser
import com.locationjoystick.core.common.util.launchCaptureMapsLinks
import com.locationjoystick.core.common.util.launchCaptureRestoreDefaultApps
import com.locationjoystick.core.common.util.launchCaptureThisAppLinks
import com.locationjoystick.core.common.util.resolvePreferredBrowserPackage
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.designsystem.component.CaptureCoordinatesForm
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
    var isDefaultBrowser by remember { mutableStateOf(context.isCaptureDefaultBrowser()) }
    var showBrowserPicker by remember { mutableStateOf(false) }
    var browserChoices by remember(context) { mutableStateOf(captureBrowserChoices(context)) }
    val preferredBrowserPackage =
        resolvePreferredBrowserPackage(uiState.previousBrowserPackage, context.packageName)
    val selectedBrowser = browserChoices.firstOrNull { it.packageName == preferredBrowserPackage } ?: browserChoices.firstOrNull()
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

    CaptureCoordinatesScreen(
        uiState = uiState,
        isDefaultBrowser = isDefaultBrowser,
        isSpoofing = spoofToggle.isSpoofing,
        onToggleSpoofing = spoofToggle.onToggle,
        locationLabel = spoofToggle.locationLabel,
        onOpenDrawer = onOpenDrawer,
        onCaptureModeEnabledChange = viewModel::setCaptureModeEnabled,
        onCaptureEnabledChange = viewModel::setCaptureEnabled,
        onJumpEnabledChange = viewModel::setJumpEnabled,
        onRouteNameChange = viewModel::onRouteNameChange,
        onPointOrderChange = viewModel::onPointOrderChange,
        onSaveRoute = viewModel::saveRoute,
        onClearPoints = viewModel::clearPoints,
        onRemoveLast = viewModel::removeLast,
        onRequestDefaultBrowser = { context.launchCaptureDefaultBrowser(viewModel::rememberPreviousBrowser) },
        onOpenThisAppLinks = { context.launchCaptureThisAppLinks() },
        onOpenMapsLinks = { context.launchCaptureMapsLinks() },
        onRestoreDefaultApps = { context.launchCaptureRestoreDefaultApps() },
        passThroughBrowserName = selectedBrowser?.label ?: stringResource(R.string.capture_browser_automatic),
        onChoosePassThroughBrowser = { showBrowserPicker = browserChoices.isNotEmpty() },
    )
    if (showBrowserPicker) {
        AlertDialog(
            onDismissRequest = { showBrowserPicker = false },
            title = { Text(stringResource(R.string.capture_coordinates_screen_pass_through_browser)) },
            text = {
                Column {
                    browserChoices.forEach { choice ->
                        androidx.compose.foundation.layout.Row(
                            modifier =
                                Modifier.clickable {
                                    viewModel.rememberPreviousBrowser(choice.packageName)
                                    showBrowserPicker = false
                                },
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = choice.packageName == selectedBrowser?.packageName,
                                onClick = {
                                    viewModel.rememberPreviousBrowser(choice.packageName)
                                    showBrowserPicker = false
                                },
                            )
                            Text(choice.label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBrowserPicker = false }) { Text(stringResource(R.string.capture_coordinates_screen_cancel)) }
            },
        )
    }
}

@Composable
internal fun CaptureCoordinatesScreen(
    uiState: CaptureCoordinatesUiState,
    isDefaultBrowser: Boolean,
    isSpoofing: Boolean,
    onToggleSpoofing: () -> Unit,
    locationLabel: String?,
    onOpenDrawer: () -> Unit,
    onCaptureModeEnabledChange: (Boolean) -> Unit,
    onCaptureEnabledChange: (Boolean) -> Unit,
    onJumpEnabledChange: (Boolean) -> Unit,
    onRouteNameChange: (String) -> Unit,
    onPointOrderChange: (CapturePointOrder) -> Unit,
    onSaveRoute: () -> Unit,
    onClearPoints: () -> Unit,
    onRemoveLast: () -> Unit,
    onRequestDefaultBrowser: () -> Unit,
    onOpenThisAppLinks: () -> Unit,
    onOpenMapsLinks: () -> Unit,
    onRestoreDefaultApps: () -> Unit,
    passThroughBrowserName: String,
    onChoosePassThroughBrowser: () -> Unit,
) {
    LjScaffold(
        title = stringResource(R.string.capture_coordinates_screen_capture),
        isSpoofing = isSpoofing,
        onToggleSpoofing = onToggleSpoofing,
        locationLabel = locationLabel,
        onNavigationClick = onOpenDrawer,
    ) { paddingValues ->
        CaptureCoordinatesForm(
            captureModeEnabled = uiState.captureModeEnabled,
            captureEnabled = uiState.captureEnabled,
            jumpEnabled = uiState.jumpEnabled,
            points = uiState.points,
            routeName = uiState.routeName,
            saved = uiState.saved,
            saveError = uiState.saveError?.let { stringResource(it) },
            canSave = uiState.canSave,
            passThroughBrowserName = passThroughBrowserName,
            isDefaultBrowser = isDefaultBrowser,
            showTitle = false,
            showClose = false,
            optimizeProximity = uiState.pointOrder == CapturePointOrder.PROXIMITY,
            onOptimizeProximityChange = { optimize ->
                onPointOrderChange(if (optimize) CapturePointOrder.PROXIMITY else CapturePointOrder.ORIGINAL)
            },
            orderedPoints = uiState.orderedPoints,
            onCaptureModeEnabledChange = onCaptureModeEnabledChange,
            onCaptureEnabledChange = onCaptureEnabledChange,
            onJumpEnabledChange = onJumpEnabledChange,
            onRouteNameChange = onRouteNameChange,
            onSaveRoute = onSaveRoute,
            onClearPoints = onClearPoints,
            onRemoveLast = onRemoveLast,
            onRequestDefaultBrowser = onRequestDefaultBrowser,
            onOpenThisAppLinks = onOpenThisAppLinks,
            onOpenMapsLinks = onOpenMapsLinks,
            onRestoreDefaultApps = onRestoreDefaultApps,
            onChoosePassThroughBrowser = onChoosePassThroughBrowser,
            modifier = Modifier.padding(paddingValues),
        )
    }
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
            isDefaultBrowser = true,
            isSpoofing = false,
            onToggleSpoofing = {},
            locationLabel = null,
            onOpenDrawer = {},
            onCaptureModeEnabledChange = {},
            onCaptureEnabledChange = {},
            onJumpEnabledChange = {},
            onRouteNameChange = {},
            onPointOrderChange = {},
            onSaveRoute = {},
            onClearPoints = {},
            onRemoveLast = {},
            onRequestDefaultBrowser = {},
            onOpenThisAppLinks = {},
            onOpenMapsLinks = {},
            onRestoreDefaultApps = {},
            passThroughBrowserName = "Chrome",
            onChoosePassThroughBrowser = {},
        )
    }
}
