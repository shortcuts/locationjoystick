package com.locationjoystick.feature.settings.impl

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.designsystem.LjSpacing
import com.locationjoystick.core.location.rememberSpoofToggleState
import com.locationjoystick.core.model.GuidedPath
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.feature.settings.api.SettingsExtension
import com.locationjoystick.feature.settings.impl.R

internal enum class SettingsSection { GPS, REALISM, MENUS, SCREEN_HELPERS, GENERAL, FAVORITES_ROUTES, ROAMING, CAPTURE }

private sealed class PendingImport {
    data class File(
        val uri: android.net.Uri,
    ) : PendingImport()

    data class QrData(
        val data: com.locationjoystick.core.model.ExportData,
    ) : PendingImport()

    data class GpsJoystick(
        val uri: android.net.Uri,
    ) : PendingImport()

    data class Yamla(
        val uri: android.net.Uri,
    ) : PendingImport()
}

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onOpenDrawer: () -> Unit = {},
    onNavigateUp: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    openCaptureSetup: Boolean = false,
    extensionsViewModel: SettingsExtensionsViewModel = hiltViewModel(),
) {
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val roamingDefaults by viewModel.roamingDefaults.collectAsStateWithLifecycle()
    val isRooted by viewModel.isRooted.collectAsStateWithLifecycle()
    val languageTag by viewModel.languageTag.collectAsStateWithLifecycle()
    val spoofToggle = rememberSpoofToggleState()
    val context = LocalContext.current
    var pendingImport by remember { mutableStateOf<PendingImport?>(null) }
    var showQrShare by remember { mutableStateOf(false) }
    var qrExportSession by remember { mutableStateOf<SettingsViewModel.QrExportSession?>(null) }
    var showQrScanner by rememberSaveable { mutableStateOf(false) }
    var showEnterCodeDialog by rememberSaveable { mutableStateOf(false) }
    var showResetConfirm by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val reportActionLabel = stringResource(R.string.settings_hub_report)
    LaunchedEffect(Unit) {
        viewModel.userFeedback.collect { feedback ->
            if (feedback.isError) {
                val result =
                    snackbarHostState.showSnackbar(
                        message = feedback.message,
                        actionLabel = reportActionLabel,
                        duration = androidx.compose.material3.SnackbarDuration.Long,
                    )
                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                    val intent =
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            com.locationjoystick.core.common.constants.AppConstants.AppInfo.GITHUB_ISSUES_URL
                                .toUri(),
                        )
                    context.startActivity(intent)
                }
            } else {
                snackbarHostState.showSnackbar(
                    message = feedback.message,
                    duration = androidx.compose.material3.SnackbarDuration.Short,
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.qrImportReady.collect { exportData ->
            showQrScanner = false
            pendingImport = PendingImport.QrData(exportData)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.qrExportReady.collect { session ->
            qrExportSession = session
            showQrShare = true
        }
    }

    val exportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(AppConstants.ExportConstants.MIME_TYPE),
        ) { uri ->
            if (uri != null) viewModel.writeExportToUri(uri)
        }

    val importLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) pendingImport = PendingImport.File(uri)
        }

    val importGpsJoystickLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) pendingImport = PendingImport.GpsJoystick(uri)
        }

    val importYamlaLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) pendingImport = PendingImport.Yamla(uri)
        }

    val pending = pendingImport
    if (pending != null) {
        ImportConfirmDialog(
            onReplace = {
                when (pending) {
                    is PendingImport.File -> viewModel.importSettings(pending.uri, replace = true)
                    is PendingImport.QrData -> viewModel.importSettings(pending.data, replace = true)
                    is PendingImport.GpsJoystick -> viewModel.importFromGpsJoystick(pending.uri, replace = true)
                    is PendingImport.Yamla -> viewModel.importFromYamla(pending.uri, replace = true)
                }
                pendingImport = null
            },
            onAdd = {
                when (pending) {
                    is PendingImport.File -> viewModel.importSettings(pending.uri, replace = false)
                    is PendingImport.QrData -> viewModel.importSettings(pending.data, replace = false)
                    is PendingImport.GpsJoystick -> viewModel.importFromGpsJoystick(pending.uri, replace = false)
                    is PendingImport.Yamla -> viewModel.importFromYamla(pending.uri, replace = false)
                }
                pendingImport = null
            },
            onDismiss = { pendingImport = null },
        )
    }

    val qrImportFetching by viewModel.qrImportFetching.collectAsStateWithLifecycle()

    if (showQrScanner) {
        QrScannerScreen(
            onQrScanned = viewModel::onQrScanned,
            onPermissionDenied = { showQrScanner = false },
            onNavigateBack = { showQrScanner = false },
            isFetching = qrImportFetching,
        )
        return
    }

    val exportSession = qrExportSession
    val isPreparingQrExport by viewModel.isPreparingQrExport.collectAsStateWithLifecycle()
    if (showQrShare) {
        QrShareDialog(
            qrText = exportSession?.qrText,
            code = exportSession?.code,
            isPreparing = isPreparingQrExport,
            onDismiss = {
                viewModel.stopQrExport()
                showQrShare = false
                qrExportSession = null
            },
        )
    }

    if (showEnterCodeDialog) {
        EnterExportCodeDialog(
            onDismiss = { showEnterCodeDialog = false },
            onConfirm = { code ->
                showEnterCodeDialog = false
                viewModel.onExportCodeEntered(code)
            },
        )
    }

    if (showResetConfirm) {
        ResetAllDataConfirmDialog(
            onConfirm = {
                showResetConfirm = false
                viewModel.resetAllData()
            },
            onDismiss = { showResetConfirm = false },
        )
    }

    SettingsScreen(
        screenState = screenState,
        roamingDefaults = roamingDefaults,
        isRooted = isRooted,
        languageTag = languageTag,
        hotLocationTree = viewModel.hotLocationTree.collectAsStateWithLifecycle().value,
        hotRouteTree = viewModel.hotRouteTree.collectAsStateWithLifecycle().value,
        onOpenDrawer = onOpenDrawer,
        isSpoofing = spoofToggle.isSpoofing,
        onToggleSpoofing = spoofToggle.onToggle,
        locationLabel = spoofToggle.locationLabel,
        onCheckScreenService = { viewModel.checkScreenServiceGranted() },
        launchableApps = viewModel.launchableApps.collectAsStateWithLifecycle().value,
        onNavigateUp = onNavigateUp,
        openCaptureSetup = openCaptureSetup,
        extensions = extensionsViewModel.extensions.toList(),
        onAction = { action ->
            when (action) {
                is SettingsAction.SetSpeed -> {
                    viewModel.setSpeed(action.id, action.displaySpeed)
                }

                is SettingsAction.SetSpeedUnit -> {
                    viewModel.setSpeedUnit(action.unit)
                }

                is SettingsAction.SetWidgetFeatures -> {
                    viewModel.setWidgetFeatures(action.features)
                }

                is SettingsAction.SetEnabledSpeedProfileIds -> {
                    viewModel.setEnabledSpeedProfileIds(action.ids)
                }

                is SettingsAction.SetMapFeatures -> {
                    viewModel.setMapFeatures(action.features)
                }

                is SettingsAction.SetFeatureOrder -> {
                    viewModel.setFeatureOrder(action.order)
                }

                is SettingsAction.SetRememberLastLocation -> {
                    viewModel.setRememberLastLocation(action.enabled)
                }

                is SettingsAction.SetMapFollowsLocation -> {
                    viewModel.setMapFollowsLocation(action.enabled)
                }

                is SettingsAction.SetDisabledGeocodingProviders -> {
                    viewModel.setDisabledGeocodingProviders(action.ids)
                }

                is SettingsAction.SetMapTileSource -> {
                    viewModel.setMapTileSource(action.source)
                }

                is SettingsAction.SetJitterIdleRadius -> {
                    viewModel.setJitterIdleRadius(action.meters)
                }

                is SettingsAction.SetJitterMovingRadius -> {
                    viewModel.setJitterMovingRadius(action.meters)
                }

                is SettingsAction.SetJitterMaxStepMeters -> {
                    viewModel.setJitterMaxStepMeters(action.meters)
                }

                is SettingsAction.SetRealismBearingHoldIdle -> {
                    viewModel.setRealismBearingHoldIdle(action.enabled)
                }

                is SettingsAction.SetRealismAltitudeEnabled -> {
                    viewModel.setRealismAltitudeEnabled(action.enabled)
                }

                is SettingsAction.SetRealismWarmupEnabled -> {
                    viewModel.setRealismWarmupEnabled(action.enabled)
                }

                is SettingsAction.SetRealismSatelliteExtrasEnabled -> {
                    viewModel.setRealismSatelliteExtrasEnabled(action.enabled)
                }

                is SettingsAction.SetRealismSuspendedMockingEnabled -> {
                    viewModel.setRealismSuspendedMockingEnabled(action.enabled)
                }

                is SettingsAction.SetJitterSpeedIdleVariationPct -> {
                    viewModel.setJitterSpeedIdleVariationPct(action.pct)
                }

                is SettingsAction.SetJitterSpeedMovingVariationPct -> {
                    viewModel.setJitterSpeedMovingVariationPct(action.pct)
                }

                is SettingsAction.SetJitterSpeedIdleWobbleProbabilityPct -> {
                    viewModel.setJitterSpeedIdleWobbleProbabilityPct(action.pct)
                }

                is SettingsAction.SetHotLocationsEnabled -> {
                    viewModel.setHotLocationsEnabled(action.enabled)
                }

                is SettingsAction.SetSelectedHotLocationIds -> {
                    viewModel.setSelectedHotLocationIds(action.ids)
                }

                is SettingsAction.SetHotRoutesEnabled -> {
                    viewModel.setHotRoutesEnabled(action.enabled)
                }

                is SettingsAction.SetSelectedHotRouteIds -> {
                    viewModel.setSelectedHotRouteIds(action.ids)
                }

                is SettingsAction.UpdateRoamingDefaults -> {
                    viewModel.updateRoamingDefaults(action.defaults)
                }

                is SettingsAction.SetFloatingMapQuickWalk -> {
                    viewModel.setFloatingMapQuickWalk(action.enabled)
                }

                is SettingsAction.SetHideTeleportFeatures -> {
                    viewModel.setHideTeleportFeatures(action.enabled)
                }

                is SettingsAction.SetHideWidgetOverlay -> {
                    viewModel.setHideWidgetOverlay(action.enabled)
                }

                is SettingsAction.SetHideForegroundNotification -> {
                    viewModel.setHideForegroundNotification(action.enabled)
                }

                is SettingsAction.SetShowRouteJumpButtons -> {
                    viewModel.setShowRouteJumpButtons(action.enabled)
                }

                is SettingsAction.SetLastKnownPositionEnabled -> {
                    viewModel.setLastKnownPositionEnabled(action.enabled)
                }

                is SettingsAction.SetAgpsResetOnStartEnabled -> {
                    viewModel.setAgpsResetOnStartEnabled(action.enabled)
                }

                is SettingsAction.SetRealismRealElevationEnabled -> {
                    viewModel.setRealismRealElevationEnabled(action.enabled)
                }

                SettingsAction.ResetAltitudeOverride -> {
                    viewModel.resetAltitudeOverride()
                }

                is SettingsAction.SetAltitudeJitterRadius -> {
                    viewModel.setAltitudeJitterRadius(action.meters)
                }

                is SettingsAction.SetPlantingRadius -> {
                    viewModel.setPlantingRadius(action.meters)
                }

                is SettingsAction.SetAltitudeOverrideButtonEnabled -> {
                    viewModel.setAltitudeOverrideButtonEnabled(action.enabled)
                }

                is SettingsAction.SetPauseResumeButtonEnabled -> {
                    viewModel.setPauseResumeButtonEnabled(action.enabled)
                }

                is SettingsAction.SetDebugStatsEnabled -> {
                    viewModel.setDebugStatsEnabled(action.enabled)
                }

                is SettingsAction.SetCaptureModeEnabled -> {
                    viewModel.setCaptureModeEnabled(action.enabled)
                }

                is SettingsAction.SetCaptureEnabled -> {
                    viewModel.setCaptureEnabled(action.enabled)
                }

                is SettingsAction.SetJumpEnabled -> {
                    viewModel.setJumpEnabled(action.enabled)
                }

                is SettingsAction.SetCapturePreviousBrowserPackage -> {
                    viewModel.setCapturePreviousBrowserPackage(action.packageName)
                }

                SettingsAction.RestoreCaptureDefaultBrowser -> {
                    viewModel.restoreCaptureDefaultBrowser()
                }

                SettingsAction.ClearCaptureSetupReset -> {
                    viewModel.clearCaptureSetupReset()
                }

                is SettingsAction.CaptureDefaultBrowserChecked -> {
                    viewModel.onCaptureDefaultBrowserChecked(action.isDefault)
                }

                is SettingsAction.SetLaunchAfterLinkPackage -> {
                    viewModel.setLaunchAfterLinkPackage(action.packageName)
                }

                is SettingsAction.SetTapToWalkMoves -> {
                    viewModel.setTapToWalkMoves(action.moves)
                }

                is SettingsAction.SetTapToWalkOverlayEnabled -> {
                    viewModel.setTapToWalkOverlayEnabled(action.enabled)
                }

                is SettingsAction.SetAccessibilityDisclosureAccepted -> {
                    viewModel.setAccessibilityDisclosureAccepted(action.accepted)
                }

                is SettingsAction.SetReconnectTapEnabled -> {
                    viewModel.setReconnectTapEnabled(action.enabled)
                }

                is SettingsAction.SetExternalTeleportEnabled -> viewModel.setExternalTeleportEnabled(action.enabled)

                is SettingsAction.SetGuidedPath -> viewModel.setGuidedPath(action.path)

                is SettingsAction.SetThemeMode -> {
                    viewModel.setThemeMode(action.mode)
                }

                is SettingsAction.SetLanguage -> {
                    viewModel.setLanguage(action.tag)
                    (context as? Activity)?.recreate()
                }

                SettingsAction.Export -> {
                    exportLauncher.launch(
                        "${AppConstants.ExportConstants.FILENAME_PREFIX}-${System.currentTimeMillis()}.json",
                    )
                }

                SettingsAction.Import -> {
                    importLauncher.launch(arrayOf(AppConstants.ExportConstants.MIME_TYPE))
                }

                SettingsAction.ImportGpsJoystick -> {
                    importGpsJoystickLauncher.launch(arrayOf("*/*"))
                }

                SettingsAction.ImportYamla -> {
                    importYamlaLauncher.launch(arrayOf("application/json"))
                }

                SettingsAction.QrShare -> {
                    showQrShare = true
                    viewModel.prepareQrExport()
                }

                SettingsAction.QrScan -> {
                    showQrScanner = true
                }

                SettingsAction.QrEnterCode -> {
                    showEnterCodeDialog = true
                }

                SettingsAction.ResetAllData -> {
                    showResetConfirm = true
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = bottomBar,
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    SettingsScreen(
        screenState = SettingsScreenState.Loaded(SettingsUiState()),
        onOpenDrawer = {},
        onAction = {},
    )
}

@Composable
internal fun SettingsScreen(
    screenState: SettingsScreenState,
    roamingDefaults: RoamingDefaults = RoamingDefaults(),
    isRooted: Boolean = false,
    languageTag: String? = null,
    hotLocationTree: HotItemTree = HotItemTree.Empty,
    hotRouteTree: HotItemTree = HotItemTree.Empty,
    onOpenDrawer: () -> Unit = {},
    isSpoofing: Boolean = false,
    onToggleSpoofing: () -> Unit = {},
    locationLabel: String? = null,
    onAction: (SettingsAction) -> Unit,
    onCheckScreenService: () -> Unit = {},
    launchableApps: List<InstalledApp> = emptyList(),
    onNavigateUp: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    openCaptureSetup: Boolean = false,
    extensions: List<SettingsExtension> = emptyList(),
) {
    var currentSection by rememberSaveable { mutableStateOf(if (openCaptureSetup) SettingsSection.CAPTURE else null) }
    var openExtensionId by rememberSaveable { mutableStateOf<String?>(null) }
    val openExtension = extensions.firstOrNull { it.id == openExtensionId }
    // Opened from the Capture screen: Back returns there, not to the settings hub.
    val atRoot = currentSection == null || (openCaptureSetup && currentSection == SettingsSection.CAPTURE)

    val back = {
        if (atRoot) onNavigateUp() else currentSection = null
    }

    BackHandler(onBack = back)

    if (openExtension != null) {
        BackHandler { openExtensionId = null }
        openExtension.Screen(onBack = { openExtensionId = null })
        return
    }

    when (currentSection) {
        null -> {
            SettingsHubScreen(
                guidedPath = (screenState as? SettingsScreenState.Loaded)?.state?.settings?.guidedPath ?: GuidedPath.DEFAULT,
                onOpenDrawer = onOpenDrawer,
                onNavigate = { currentSection = it },
                extensions = extensions,
                onOpenExtension = { openExtensionId = it },
                isSpoofing = isSpoofing,
                onToggleSpoofing = onToggleSpoofing,
                locationLabel = locationLabel,
                onAction = onAction,
                bottomBar = bottomBar,
                snackbarHost = snackbarHost,
            )
        }

        SettingsSection.GPS -> {
            SettingsGpsSubScreen(
                screenState = screenState,
                onNavigateBack = back,
                isSpoofing = isSpoofing,
                onToggleSpoofing = onToggleSpoofing,
                locationLabel = locationLabel,
                onAction = onAction,
                bottomBar = bottomBar,
                snackbarHost = snackbarHost,
            )
        }

        SettingsSection.REALISM -> {
            SettingsRealismSubScreen(
                screenState = screenState,
                onNavigateBack = back,
                isSpoofing = isSpoofing,
                onToggleSpoofing = onToggleSpoofing,
                locationLabel = locationLabel,
                onAction = onAction,
                bottomBar = bottomBar,
                snackbarHost = snackbarHost,
            )
        }

        SettingsSection.MENUS -> {
            SettingsMenusSubScreen(
                screenState = screenState,
                isRooted = isRooted,
                onNavigateBack = back,
                isSpoofing = isSpoofing,
                onToggleSpoofing = onToggleSpoofing,
                locationLabel = locationLabel,
                onAction = onAction,
                bottomBar = bottomBar,
                snackbarHost = snackbarHost,
            )
        }

        SettingsSection.SCREEN_HELPERS -> {
            SettingsScreenHelpersSubScreen(
                screenState = screenState,
                onNavigateBack = back,
                isSpoofing = isSpoofing,
                onToggleSpoofing = onToggleSpoofing,
                locationLabel = locationLabel,
                onAction = onAction,
                onCheckScreenService = onCheckScreenService,
                bottomBar = bottomBar,
                snackbarHost = snackbarHost,
            )
        }

        SettingsSection.GENERAL -> {
            SettingsGeneralSubScreen(
                screenState = screenState,
                languageTag = languageTag,
                onNavigateBack = back,
                isSpoofing = isSpoofing,
                onToggleSpoofing = onToggleSpoofing,
                locationLabel = locationLabel,
                onAction = onAction,
                bottomBar = bottomBar,
                snackbarHost = snackbarHost,
            )
        }

        SettingsSection.FAVORITES_ROUTES -> {
            SettingsFavoritesRoutesSubScreen(
                screenState = screenState,
                hotLocationTree = hotLocationTree,
                hotRouteTree = hotRouteTree,
                onNavigateBack = back,
                isSpoofing = isSpoofing,
                onToggleSpoofing = onToggleSpoofing,
                locationLabel = locationLabel,
                onAction = onAction,
                bottomBar = bottomBar,
                snackbarHost = snackbarHost,
            )
        }

        SettingsSection.ROAMING -> {
            SettingsRoamingSubScreen(
                screenState = screenState,
                roamingDefaults = roamingDefaults,
                onNavigateBack = back,
                isSpoofing = isSpoofing,
                onToggleSpoofing = onToggleSpoofing,
                locationLabel = locationLabel,
                onAction = onAction,
                bottomBar = bottomBar,
                snackbarHost = snackbarHost,
            )
        }

        SettingsSection.CAPTURE -> {
            SettingsCaptureSubScreen(
                screenState = screenState,
                onNavigateBack = back,
                isSpoofing = isSpoofing,
                onToggleSpoofing = onToggleSpoofing,
                locationLabel = locationLabel,
                onAction = onAction,
                launchableApps = launchableApps,
                bottomBar = bottomBar,
                snackbarHost = snackbarHost,
            )
        }
    }
}

internal fun settingsScrollPadding(): PaddingValues = PaddingValues(LjSpacing.md)
