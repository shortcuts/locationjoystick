package com.locationjoystick.feature.settings.impl

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.component.LjCheckboxRow
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.designsystem.component.LjSegmentedControl
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.core.model.WidgetFeature

private sealed class PendingImport {
    data class File(val uri: android.net.Uri) : PendingImport()
    data class QrData(val data: com.locationjoystick.core.model.ExportData) : PendingImport()
    data class GpsJoystick(val uri: android.net.Uri) : PendingImport()
    data class Yamla(val uri: android.net.Uri) : PendingImport()
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
    val context = LocalContext.current
    var pendingImport by remember { mutableStateOf<PendingImport?>(null) }
    var showQrShare by remember { mutableStateOf(false) }
    var qrChunkResult by remember { mutableStateOf<QrChunker.ChunkResult?>(null) }
    var showQrScanner by remember { mutableStateOf(false) }
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
        viewModel.qrChunksReady.collect { result ->
            qrChunkResult = result
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

    val qrScanProgress by viewModel.qrScanProgress.collectAsStateWithLifecycle()

    if (showQrScanner) {
        QrScannerScreen(
            onChunkScanned = viewModel::onChunkScanned,
            onPermissionDenied = { showQrScanner = false },
            onNavigateBack = { showQrScanner = false },
            scanProgress = qrScanProgress,
        )
        return
    }

    val result = qrChunkResult
    if (showQrShare && result != null) {
        QrShareDialog(
            chunks = result.chunks,
            skippedRoutes = result.skippedRoutes,
            onDismiss = {
                showQrShare = false
                qrChunkResult = null
            },
        )
    }

    SettingsScreen(
        uiState = uiState,
        roamingDefaults = roamingDefaults,
        isRooted = isRooted,
        onOpenDrawer = onOpenDrawer,
        onAction = { action ->
            when (action) {
                is SettingsAction.SetWalkSpeed -> viewModel.setWalkSpeed(action.displaySpeed)
                is SettingsAction.SetRunSpeed -> viewModel.setRunSpeed(action.displaySpeed)
                is SettingsAction.SetBikeSpeed -> viewModel.setBikeSpeed(action.displaySpeed)
                is SettingsAction.SetSpeedUnit -> viewModel.setSpeedUnit(action.unit)
                is SettingsAction.SetWidgetFeatures -> viewModel.setWidgetFeatures(action.features)
                is SettingsAction.SetRememberLastLocation -> viewModel.setRememberLastLocation(action.enabled)
                is SettingsAction.SetMapFollowsLocation -> viewModel.setMapFollowsLocation(action.enabled)
                is SettingsAction.SetJitterIdleRadius -> viewModel.setJitterIdleRadius(action.meters)
                is SettingsAction.SetJitterMovingRadius -> viewModel.setJitterMovingRadius(action.meters)
                is SettingsAction.SetJitterIntervalSeconds -> viewModel.setJitterIntervalSeconds(action.seconds)
                is SettingsAction.SetJitterIdleIntervalSeconds -> viewModel.setJitterIdleIntervalSeconds(action.seconds)
                is SettingsAction.SetRealismBearingHoldIdle -> viewModel.setRealismBearingHoldIdle(action.enabled)
                is SettingsAction.SetRealismAltitudeEnabled -> viewModel.setRealismAltitudeEnabled(action.enabled)
                is SettingsAction.SetRealismWarmupEnabled -> viewModel.setRealismWarmupEnabled(action.enabled)
                is SettingsAction.SetRealismSatelliteExtrasEnabled -> viewModel.setRealismSatelliteExtrasEnabled(action.enabled)
                is SettingsAction.SetRealismSuspendedMockingEnabled -> viewModel.setRealismSuspendedMockingEnabled(action.enabled)
                is SettingsAction.SetJitterSpeedIdleVariationPct -> viewModel.setJitterSpeedIdleVariationPct(action.pct)
                is SettingsAction.SetJitterSpeedMovingVariationPct -> viewModel.setJitterSpeedMovingVariationPct(action.pct)
                is SettingsAction.SetElevationTiltJitterDegrees -> viewModel.setElevationTiltJitterDegrees(action.degrees)
                is SettingsAction.SetElevationNoiseAmplitudeMs2 -> viewModel.setElevationNoiseAmplitudeMs2(action.amplitude)
                is SettingsAction.RequestElevationAccess -> viewModel.requestElevationAccess()
                is SettingsAction.UpdateRoamingDefaults -> viewModel.updateRoamingDefaults(action.defaults)
                SettingsAction.Export -> exportLauncher.launch("${AppConstants.ExportConstants.FILENAME_PREFIX}-${System.currentTimeMillis()}.json")
                SettingsAction.Import -> importLauncher.launch(arrayOf(AppConstants.ExportConstants.MIME_TYPE))
                SettingsAction.ImportGpsJoystick -> importGpsJoystickLauncher.launch(arrayOf("*/*"))
                SettingsAction.ImportYamla -> importYamlaLauncher.launch(arrayOf("application/json"))
                SettingsAction.QrShare -> viewModel.prepareQrChunks()
                SettingsAction.QrScan -> showQrScanner = true
                SettingsAction.SaveChanges -> viewModel.saveChanges()
                SettingsAction.DiscardChanges -> viewModel.discardChanges()
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
    onOpenDrawer: () -> Unit = {},
    onAction: (SettingsAction) -> Unit,
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
) {
    LjScaffold(
        title = "Settings",
        onNavigationClick = onOpenDrawer,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        actions = {
            var showDownloadMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showDownloadMenu = true }) {
                    Icon(LjIcons.FileDownload, contentDescription = "Export")
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
                    Icon(LjIcons.FileUpload, contentDescription = "Import")
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
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                else -> {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                    ) {
                        val isMph = uiState.speedUnit == SpeedUnit.MPH
                        SpeedProfilesSection(uiState, onAction)
                        Spacer(modifier = Modifier.height(24.dp))
                        GpsJitterSection(uiState, isMph, onAction)
                        Spacer(modifier = Modifier.height(24.dp))
                        ElevationJitterSection(
                            uiState,
                            elevationControlsEnabled = WidgetFeature.ELEVATION_CONTROLS in uiState.enabledWidgetFeatures,
                            onAction,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        GpsRealismSection(uiState, onAction)
                        Spacer(modifier = Modifier.height(24.dp))
                        MapSection(uiState, onAction)
                        Spacer(modifier = Modifier.height(24.dp))
                        FloatingWidgetSection(uiState, isRooted, onAction)
                        Spacer(modifier = Modifier.height(24.dp))
                        RoamingSection(roamingDefaults, isMph, onAction)
                    }
                }
            }
        }
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
    val isValid = parsedValue != null && parsedValue in 0.1..15.0

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(0.2f),
        )

        Row(
            modifier =
                Modifier
                    .weight(0.8f)
                    .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = localValue,
                onValueChange = { newValue ->
                    localValue = newValue

                    val parsed = newValue.toDoubleOrNull()
                    if (parsed != null && parsed in 0.1..15.0) {
                        onSpeedChange(parsed)
                        lastSentValue = parsed
                    }
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                isError = localValue.isNotBlank() && !isValid,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                    ),
            )

            Spacer(modifier = Modifier.width(4.dp))

            Text(
                unit,
                modifier = Modifier.width(40.dp),
            )
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
private fun MapSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    Text("Map", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "Controls how the map behaves while spoofing is active.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))

    LjCheckboxRow(
        checked = uiState.rememberLastLocation,
        onCheckedChange = { onAction(SettingsAction.SetRememberLastLocation(it)) },
        title = "Remember last location",
        description = "Restores the last spoofed position when the app restarts, so you don't have to re-enter it.",
    )
    LjCheckboxRow(
        checked = uiState.mapFollowsLocation,
        onCheckedChange = { onAction(SettingsAction.SetMapFollowsLocation(it)) },
        title = "Follow location on map",
        description = "Keeps the map camera centered on the spoofed position as it moves.",
    )
}

@Composable
private fun WidgetFeatureRow(
    feature: WidgetFeature,
    label: String,
    enabledFeatures: Set<WidgetFeature>,
    onAction: (SettingsAction) -> Unit,
    enabled: Boolean = true,
    subtitle: String? = null,
    subtitleColor: androidx.compose.ui.graphics.Color? = null,
    icon: ImageVector? = null,
    onEnabled: (() -> Unit)? = null,
) {
    LjCheckboxRow(
        checked = feature in enabledFeatures,
        onCheckedChange = { isChecked ->
            if (enabled) {
                val updated = enabledFeatures.toMutableSet()
                if (isChecked) {
                    updated.add(feature)
                    onEnabled?.invoke()
                } else {
                    updated.remove(feature)
                }
                onAction(SettingsAction.SetWidgetFeatures(updated))
            }
        },
        title = label,
        description = subtitle,
        enabled = enabled,
        descriptionColor = subtitleColor,
        icon = icon,
    )
}

@Composable
private fun FloatingWidgetSection(
    uiState: SettingsUiState,
    isRooted: Boolean = false,
    onAction: (SettingsAction) -> Unit,
) {
    Text("Floating Widget", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "Choose which quick-access buttons appear in the floating widget overlay.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))

    WidgetFeatureRow(
        feature = WidgetFeature.MAP_FLOATING,
        label = "Map shortcut",
        enabledFeatures = uiState.enabledWidgetFeatures,
        onAction = onAction,
        icon = LjIcons.LocationOn,
        subtitle = "Opens a compact map view without switching to the main app.",
    )
    WidgetFeatureRow(
        feature = WidgetFeature.JOYSTICK_TOGGLE,
        label = "Show/hide joystick",
        enabledFeatures = uiState.enabledWidgetFeatures,
        onAction = onAction,
        icon = LjIcons.Visibility,
        subtitle = "Toggles the floating joystick overlay on or off.",
    )
    WidgetFeatureRow(
        feature = WidgetFeature.JOYSTICK_LOCK,
        label = "Lock joystick",
        enabledFeatures = uiState.enabledWidgetFeatures,
        onAction = onAction,
        icon = LjIcons.Lock,
        subtitle = "Keeps the joystick moving in the last held direction after you release.",
    )
    WidgetFeatureRow(
        feature = WidgetFeature.ROUTES_FLOATING,
        label = "Routes picker",
        enabledFeatures = uiState.enabledWidgetFeatures,
        onAction = onAction,
        icon = LjIcons.Route,
        subtitle = "Lists saved routes and starts replay without opening the app.",
    )
    WidgetFeatureRow(
        feature = WidgetFeature.FAVORITES_FLOATING,
        label = "Favorites picker",
        enabledFeatures = uiState.enabledWidgetFeatures,
        onAction = onAction,
        icon = LjIcons.Favorite,
        subtitle = "Lists favorite locations with one-tap teleport and walk shortcuts.",
    )
    WidgetFeatureRow(
        feature = WidgetFeature.SPEED_CYCLE,
        label = "Speed cycle",
        enabledFeatures = uiState.enabledWidgetFeatures,
        onAction = onAction,
        icon = LjIcons.Speed,
        subtitle = "Cycles through Walk, Run, and Bike speed profiles with a single tap.",
    )
    WidgetFeatureRow(
        feature = WidgetFeature.ELEVATION_CONTROLS,
        label = "Elevation controls",
        enabledFeatures = uiState.enabledWidgetFeatures,
        onAction = onAction,
        icon = LjIcons.Layers,
        subtitle = "Shows a floating overlay to tilt the simulated sensor angle · requires root",
        subtitleColor = MaterialTheme.colorScheme.error,
        onEnabled = { onAction(SettingsAction.RequestElevationAccess) },
    )
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
