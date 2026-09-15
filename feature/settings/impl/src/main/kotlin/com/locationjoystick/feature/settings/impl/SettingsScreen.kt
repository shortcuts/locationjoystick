package com.locationjoystick.feature.settings.impl

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.component.DestinationCardSpec
import com.locationjoystick.core.designsystem.component.DestinationHub
import com.locationjoystick.core.designsystem.component.LjOverflowMenu
import com.locationjoystick.core.designsystem.component.LjOverflowMenuSectionLabel
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.location.rememberSpoofToggleState
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.feature.settings.impl.R

private enum class SettingsSection { GPS, MENUS, FAVORITES_ROUTES, ROAMING }

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
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val roamingDefaults by viewModel.roamingDefaults.collectAsStateWithLifecycle()
    val isRooted by viewModel.isRooted.collectAsStateWithLifecycle()
    val spoofToggle = rememberSpoofToggleState()
    val context = LocalContext.current
    var pendingImport by remember { mutableStateOf<PendingImport?>(null) }
    var showQrShare by remember { mutableStateOf(false) }
    var qrExportSession by remember { mutableStateOf<SettingsViewModel.QrExportSession?>(null) }
    var showQrScanner by remember { mutableStateOf(false) }
    var showEnterCodeDialog by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
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
                            android.net.Uri.parse(com.locationjoystick.core.common.constants.AppConstants.AppInfo.GITHUB_ISSUES_URL),
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
        uiState = uiState,
        roamingDefaults = roamingDefaults,
        isRooted = isRooted,
        hotLocationTree = viewModel.hotLocationTree,
        hotRouteTree = viewModel.hotRouteTree,
        onOpenDrawer = onOpenDrawer,
        isSpoofing = spoofToggle.isSpoofing,
        onToggleSpoofing = spoofToggle.onToggle,
        locationLabel = spoofToggle.locationLabel,
        onCheckCompassService = { viewModel.checkCompassServiceGranted() },
        onTestCompassDetection = { viewModel.testCompassDetection() },
        launchableApps = viewModel.launchableApps,
        onNavigateUp = onNavigateUp,
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

                is SettingsAction.SetRealismRealElevationEnabled -> {
                    viewModel.setRealismRealElevationEnabled(action.enabled)
                }

                SettingsAction.ResetAltitudeOverride -> {
                    viewModel.resetAltitudeOverride()
                }

                is SettingsAction.SetAltitudeJitterRadius -> {
                    viewModel.setAltitudeJitterRadius(action.meters)
                }

                is SettingsAction.SetAltitudeOverrideButtonEnabled -> {
                    viewModel.setAltitudeOverrideButtonEnabled(action.enabled)
                }

                is SettingsAction.SetDebugStatsEnabled -> {
                    viewModel.setDebugStatsEnabled(action.enabled)
                }

                is SettingsAction.SetTapToWalkOverlayEnabled -> {
                    viewModel.setTapToWalkOverlayEnabled(action.enabled)
                }

                is SettingsAction.SetTapToWalkScaleMpx -> {
                    viewModel.setTapToWalkScaleMpx(action.scale)
                }

                is SettingsAction.SetCompassTestTargetPackage -> {
                    viewModel.setCompassTestTargetPackage(action.packageName)
                }

                is SettingsAction.SetThemeMode -> {
                    viewModel.setThemeMode(action.mode)
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

                SettingsAction.SaveChanges -> {
                    viewModel.saveChanges()
                }

                SettingsAction.DiscardChanges -> {
                    viewModel.discardChanges()
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
        uiState = SettingsUiState(),
        onOpenDrawer = {},
        onAction = {},
    )
}

private sealed class PendingUnsavedIntent {
    object BackToHub : PendingUnsavedIntent()

    object ExitSettings : PendingUnsavedIntent()

    object StartSpoofing : PendingUnsavedIntent()
}

