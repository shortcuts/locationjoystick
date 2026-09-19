package com.locationjoystick.feature.settings.impl

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
import androidx.compose.ui.unit.dp
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
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.designsystem.component.CaptureSetupForm
import com.locationjoystick.core.designsystem.component.CaptureSetupState
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.feature.settings.impl.R

@Composable
internal fun SettingsCaptureSubScreen(
    uiState: SettingsUiState,
    onNavigateBack: () -> Unit,
    isSpoofing: Boolean,
    onToggleSpoofing: () -> Unit,
    locationLabel: String? = null,
    onAction: (SettingsAction) -> Unit,
    bottomBar: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
    viewModel: SettingsCaptureViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val captureModeEnabled by viewModel.captureModeEnabled.collectAsStateWithLifecycle()
    val previousBrowserPackage by viewModel.previousBrowserPackage.collectAsStateWithLifecycle()
    var isDefaultBrowser by remember { mutableStateOf(context.isCaptureDefaultBrowser()) }
    var browserChoices by remember(context) { mutableStateOf(captureBrowserChoices(context)) }
    val preferredBrowserPackage =
        resolvePreferredBrowserPackage(previousBrowserPackage, context.packageName)
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

    LjScaffold(
        title = stringResource(R.string.settings_capture_sub_screen_title),
        isSpoofing = isSpoofing,
        onToggleSpoofing = onToggleSpoofing,
        locationLabel = locationLabel,
        onNavigationClick = onNavigateBack,
        navigationIcon = LjIcons.ArrowBack,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = { SettingsSaveDiscardFab(uiState.isDirty, onAction) },
    ) { paddingValues ->
        CaptureSetupForm(
            state =
                CaptureSetupState(
                    captureModeEnabled = captureModeEnabled,
                    isDefaultBrowser = isDefaultBrowser,
                    passThroughBrowserName = selectedBrowser?.label ?: stringResource(R.string.capture_browser_automatic),
                    browserChoices = browserChoices,
                    selectedBrowserPackage = selectedBrowser?.packageName,
                    onSelectBrowser = viewModel::rememberPreviousBrowser,
                    onRequestDefaultBrowser = { context.launchCaptureDefaultBrowser(viewModel::rememberPreviousBrowser) },
                    onOpenMapsLinks = { context.launchCaptureMapsLinks() },
                    onOpenThisAppLinks = { context.launchCaptureThisAppLinks() },
                    onRestoreDefaultApps = { context.launchCaptureRestoreDefaultApps() },
                ),
            modifier = Modifier.padding(paddingValues).padding(16.dp),
        )
    }
}

@Preview
@Composable
private fun SettingsCaptureSubScreenPreview() {
    LjTheme {
        CaptureSetupForm(
            state =
                CaptureSetupState(
                    captureModeEnabled = true,
                    isDefaultBrowser = false,
                    passThroughBrowserName = "Chrome",
                    browserChoices = emptyList(),
                    selectedBrowserPackage = null,
                    onSelectBrowser = {},
                    onRequestDefaultBrowser = {},
                    onOpenMapsLinks = {},
                    onOpenThisAppLinks = {},
                    onRestoreDefaultApps = {},
                ),
            modifier = Modifier.padding(16.dp),
        )
    }
}
