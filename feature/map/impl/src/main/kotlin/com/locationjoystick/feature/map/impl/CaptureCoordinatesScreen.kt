package com.locationjoystick.feature.map.impl

import androidx.compose.foundation.layout.padding
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
import com.locationjoystick.core.common.util.isCaptureDefaultBrowser
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.designsystem.component.CaptureCoordinatesForm
import com.locationjoystick.core.designsystem.component.CaptureModeState
import com.locationjoystick.core.designsystem.component.CapturePointsState
import com.locationjoystick.core.designsystem.component.CaptureRouteSaveState
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
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    isDefaultBrowser = context.isCaptureDefaultBrowser()
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
    )
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
) {
    LjScaffold(
        title = stringResource(R.string.capture_coordinates_screen_capture),
        isSpoofing = isSpoofing,
        onToggleSpoofing = onToggleSpoofing,
        locationLabel = locationLabel,
        onNavigationClick = onOpenDrawer,
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
                    isDefaultBrowser = isDefaultBrowser,
                ),
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
        )
    }
}