@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    roamingDefaults: RoamingDefaults = RoamingDefaults(),
    isRooted: Boolean = false,
    hotLocationTree: HotItemTree = HotItemTree.Empty,
    hotRouteTree: HotItemTree = HotItemTree.Empty,
    onOpenDrawer: () -> Unit = {},
    isSpoofing: Boolean = false,
    onToggleSpoofing: () -> Unit = {},
    locationLabel: String? = null,
    onAction: (SettingsAction) -> Unit,
    onCheckCompassService: () -> Unit = {},
    onTestCompassDetection: suspend () -> Float? = { null },
    launchableApps: List<InstalledApp> = emptyList(),
    onNavigateUp: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
) {
    var currentSection by remember { mutableStateOf<SettingsSection?>(null) }
    var pendingUnsavedIntent by remember { mutableStateOf<PendingUnsavedIntent?>(null) }

    val guardedBack = {
        if (uiState.isDirty) {
            pendingUnsavedIntent = if (currentSection == null) PendingUnsavedIntent.ExitSettings else PendingUnsavedIntent.BackToHub
        } else if (currentSection == null) {
            onNavigateUp()
        } else {
            currentSection = null
        }
    }
    val guardedToggleSpoofing = {
        if (uiState.isDirty && !isSpoofing) {
            pendingUnsavedIntent = PendingUnsavedIntent.StartSpoofing
        } else {
            onToggleSpoofing()
        }
    }

    BackHandler(onBack = guardedBack)

    pendingUnsavedIntent?.let { intent ->
        val message =
            when (intent) {
                PendingUnsavedIntent.BackToHub, PendingUnsavedIntent.ExitSettings ->
                    stringResource(R.string.settings_unsaved_changes_leave)
                PendingUnsavedIntent.StartSpoofing ->
                    stringResource(R.string.settings_unsaved_changes_start_spoofing)
            }
        UnsavedChangesConfirmDialog(
            message = message,
            onSave = {
                onAction(SettingsAction.SaveChanges)
                pendingUnsavedIntent = null
                when (intent) {
                    PendingUnsavedIntent.BackToHub -> currentSection = null
                    PendingUnsavedIntent.ExitSettings -> onNavigateUp()
                    PendingUnsavedIntent.StartSpoofing -> onToggleSpoofing()
                }
            },
            onDiscard = {
                onAction(SettingsAction.DiscardChanges)
                pendingUnsavedIntent = null
                when (intent) {
                    PendingUnsavedIntent.BackToHub -> currentSection = null
                    PendingUnsavedIntent.ExitSettings -> onNavigateUp()
                    PendingUnsavedIntent.StartSpoofing -> onToggleSpoofing()
                }
            },
            onDismiss = { pendingUnsavedIntent = null },
        )
    }

    when (currentSection) {
        null -> {
            SettingsHubScreen(
                uiState = uiState,
                onOpenDrawer = onOpenDrawer,
                onNavigate = { currentSection = it },
                isSpoofing = isSpoofing,
                onToggleSpoofing = guardedToggleSpoofing,
                locationLabel = locationLabel,
                onAction = onAction,
                bottomBar = bottomBar,
                snackbarHost = snackbarHost,
            )
        }

        SettingsSection.GPS -> {
            SettingsGpsSubScreen(
                uiState = uiState,
                onNavigateBack = guardedBack,
                isSpoofing = isSpoofing,
                onToggleSpoofing = guardedToggleSpoofing,
                locationLabel = locationLabel,
                onAction = onAction,
                bottomBar = bottomBar,
                snackbarHost = snackbarHost,
            )
        }

        SettingsSection.MENUS -> {
            SettingsMenusSubScreen(
                uiState = uiState,
                isRooted = isRooted,
                onNavigateBack = guardedBack,
                isSpoofing = isSpoofing,
                onToggleSpoofing = guardedToggleSpoofing,
                locationLabel = locationLabel,
                onAction = onAction,
                onCheckCompassService = onCheckCompassService,
                onTestCompassDetection = onTestCompassDetection,
                launchableApps = launchableApps,
                bottomBar = bottomBar,
                snackbarHost = snackbarHost,
            )
        }

        SettingsSection.FAVORITES_ROUTES -> {
            SettingsFavoritesRoutesSubScreen(
                uiState = uiState,
                hotLocationTree = hotLocationTree,
                hotRouteTree = hotRouteTree,
                onNavigateBack = guardedBack,
                isSpoofing = isSpoofing,
                onToggleSpoofing = guardedToggleSpoofing,
                locationLabel = locationLabel,
                onAction = onAction,
                bottomBar = bottomBar,
                snackbarHost = snackbarHost,
            )
        }

        SettingsSection.ROAMING -> {
            SettingsRoamingSubScreen(
                uiState = uiState,
                roamingDefaults = roamingDefaults,
                onNavigateBack = guardedBack,
                isSpoofing = isSpoofing,
                onToggleSpoofing = guardedToggleSpoofing,
                locationLabel = locationLabel,
                onAction = onAction,
                bottomBar = bottomBar,
                snackbarHost = snackbarHost,
            )
        }
    }
}

