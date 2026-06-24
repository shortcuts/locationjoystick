package com.locationjoystick.feature.settings.impl

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.component.AppIcon
import com.locationjoystick.core.designsystem.component.LjCheckboxRow
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.designsystem.component.LjSegmentedControl
import com.locationjoystick.core.location.rememberSpoofToggleState
import com.locationjoystick.core.model.AppFeature
import com.locationjoystick.core.model.FeatureSurface
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.SpeedUnit
import kotlin.math.roundToInt

private enum class SettingsSection { GPS, MENUS, FAVORITES_ROUTES }

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
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.userFeedback.collect { feedback ->
            if (feedback.isError) {
                val result =
                    snackbarHostState.showSnackbar(
                        message = feedback.message,
                        actionLabel = "Report",
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
    if (showQrShare && exportSession != null) {
        QrShareDialog(
            qrText = exportSession.qrText,
            code = exportSession.code,
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

    SettingsScreen(
        uiState = uiState,
        roamingDefaults = roamingDefaults,
        isRooted = isRooted,
        hotLocationTree = viewModel.hotLocationTree,
        hotRouteTree = viewModel.hotRouteTree,
        onOpenDrawer = onOpenDrawer,
        isSpoofing = spoofToggle.isSpoofing,
        onToggleSpoofing = spoofToggle.onToggle,
        onAction = { action ->
            when (action) {
                is SettingsAction.SetWalkSpeed -> {
                    viewModel.setWalkSpeed(action.displaySpeed)
                }

                is SettingsAction.SetRunSpeed -> {
                    viewModel.setRunSpeed(action.displaySpeed)
                }

                is SettingsAction.SetBikeSpeed -> {
                    viewModel.setBikeSpeed(action.displaySpeed)
                }

                is SettingsAction.SetSpeedUnit -> {
                    viewModel.setSpeedUnit(action.unit)
                }

                is SettingsAction.SetWidgetFeatures -> {
                    viewModel.setWidgetFeatures(action.features)
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

                is SettingsAction.SetJitterIntervalSeconds -> {
                    viewModel.setJitterIntervalSeconds(action.seconds)
                }

                is SettingsAction.SetJitterIdleIntervalSeconds -> {
                    viewModel.setJitterIdleIntervalSeconds(action.seconds)
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

                is SettingsAction.SetRealismPedometerMockingEnabled -> {
                    viewModel.setRealismPedometerMockingEnabled(action.enabled)
                }

                is SettingsAction.SetJitterSpeedIdleVariationPct -> {
                    viewModel.setJitterSpeedIdleVariationPct(action.pct)
                }

                is SettingsAction.SetJitterSpeedMovingVariationPct -> {
                    viewModel.setJitterSpeedMovingVariationPct(action.pct)
                }

                is SettingsAction.SetElevationTiltJitterDegrees -> {
                    viewModel.setElevationTiltJitterDegrees(action.degrees)
                }

                is SettingsAction.SetElevationNoiseAmplitudeMs2 -> {
                    viewModel.setElevationNoiseAmplitudeMs2(action.amplitude)
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

                is SettingsAction.RequestElevationAccess -> {
                    viewModel.requestElevationAccess()
                }

                is SettingsAction.UpdateRoamingDefaults -> {
                    viewModel.updateRoamingDefaults(action.defaults)
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
    onAction: (SettingsAction) -> Unit,
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
) {
    var currentSection by remember { mutableStateOf<SettingsSection?>(null) }

    BackHandler(enabled = currentSection != null) {
        currentSection = null
    }

    when (currentSection) {
        null -> {
            SettingsHubScreen(
                uiState = uiState,
                onOpenDrawer = onOpenDrawer,
                onNavigate = { currentSection = it },
                isSpoofing = isSpoofing,
                onToggleSpoofing = onToggleSpoofing,
                onAction = onAction,
                bottomBar = bottomBar,
                snackbarHost = snackbarHost,
            )
        }

        SettingsSection.GPS -> {
            SettingsGpsSubScreen(
                uiState = uiState,
                isRooted = isRooted,
                onNavigateBack = { currentSection = null },
                isSpoofing = isSpoofing,
                onToggleSpoofing = onToggleSpoofing,
                onAction = onAction,
                bottomBar = bottomBar,
                snackbarHost = snackbarHost,
            )
        }

        SettingsSection.MENUS -> {
            SettingsMenusSubScreen(
                uiState = uiState,
                isRooted = isRooted,
                onNavigateBack = { currentSection = null },
                isSpoofing = isSpoofing,
                onToggleSpoofing = onToggleSpoofing,
                onAction = onAction,
                bottomBar = bottomBar,
                snackbarHost = snackbarHost,
            )
        }

        SettingsSection.FAVORITES_ROUTES -> {
            SettingsFavoritesRoutesSubScreen(
                uiState = uiState,
                roamingDefaults = roamingDefaults,
                hotLocationTree = hotLocationTree,
                hotRouteTree = hotRouteTree,
                onNavigateBack = { currentSection = null },
                isSpoofing = isSpoofing,
                onToggleSpoofing = onToggleSpoofing,
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
    onAction: (SettingsAction) -> Unit,
    bottomBar: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
) {
    LjScaffold(
        title = "Settings",
        isSpoofing = isSpoofing,
        onToggleSpoofing = onToggleSpoofing,
        onNavigationClick = onOpenDrawer,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        actions = {
            var showDownloadMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showDownloadMenu = true }) {
                    Icon(LjIcons.FileUpload, contentDescription = "Export")
                }
                DropdownMenu(expanded = showDownloadMenu, onDismissRequest = { showDownloadMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Export via QR code") },
                        onClick = {
                            showDownloadMenu = false
                            onAction(SettingsAction.QrShare)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Export settings") },
                        onClick = {
                            showDownloadMenu = false
                            onAction(SettingsAction.Export)
                        },
                    )
                }
            }
            var showUploadMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showUploadMenu = true }) {
                    Icon(LjIcons.FileDownload, contentDescription = "Import")
                }
                DropdownMenu(expanded = showUploadMenu, onDismissRequest = { showUploadMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Import from QR code") },
                        onClick = {
                            showUploadMenu = false
                            onAction(SettingsAction.QrScan)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Import via code") },
                        onClick = {
                            showUploadMenu = false
                            onAction(SettingsAction.QrEnterCode)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Import from file") },
                        onClick = {
                            showUploadMenu = false
                            onAction(SettingsAction.Import)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Import from GPS Joystick") },
                        onClick = {
                            showUploadMenu = false
                            onAction(SettingsAction.ImportGpsJoystick)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Import from YAMLA") },
                        onClick = {
                            showUploadMenu = false
                            onAction(SettingsAction.ImportYamla)
                        },
                    )
                }
            }
            if (uiState.isDirty) {
                TextButton(
                    onClick = { onAction(SettingsAction.DiscardChanges) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                ) { Text("Discard") }
                TextButton(
                    onClick = { onAction(SettingsAction.SaveChanges) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                ) { Text("Save") }
            }
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(remember { ScrollState(0) })
                    .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            AppIcon()
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "locationjoystick",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "v${AppConstants.AppInfo.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(28.dp))
            SettingsDestinationCard(
                icon = LjIcons.LocationOn,
                title = "GPS Settings",
                description = "Speed profiles, GPS signal realism, and location jitter.",
                onClick = { onNavigate(SettingsSection.GPS) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            SettingsDestinationCard(
                icon = LjIcons.Joystick,
                title = "Menus",
                description = "Quick-access buttons in the floating widget and map screen.",
                onClick = { onNavigate(SettingsSection.MENUS) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            SettingsDestinationCard(
                icon = LjIcons.Favorite,
                title = "Favorites & Routes",
                description = "Hot locations and default roaming settings.",
                onClick = { onNavigate(SettingsSection.FAVORITES_ROUTES) },
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsDestinationCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SubScreenActions(
    isDirty: Boolean,
    onAction: (SettingsAction) -> Unit,
) {
    if (isDirty) {
        TextButton(
            onClick = { onAction(SettingsAction.DiscardChanges) },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        ) { Text("Discard") }
        TextButton(
            onClick = { onAction(SettingsAction.SaveChanges) },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        ) { Text("Save") }
    }
}

@Composable
private fun SettingsGpsSubScreen(
    uiState: SettingsUiState,
    isRooted: Boolean,
    onNavigateBack: () -> Unit,
    isSpoofing: Boolean,
    onToggleSpoofing: () -> Unit,
    onAction: (SettingsAction) -> Unit,
    bottomBar: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
) {
    LjScaffold(
        title = "GPS Settings",
        isSpoofing = isSpoofing,
        onToggleSpoofing = onToggleSpoofing,
        onNavigationClick = onNavigateBack,
        navigationIcon = LjIcons.ArrowBack,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        actions = { SubScreenActions(uiState.isDirty, onAction) },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                else -> {
                    val isMph = uiState.speedUnit == SpeedUnit.MPH
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(remember { ScrollState(0) })
                                .padding(16.dp),
                    ) {
                        SpeedProfilesSection(uiState, onAction)
                        Spacer(modifier = Modifier.height(24.dp))
                        GpsJitterSection(uiState, isMph, onAction)
                        if (AppFeature.ELEVATION_CONTROLS in uiState.enabledWidgetFeatures) {
                            Spacer(modifier = Modifier.height(24.dp))
                            ElevationJitterSection(uiState, elevationControlsEnabled = true, onAction)
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        GpsRealismSection(uiState, isRooted, onAction)
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Location Memory", style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Controls how the app handles location state across restarts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LjCheckboxRow(
                            checked = uiState.rememberLastLocation,
                            onCheckedChange = { onAction(SettingsAction.SetRememberLastLocation(it)) },
                            title = "Remember last location",
                            description = "Restores the last spoofed position when the app restarts.",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsMenusSubScreen(
    uiState: SettingsUiState,
    isRooted: Boolean,
    onNavigateBack: () -> Unit,
    isSpoofing: Boolean,
    onToggleSpoofing: () -> Unit,
    onAction: (SettingsAction) -> Unit,
    bottomBar: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
) {
    LjScaffold(
        title = "Menus",
        isSpoofing = isSpoofing,
        onToggleSpoofing = onToggleSpoofing,
        onNavigationClick = onNavigateBack,
        navigationIcon = LjIcons.ArrowBack,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        actions = { SubScreenActions(uiState.isDirty, onAction) },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                else -> {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(remember { ScrollState(0) })
                                .padding(16.dp),
                    ) {
                        AppFeaturesSection(uiState, isRooted, onAction)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsFavoritesRoutesSubScreen(
    uiState: SettingsUiState,
    roamingDefaults: RoamingDefaults,
    hotLocationTree: HotItemTree,
    hotRouteTree: HotItemTree,
    onNavigateBack: () -> Unit,
    isSpoofing: Boolean,
    onToggleSpoofing: () -> Unit,
    onAction: (SettingsAction) -> Unit,
    bottomBar: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
) {
    LjScaffold(
        title = "Favorites & Routes",
        isSpoofing = isSpoofing,
        onToggleSpoofing = onToggleSpoofing,
        onNavigationClick = onNavigateBack,
        navigationIcon = LjIcons.ArrowBack,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        actions = { SubScreenActions(uiState.isDirty, onAction) },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                else -> {
                    val isMph = uiState.speedUnit == SpeedUnit.MPH
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(remember { ScrollState(0) })
                                .padding(16.dp),
                    ) {
                        FavoritesSection(uiState, hotLocationTree, onAction)
                        Spacer(modifier = Modifier.height(24.dp))
                        RoutesSection(uiState, hotRouteTree, onAction)
                        Spacer(modifier = Modifier.height(24.dp))
                        RoamingSection(roamingDefaults, isMph, onAction)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutesSection(
    uiState: SettingsUiState,
    hotRouteTree: HotItemTree,
    onAction: (SettingsAction) -> Unit,
) {
    Text("Routes", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "Options for the routes list.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))
    LjCheckboxRow(
        checked = uiState.hotRoutesEnabled,
        onCheckedChange = { onAction(SettingsAction.SetHotRoutesEnabled(it)) },
        title = "Show hot routes",
        description = "Adds a curated set of pre-built routes to your routes list. Select which ones to include below.",
    )
    if (uiState.hotRoutesEnabled && hotRouteTree.allIds.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        HotItemTreeSection(
            headerLabel = "Routes",
            tree = hotRouteTree,
            selectedIds = uiState.selectedHotRouteIds,
            onSelectionChange = { onAction(SettingsAction.SetSelectedHotRouteIds(it)) },
        )
    }
}

@Composable
private fun SpeedProfileInput(
    label: String,
    displaySpeed: Double,
    onSpeedChange: (Double) -> Unit,
    unit: String,
) {
    var localValue by remember { mutableStateOf(String.format("%.1f", displaySpeed)) }
    var lastSentValue by remember { mutableStateOf(displaySpeed) }

    LaunchedEffect(displaySpeed) {
        if (displaySpeed != lastSentValue) {
            localValue = String.format("%.1f", displaySpeed)
            lastSentValue = displaySpeed
        }
    }

    val parsedValue = localValue.toDoubleOrNull()
    val isValid = parsedValue != null && parsedValue > 0.0

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(0.2f))

        Row(
            modifier = Modifier.weight(0.8f).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = localValue,
                onValueChange = { newValue ->
                    localValue = newValue
                    val parsed = newValue.toDoubleOrNull()
                    if (parsed != null && parsed > 0.0) {
                        onSpeedChange(parsed)
                        lastSentValue = parsed
                    }
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                isError = localValue.isNotBlank() && !isValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(unit, modifier = Modifier.width(40.dp))
        }
    }
}

@Composable
private fun AntiCheatWarning() {
    Text(
        text = "Speed exceeds 8 m/s — may trigger anti-cheat in some games",
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
    )
}

private fun convertMsToDisplay(
    ms: Double,
    unit: SpeedUnit,
): Double =
    when (unit) {
        SpeedUnit.KMH -> ms * 3.6
        SpeedUnit.MPH -> ms * 2.237
    }

@Composable
private fun SpeedProfilesSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    Text("Speed Profiles", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "Movement speed used by the joystick, route replay, and roaming. Select a unit, then set each preset.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Unit", modifier = Modifier.weight(0.3f))
        LjSegmentedControl(
            options = listOf(SpeedUnit.KMH to "km/h", SpeedUnit.MPH to "mph"),
            selected = uiState.speedUnit,
            onSelect = { onAction(SettingsAction.SetSpeedUnit(it)) },
            modifier = Modifier.weight(0.7f),
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    SpeedProfileInput(
        label = "Walk",
        displaySpeed = convertMsToDisplay(uiState.walkSpeed, uiState.speedUnit),
        onSpeedChange = { onAction(SettingsAction.SetWalkSpeed(it)) },
        unit = if (uiState.speedUnit == SpeedUnit.KMH) "km/h" else "mph",
    )
    if (uiState.walkSpeed > AppConstants.ProfileConstants.ANTI_CHEAT_WARNING_THRESHOLD_MS) AntiCheatWarning()

    Spacer(modifier = Modifier.height(8.dp))

    SpeedProfileInput(
        label = "Run",
        displaySpeed = convertMsToDisplay(uiState.runSpeed, uiState.speedUnit),
        onSpeedChange = { onAction(SettingsAction.SetRunSpeed(it)) },
        unit = if (uiState.speedUnit == SpeedUnit.KMH) "km/h" else "mph",
    )
    if (uiState.runSpeed > AppConstants.ProfileConstants.ANTI_CHEAT_WARNING_THRESHOLD_MS) AntiCheatWarning()

    Spacer(modifier = Modifier.height(8.dp))

    SpeedProfileInput(
        label = "Bike",
        displaySpeed = convertMsToDisplay(uiState.bikeSpeed, uiState.speedUnit),
        onSpeedChange = { onAction(SettingsAction.SetBikeSpeed(it)) },
        unit = if (uiState.speedUnit == SpeedUnit.KMH) "km/h" else "mph",
    )
    if (uiState.bikeSpeed > AppConstants.ProfileConstants.ANTI_CHEAT_WARNING_THRESHOLD_MS) AntiCheatWarning()
}

@Composable
private fun FavoritesSection(
    uiState: SettingsUiState,
    hotLocationTree: HotItemTree,
    onAction: (SettingsAction) -> Unit,
) {
    Text("Favorites", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "Options for the favorites list.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))
    LjCheckboxRow(
        checked = uiState.hotLocationsEnabled,
        onCheckedChange = { onAction(SettingsAction.SetHotLocationsEnabled(it)) },
        title = "Show hot locations",
        description = "Adds a curated list of popular locations to your favorites. Select which ones to include below.",
    )
    if (uiState.hotLocationsEnabled && hotLocationTree.allIds.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        HotItemTreeSection(
            headerLabel = "Locations",
            tree = hotLocationTree,
            selectedIds = uiState.selectedHotLocationIds,
            onSelectionChange = { onAction(SettingsAction.SetSelectedHotLocationIds(it)) },
        )
    }
}

private data class FeatureMeta(
    val label: String,
    val subtitle: String,
    val icon: ImageVector,
    val isRootGated: Boolean = false,
)

private fun featureMeta(feature: AppFeature): FeatureMeta =
    when (feature) {
        AppFeature.MAP_FLOATING -> {
            FeatureMeta("Map shortcut", "Opens a compact map view without switching to the main app.", LjIcons.LocationOn)
        }

        AppFeature.JOYSTICK_TOGGLE -> {
            FeatureMeta("Show/hide joystick", "Toggles the floating joystick overlay on or off.", LjIcons.Visibility)
        }

        AppFeature.JOYSTICK_LOCK -> {
            FeatureMeta(
                "Lock joystick",
                "Keeps the joystick moving in the last held direction after you release.",
                LjIcons.Lock,
            )
        }

        AppFeature.FAVORITES -> {
            FeatureMeta("Favorites", "Teleport or walk to a saved location.", LjIcons.Favorite)
        }

        AppFeature.ROUTES -> {
            FeatureMeta("Routes", "Lists saved routes and starts replay.", LjIcons.Route)
        }

        AppFeature.ROAMING -> {
            FeatureMeta("Roaming", "Configure and start random walking within a radius.", LjIcons.Explore)
        }

        AppFeature.SEARCH -> {
            FeatureMeta("Search", "Find and jump to a place by name.", LjIcons.Search)
        }

        AppFeature.SPEED_CYCLE -> {
            FeatureMeta("Speed cycle", "Cycles through Walk, Run, and Bike speed profiles with a single tap.", LjIcons.Speed)
        }

        AppFeature.ELEVATION_CONTROLS -> {
            FeatureMeta(
                "Elevation controls",
                "Shows a floating overlay to tilt the simulated sensor angle · requires root",
                LjIcons.Layers,
                isRootGated = true,
            )
        }
    }

private val FEATURE_ROW_HEIGHT = 56.dp

@Composable
private fun AppFeaturesSection(
    uiState: SettingsUiState,
    isRooted: Boolean,
    onAction: (SettingsAction) -> Unit,
) {
    Text("App Features", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "Choose which quick-access features appear in the floating widget and on the map screen, " +
            "and drag to reorder them. Both surfaces share the same order by default.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))

    Row(modifier = Modifier.fillMaxWidth().padding(start = 48.dp), horizontalArrangement = Arrangement.End) {
        Text("Widget", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(56.dp), textAlign = TextAlign.Center)
        Text("Map", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(56.dp), textAlign = TextAlign.Center)
    }

    val order = uiState.featureOrder
    val rowHeightPx = with(LocalDensity.current) { FEATURE_ROW_HEIGHT.toPx() }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragDeltaY by remember { mutableStateOf(0f) }

    Column {
        order.forEachIndexed { index, feature ->
            val isDragging = draggingIndex == index
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging) 1f else 0f)
                        .let { mod ->
                            if (isDragging) {
                                mod.graphicsLayerTranslationY(dragDeltaY)
                            } else {
                                mod
                            }
                        },
            ) {
                FeatureRow(
                    feature = feature,
                    isRooted = isRooted,
                    uiState = uiState,
                    onAction = onAction,
                    dragModifier =
                        Modifier.pointerInput(feature) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingIndex = index
                                    dragDeltaY = 0f
                                },
                                onDragEnd = {
                                    val targetIndex =
                                        (index + (dragDeltaY / rowHeightPx).roundToInt()).coerceIn(0, order.lastIndex)
                                    if (targetIndex != index) {
                                        val newOrder = order.toMutableList()
                                        val moved = newOrder.removeAt(index)
                                        newOrder.add(targetIndex, moved)
                                        onAction(SettingsAction.SetFeatureOrder(newOrder))
                                    }
                                    draggingIndex = null
                                    dragDeltaY = 0f
                                },
                                onDragCancel = {
                                    draggingIndex = null
                                    dragDeltaY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragDeltaY += dragAmount.y
                                },
                            )
                        },
                )
            }
        }
    }
}

private fun Modifier.graphicsLayerTranslationY(ty: Float): Modifier = this.then(Modifier.graphicsLayer { translationY = ty })

@Composable
private fun FeatureRow(
    feature: AppFeature,
    isRooted: Boolean,
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    dragModifier: Modifier,
) {
    val meta = featureMeta(feature)
    val rowEnabled = !meta.isRootGated || isRooted
    Row(
        modifier = Modifier.fillMaxWidth().height(FEATURE_ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = LjIcons.DragHandle,
            contentDescription = "Drag to reorder ${meta.label}",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = dragModifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = meta.icon,
            contentDescription = null,
            tint = if (rowEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            Text(
                text = meta.label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (rowEnabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            Text(
                text = meta.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (meta.isRootGated) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (FeatureSurface.WIDGET in feature.surfaces) {
            Checkbox(
                checked = feature in uiState.enabledWidgetFeatures,
                enabled = rowEnabled,
                modifier = Modifier.width(56.dp),
                onCheckedChange = { checked ->
                    val updated = uiState.enabledWidgetFeatures.toMutableSet()
                    if (checked) {
                        updated.add(feature)
                        if (meta.isRootGated) onAction(SettingsAction.RequestElevationAccess)
                    } else {
                        updated.remove(feature)
                    }
                    onAction(SettingsAction.SetWidgetFeatures(updated))
                },
            )
        } else {
            Spacer(modifier = Modifier.width(56.dp))
        }
        if (FeatureSurface.MAP in feature.surfaces) {
            Checkbox(
                checked = feature in uiState.enabledMapFeatures,
                modifier = Modifier.width(56.dp),
                onCheckedChange = { checked ->
                    val updated = uiState.enabledMapFeatures.toMutableSet()
                    if (checked) updated.add(feature) else updated.remove(feature)
                    onAction(SettingsAction.SetMapFeatures(updated))
                },
            )
        } else {
            Spacer(modifier = Modifier.width(56.dp))
        }
    }
}

@Composable
private fun RoamingSection(
    roamingDefaults: RoamingDefaults,
    isMph: Boolean,
    onAction: (SettingsAction) -> Unit,
) {
    Text("Roaming", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "Default settings used when starting a roaming session from the map.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))

    var radiusText by remember(isMph) {
        mutableStateOf(
            if (isMph) {
                String.format("%.2f", roamingDefaults.radiusMeters / 1609.344)
            } else {
                roamingDefaults.radiusMeters.toInt().toString()
            },
        )
    }
    OutlinedTextField(
        value = radiusText,
        onValueChange = { text ->
            radiusText = text
            text.toDoubleOrNull()?.let { v ->
                val meters = if (isMph) v * 1609.344 else v
                onAction(SettingsAction.UpdateRoamingDefaults(roamingDefaults.copy(radiusMeters = meters.coerceIn(1_000.0, 100_000.0))))
            }
        },
        label = { Text(if (isMph) "Radius (mi)" else "Radius (m)") },
        keyboardOptions = KeyboardOptions(keyboardType = if (isMph) KeyboardType.Decimal else KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(4.dp))

    var distanceText by remember(isMph) {
        mutableStateOf(
            if (isMph) {
                String.format("%.2f", roamingDefaults.distanceMeters / 1609.344)
            } else {
                roamingDefaults.distanceMeters.toInt().toString()
            },
        )
    }
    OutlinedTextField(
        value = distanceText,
        onValueChange = { text ->
            distanceText = text
            text.toDoubleOrNull()?.let { v ->
                val meters = if (isMph) v * 1609.344 else v
                onAction(SettingsAction.UpdateRoamingDefaults(roamingDefaults.copy(distanceMeters = meters.coerceIn(50.0, 50_000.0))))
            }
        },
        label = { Text(if (isMph) "Route distance (mi)" else "Route distance (m)") },
        keyboardOptions = KeyboardOptions(keyboardType = if (isMph) KeyboardType.Decimal else KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(4.dp))

    Text("Speed profile", style = MaterialTheme.typography.labelLarge)
    Spacer(modifier = Modifier.height(4.dp))
    LjSegmentedControl(
        options = listOf("walk" to "Walk", "run" to "Run", "bike" to "Bike"),
        selected = roamingDefaults.speedProfileId,
        onSelect = { onAction(SettingsAction.UpdateRoamingDefaults(roamingDefaults.copy(speedProfileId = it))) },
        modifier = Modifier.fillMaxWidth(),
    )

    LjCheckboxRow(
        checked = roamingDefaults.followRoads,
        onCheckedChange = { onAction(SettingsAction.UpdateRoamingDefaults(roamingDefaults.copy(followRoads = it))) },
        title = "Follow roads",
        description = "Uses OSRM road routing to stay on walkable paths. Falls back to straight-line if unavailable.",
    )
    LjCheckboxRow(
        checked = roamingDefaults.returnToInitialLocation,
        onCheckedChange = { onAction(SettingsAction.UpdateRoamingDefaults(roamingDefaults.copy(returnToInitialLocation = it))) },
        title = "Return to start",
        description = "Walks back to the starting position after the roaming session completes.",
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
        title = { Text("Import data") },
        text = { Text("How would you like to handle existing data?") },
        confirmButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = onAdd) { Text("Add") }
                TextButton(onClick = onReplace) { Text("Replace") }
            }
        },
        dismissButton = {},
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
        title = { Text("Enter export code") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Ask the sender for their 6-character code shown in the QR share dialog.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(6) },
                    label = { Text("Code") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (code.length == 6) onConfirm(code) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(code) }, enabled = code.length == 6) { Text("Import") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