@Composable
private fun SettingsHubScreen(
    uiState: SettingsUiState,
    onOpenDrawer: () -> Unit,
    onNavigate: (SettingsSection) -> Unit,
    isSpoofing: Boolean,
    onToggleSpoofing: () -> Unit,
    locationLabel: String? = null,
    onAction: (SettingsAction) -> Unit,
    bottomBar: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
) {
    LjScaffold(
        title = stringResource(R.string.settings_hub_settings),
        isSpoofing = isSpoofing,
        onToggleSpoofing = onToggleSpoofing,
        locationLabel = locationLabel,
        onNavigationClick = onOpenDrawer,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        actions = {
            LjOverflowMenu { dismiss ->
                LjOverflowMenuSectionLabel(stringResource(R.string.settings_hub_export), showDivider = false)
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_export_via_qr_code)) },
                    onClick = {
                        dismiss()
                        onAction(SettingsAction.QrShare)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_export_settings)) },
                    onClick = {
                        dismiss()
                        onAction(SettingsAction.Export)
                    },
                )
                LjOverflowMenuSectionLabel(stringResource(R.string.settings_hub_import))
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_import_from_qr_code)) },
                    onClick = {
                        dismiss()
                        onAction(SettingsAction.QrScan)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_import_via_code)) },
                    onClick = {
                        dismiss()
                        onAction(SettingsAction.QrEnterCode)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_import_from_file)) },
                    onClick = {
                        dismiss()
                        onAction(SettingsAction.Import)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_import_from_gps_joystick)) },
                    onClick = {
                        dismiss()
                        onAction(SettingsAction.ImportGpsJoystick)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_import_from_yamla)) },
                    onClick = {
                        dismiss()
                        onAction(SettingsAction.ImportYamla)
                    },
                )
                LjOverflowMenuSectionLabel(stringResource(R.string.settings_hub_danger))
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_reset_all_data), color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(LjIcons.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = {
                        dismiss()
                        onAction(SettingsAction.ResetAllData)
                    },
                )
            }
        },
        floatingActionButton = { SettingsSaveDiscardFab(uiState.isDirty, onAction) },
    ) { paddingValues ->
        DestinationHub(
            paddingValues = paddingValues,
            cards =
                listOf(
                    DestinationCardSpec(
                        icon = LjIcons.Speed,
                        title = stringResource(R.string.settings_hub_movement_and_gps),
                        description = stringResource(R.string.settings_hub_movement_and_gps_desc),
                        onClick = { onNavigate(SettingsSection.GPS) },
                    ),
                    DestinationCardSpec(
                        icon = LjIcons.Joystick,
                        title = stringResource(R.string.settings_hub_menus),
                        description = stringResource(R.string.settings_hub_menus_desc),
                        onClick = { onNavigate(SettingsSection.MENUS) },
                    ),
                    DestinationCardSpec(
                        icon = LjIcons.Favorite,
                        title = stringResource(R.string.settings_hub_favorites_and_routes),
                        description = stringResource(R.string.settings_hub_favorites_and_routes_desc),
                        onClick = { onNavigate(SettingsSection.FAVORITES_ROUTES) },
                    ),
                    DestinationCardSpec(
                        icon = LjIcons.Explore,
                        title = stringResource(R.string.settings_hub_roaming),
                        description = stringResource(R.string.settings_hub_roaming_desc),
                        onClick = { onNavigate(SettingsSection.ROAMING) },
                    ),
                ),
        )
    }
}

@Composable
internal fun SettingsSaveDiscardFab(
    isDirty: Boolean,
    onAction: (SettingsAction) -> Unit,
) {
    // Labeled + widely spaced (vs. the old adjacent icon-only pair) so the pending-save
    // state reads clearly and Save/Discard aren't easy to mis-tap for each other.
    AnimatedVisibility(
        visible = isDirty,
        enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 3 },
        exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 6 },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ExtendedFloatingActionButton(
                onClick = { onAction(SettingsAction.DiscardChanges) },
                icon = { Icon(LjIcons.Close, contentDescription = null) },
                text = { Text(stringResource(R.string.settings_discard)) },
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ExtendedFloatingActionButton(
                onClick = { onAction(SettingsAction.SaveChanges) },
                icon = { Icon(LjIcons.Check, contentDescription = null) },
                text = { Text(stringResource(R.string.settings_save)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun UnsavedChangesConfirmDialog(
    message: String,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_save_changes)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onSave) { Text(stringResource(R.string.settings_save_2)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                TextButton(
                    onClick = onDiscard,
                ) { Text(stringResource(R.string.settings_discard_2), color = MaterialTheme.colorScheme.error) }
            }
        },
    )
}

@Composable
private fun ImportConfirmDialog(
    onReplace: () -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_import_data)) },
        text = { Text(stringResource(R.string.settings_how_would_you_like_to_handle)) },
        confirmButton = {
            Row {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                TextButton(onClick = onAdd) { Text(stringResource(R.string.settings_add)) }
                TextButton(onClick = onReplace) { Text(stringResource(R.string.settings_replace), color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = {},
    )
}

@Composable
private fun ResetAllDataConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_reset_all_data_2)) },
        text = { Text(stringResource(R.string.settings_all_favorites_routes_and_settings_will)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.settings_reset), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun EnterExportCodeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_enter_export_code)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            TextButton(onClick = { onConfirm(code) }, enabled = code.length == 6) { Text(stringResource(R.string.settings_import)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
