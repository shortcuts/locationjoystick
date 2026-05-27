package com.locationjoystick.feature.settings.impl

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.designsystem.LjAccent
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.core.model.WidgetFeature
import kotlin.math.roundToInt

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onOpenDrawer: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val roamingDefaults by viewModel.roamingDefaults.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingQrImportData by remember { mutableStateOf<com.locationjoystick.core.model.ExportData?>(null) }
    var pendingGpsJoystickUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingYamlaUri by remember { mutableStateOf<android.net.Uri?>(null) }
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
            pendingQrImportData = exportData
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
            if (uri != null) viewModel.writeExportToUri(context, uri)
        }

    val importLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) pendingImportUri = uri
        }

    val importGpsJoystickLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) pendingGpsJoystickUri = uri
        }

    val importYamlaLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) pendingYamlaUri = uri
        }

    if (pendingImportUri != null) {
        val uri = pendingImportUri!!
        ImportConfirmDialog(
            onReplace = {
                viewModel.importSettings(context, uri, replace = true)
                pendingImportUri = null
            },
            onAdd = {
                viewModel.importSettings(context, uri, replace = false)
                pendingImportUri = null
            },
            onDismiss = { pendingImportUri = null },
        )
    }

    if (pendingQrImportData != null) {
        val exportData = pendingQrImportData!!
        ImportConfirmDialog(
            onReplace = {
                viewModel.importSettings(exportData, replace = true)
                pendingQrImportData = null
            },
            onAdd = {
                viewModel.importSettings(exportData, replace = false)
                pendingQrImportData = null
            },
            onDismiss = { pendingQrImportData = null },
        )
    }

    if (pendingGpsJoystickUri != null) {
        val uri = pendingGpsJoystickUri!!
        ImportConfirmDialog(
            onReplace = {
                viewModel.importFromGpsJoystick(context, uri, replace = true)
                pendingGpsJoystickUri = null
            },
            onAdd = {
                viewModel.importFromGpsJoystick(context, uri, replace = false)
                pendingGpsJoystickUri = null
            },
            onDismiss = { pendingGpsJoystickUri = null },
        )
    }

    if (pendingYamlaUri != null) {
        val uri = pendingYamlaUri!!
        ImportConfirmDialog(
            onReplace = {
                viewModel.importFromYamla(context, uri, replace = true)
                pendingYamlaUri = null
            },
            onAdd = {
                viewModel.importFromYamla(context, uri, replace = false)
                pendingYamlaUri = null
            },
            onDismiss = { pendingYamlaUri = null },
        )
    }

    if (showQrScanner) {
        QrScannerScreen(
            onChunkScanned = viewModel::onChunkScanned,
            onPermissionDenied = { showQrScanner = false },
            onNavigateBack = { showQrScanner = false },
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
        onOpenDrawer = onOpenDrawer,
        onSetWalkSpeed = viewModel::setWalkSpeed,
        onSetRunSpeed = viewModel::setRunSpeed,
        onSetBikeSpeed = viewModel::setBikeSpeed,
        onSetSpeedUnit = viewModel::setSpeedUnit,
        onSetWidgetFeatures = viewModel::setWidgetFeatures,
        onSetRememberLastLocation = viewModel::setRememberLastLocation,
        onSetMapFollowsLocation = viewModel::setMapFollowsLocation,
        onSetJitterIdleRadius = viewModel::setJitterIdleRadius,
        onSetJitterMovingRadius = viewModel::setJitterMovingRadius,
        onSetJitterIntervalSeconds = viewModel::setJitterIntervalSeconds,
        onSetJitterIdleIntervalSeconds = viewModel::setJitterIdleIntervalSeconds,
        onSetRealismBearingHoldIdle = viewModel::setRealismBearingHoldIdle,
        onSetRealismAltitudeEnabled = viewModel::setRealismAltitudeEnabled,
        onSetRealismWarmupEnabled = viewModel::setRealismWarmupEnabled,
        onSetRealismSatelliteExtrasEnabled = viewModel::setRealismSatelliteExtrasEnabled,
        onSetRealismSuspendedMockingEnabled = viewModel::setRealismSuspendedMockingEnabled,
        onSetJitterSpeedIdleVariationPct = viewModel::setJitterSpeedIdleVariationPct,
        onSetJitterSpeedMovingVariationPct = viewModel::setJitterSpeedMovingVariationPct,
        convertMsToDisplay = viewModel::convertMsToDisplay,
        onUpdateRoamingDefaults = viewModel::updateRoamingDefaults,
        onExport = { exportLauncher.launch("${AppConstants.ExportConstants.FILENAME_PREFIX}-${System.currentTimeMillis()}.json") },
        onImport = { importLauncher.launch(arrayOf(AppConstants.ExportConstants.MIME_TYPE)) },
        onImportGpsJoystick = { importGpsJoystickLauncher.launch(arrayOf("*/*")) },
        onImportYamla = { importYamlaLauncher.launch(arrayOf("application/json")) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        onQrShare = { viewModel.prepareQrChunks() },
        onQrScan = { showQrScanner = true },
        onSaveChanges = viewModel::saveChanges,
        onDiscardChanges = viewModel::discardChanges,
        bottomBar = bottomBar,
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    SettingsScreen(
        uiState = SettingsUiState(),
        onOpenDrawer = {},
        onSetWalkSpeed = {},
        onSetRunSpeed = {},
        onSetBikeSpeed = {},
        onSetSpeedUnit = {},
        onSetWidgetFeatures = {},
        onSetRememberLastLocation = {},
        onSetMapFollowsLocation = {},
        onSetJitterIdleRadius = {},
        onSetJitterMovingRadius = {},
        onSetJitterIntervalSeconds = {},
        onSetJitterIdleIntervalSeconds = {},
        onSetRealismBearingHoldIdle = {},
        onSetRealismAltitudeEnabled = {},
        onSetRealismWarmupEnabled = {},
        onSetRealismSatelliteExtrasEnabled = {},
        onSetRealismSuspendedMockingEnabled = {},
        onSetJitterSpeedIdleVariationPct = {},
        onSetJitterSpeedMovingVariationPct = {},
        convertMsToDisplay = { v, _ -> v },
        onExport = {},
        onImport = {},
        onImportGpsJoystick = {},
        onImportYamla = {},
        onQrShare = {},
        onQrScan = {},
    )
}

@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    roamingDefaults: RoamingDefaults = RoamingDefaults(),
    onOpenDrawer: () -> Unit = {},
    onSetWalkSpeed: (Double) -> Unit,
    onSetRunSpeed: (Double) -> Unit,
    onSetBikeSpeed: (Double) -> Unit,
    onSetSpeedUnit: (SpeedUnit) -> Unit,
    onSetWidgetFeatures: (Set<WidgetFeature>) -> Unit,
    onSetRememberLastLocation: (Boolean) -> Unit,
    onSetMapFollowsLocation: (Boolean) -> Unit,
    onSetJitterIdleRadius: (Double) -> Unit,
    onSetJitterMovingRadius: (Double) -> Unit,
    onSetJitterIntervalSeconds: (Int) -> Unit,
    onSetJitterIdleIntervalSeconds: (Int) -> Unit,
    onSetRealismBearingHoldIdle: (Boolean) -> Unit,
    onSetRealismAltitudeEnabled: (Boolean) -> Unit,
    onSetRealismWarmupEnabled: (Boolean) -> Unit,
    onSetRealismSatelliteExtrasEnabled: (Boolean) -> Unit,
    onSetRealismSuspendedMockingEnabled: (Boolean) -> Unit,
    onSetJitterSpeedIdleVariationPct: (Int) -> Unit,
    onSetJitterSpeedMovingVariationPct: (Int) -> Unit,
    convertMsToDisplay: (Double, SpeedUnit) -> Double,
    onUpdateRoamingDefaults: (RoamingDefaults) -> Unit = {},
    onExport: () -> Unit,
    onImport: () -> Unit,
    onImportGpsJoystick: () -> Unit,
    onImportYamla: () -> Unit,
    onQrShare: () -> Unit,
    onQrScan: () -> Unit,
    onSaveChanges: () -> Unit = {},
    onDiscardChanges: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
) {
    LjScaffold(
        title = "Lj",
        onNavigationClick = onOpenDrawer,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        actions = {
            var showDownloadMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showDownloadMenu = true }) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Export")
                }
                DropdownMenu(expanded = showDownloadMenu, onDismissRequest = { showDownloadMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Export via QR code") },
                        onClick = {
                            showDownloadMenu = false
                            onQrShare()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Export settings") },
                        onClick = {
                            showDownloadMenu = false
                            onExport()
                        },
                    )
                }
            }
            var showUploadMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showUploadMenu = true }) {
                    Icon(Icons.Default.Upload, contentDescription = "Import")
                }
                DropdownMenu(expanded = showUploadMenu, onDismissRequest = { showUploadMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Import from QR code") },
                        onClick = {
                            showUploadMenu = false
                            onQrScan()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Import from file") },
                        onClick = {
                            showUploadMenu = false
                            onImport()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Import from GPS Joystick") },
                        onClick = {
                            showUploadMenu = false
                            onImportGpsJoystick()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Import from YAMLA") },
                        onClick = {
                            showUploadMenu = false
                            onImportYamla()
                        },
                    )
                }
            }
            if (uiState.isDirty) {
                TextButton(
                    onClick = onDiscardChanges,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                ) { Text("Discard") }
                TextButton(
                    onClick = onSaveChanges,
                    colors = ButtonDefaults.textButtonColors(contentColor = LjAccent),
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
                        SpeedProfilesSection(uiState, onSetWalkSpeed, onSetRunSpeed, onSetBikeSpeed, onSetSpeedUnit, convertMsToDisplay)
                        Spacer(modifier = Modifier.height(24.dp))
                        GpsJitterSection(
                            uiState,
                            isMph,
                            onSetJitterIdleRadius,
                            onSetJitterMovingRadius,
                            onSetJitterIntervalSeconds,
                            onSetJitterIdleIntervalSeconds,
                            onSetJitterSpeedIdleVariationPct,
                            onSetJitterSpeedMovingVariationPct,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        GpsRealismSection(
                            uiState,
                            onSetRealismBearingHoldIdle,
                            onSetRealismAltitudeEnabled,
                            onSetRealismWarmupEnabled,
                            onSetRealismSatelliteExtrasEnabled,
                            onSetRealismSuspendedMockingEnabled,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        MapSection(uiState, onSetRememberLastLocation, onSetMapFollowsLocation)
                        FloatingWidgetSection(uiState, onSetWidgetFeatures)
                        Spacer(modifier = Modifier.height(24.dp))
                        RoamingSection(roamingDefaults, isMph, onUpdateRoamingDefaults)
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

private fun formatJitterDouble(d: Double): String {
    val rounded = (d * 100).roundToInt() / 100.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

@Composable
private fun JitterInput(
    value: Double,
    onValueChange: (Double) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    var localValue by remember { mutableStateOf(formatJitterDouble(value)) }
    var lastSentValue by remember { mutableStateOf(value) }

    LaunchedEffect(value) {
        if (value != lastSentValue) {
            localValue = formatJitterDouble(value)
            lastSentValue = value
        }
    }

    OutlinedTextField(
        value = localValue,
        onValueChange = { v ->
            localValue = v
            v.toDoubleOrNull()?.let {
                onValueChange(it)
                lastSentValue = it
            }
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
private fun JitterInput(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    var localValue by remember { mutableStateOf(value.toString()) }
    var lastSentValue by remember { mutableStateOf(value) }

    LaunchedEffect(value) {
        if (value != lastSentValue) {
            localValue = value.toString()
            lastSentValue = value
        }
    }

    OutlinedTextField(
        value = localValue,
        onValueChange = { v ->
            localValue = v
            v.toIntOrNull()?.let {
                onValueChange(it)
                lastSentValue = it
            }
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun SpeedProfilesSection(
    uiState: SettingsUiState,
    onSetWalkSpeed: (Double) -> Unit,
    onSetRunSpeed: (Double) -> Unit,
    onSetBikeSpeed: (Double) -> Unit,
    onSetSpeedUnit: (SpeedUnit) -> Unit,
    convertMsToDisplay: (Double, SpeedUnit) -> Double,
) {
    Text("Speed Profiles", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Unit: ", modifier = Modifier.weight(0.3f))
        Row(modifier = Modifier.weight(0.7f)) {
            val units = listOf(SpeedUnit.KMH to "km/h", SpeedUnit.MPH to "mph")
            units.forEachIndexed { index, (unit, label) ->
                val padding = if (index == 0) Modifier.padding(end = 4.dp) else Modifier.padding(start = 4.dp)
                if (uiState.speedUnit == unit) {
                    OutlinedButton(
                        onClick = { onSetSpeedUnit(unit) },
                        modifier = Modifier.weight(0.5f).then(padding),
                    ) { Text(label) }
                } else {
                    FilledTonalButton(
                        onClick = { onSetSpeedUnit(unit) },
                        modifier = Modifier.weight(0.5f).then(padding),
                    ) { Text(label) }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    SpeedProfileInput(
        label = "Walk",
        displaySpeed = convertMsToDisplay(uiState.walkSpeed, uiState.speedUnit),
        onSpeedChange = { onSetWalkSpeed(it) },
        unit = if (uiState.speedUnit == SpeedUnit.KMH) "km/h" else "mph",
    )
    if (uiState.walkSpeed > AppConstants.ProfileConstants.ANTI_CHEAT_WARNING_THRESHOLD_MS) {
        Text(
            text = "Speed exceeds 8 m/s — may trigger anti-cheat in some games",
            color = MaterialTheme.colorScheme.errorContainer,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    SpeedProfileInput(
        label = "Run",
        displaySpeed = convertMsToDisplay(uiState.runSpeed, uiState.speedUnit),
        onSpeedChange = { onSetRunSpeed(it) },
        unit = if (uiState.speedUnit == SpeedUnit.KMH) "km/h" else "mph",
    )
    if (uiState.runSpeed > AppConstants.ProfileConstants.ANTI_CHEAT_WARNING_THRESHOLD_MS) {
        Text(
            text = "Speed exceeds 8 m/s — may trigger anti-cheat in some games",
            color = MaterialTheme.colorScheme.errorContainer,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    SpeedProfileInput(
        label = "Bike",
        displaySpeed = convertMsToDisplay(uiState.bikeSpeed, uiState.speedUnit),
        onSpeedChange = { onSetBikeSpeed(it) },
        unit = if (uiState.speedUnit == SpeedUnit.KMH) "km/h" else "mph",
    )
    if (uiState.bikeSpeed > AppConstants.ProfileConstants.ANTI_CHEAT_WARNING_THRESHOLD_MS) {
        Text(
            text = "Speed exceeds 8 m/s — may trigger anti-cheat in some games",
            color = MaterialTheme.colorScheme.errorContainer,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
        )
    }
}

@Composable
private fun GpsJitterSection(
    uiState: SettingsUiState,
    isMph: Boolean,
    onSetJitterIdleRadius: (Double) -> Unit,
    onSetJitterMovingRadius: (Double) -> Unit,
    onSetJitterIntervalSeconds: (Int) -> Unit,
    onSetJitterIdleIntervalSeconds: (Int) -> Unit,
    onSetJitterSpeedIdleVariationPct: (Int) -> Unit,
    onSetJitterSpeedMovingVariationPct: (Int) -> Unit,
) {
    Text("GPS Jitter", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "Adds noise to each location update. Set 0 to disable.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        JitterInput(
            value = if (isMph) uiState.jitterIdleRadiusMeters * 3.28084 else uiState.jitterIdleRadiusMeters,
            onValueChange = { onSetJitterIdleRadius(if (isMph) it / 3.28084 else it) },
            label = if (isMph) "Idle radius (ft)" else "Idle radius (m)",
            modifier = Modifier.weight(1f),
        )
        JitterInput(
            value = uiState.jitterIdleIntervalSeconds,
            onValueChange = { onSetJitterIdleIntervalSeconds(it) },
            label = "Idle interval (s)",
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        JitterInput(
            value = if (isMph) uiState.jitterMovingRadiusMeters * 3.28084 else uiState.jitterMovingRadiusMeters,
            onValueChange = { onSetJitterMovingRadius(if (isMph) it / 3.28084 else it) },
            label = if (isMph) "Moving radius (ft)" else "Moving radius (m)",
            modifier = Modifier.weight(1f),
        )
        JitterInput(
            value = uiState.jitterIntervalSeconds,
            onValueChange = { onSetJitterIntervalSeconds(it) },
            label = "Moving interval (s)",
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        JitterInput(
            value = uiState.jitterSpeedIdleVariationPct.toDouble(),
            onValueChange = { onSetJitterSpeedIdleVariationPct(it.toInt()) },
            label = "Idle speed variation (%)",
            modifier = Modifier.weight(1f),
        )
        JitterInput(
            value = uiState.jitterSpeedMovingVariationPct.toDouble(),
            onValueChange = { onSetJitterSpeedMovingVariationPct(it.toInt()) },
            label = "Moving speed variation (%)",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun GpsRealismSection(
    uiState: SettingsUiState,
    onSetRealismBearingHoldIdle: (Boolean) -> Unit,
    onSetRealismAltitudeEnabled: (Boolean) -> Unit,
    onSetRealismWarmupEnabled: (Boolean) -> Unit,
    onSetRealismSatelliteExtrasEnabled: (Boolean) -> Unit,
    onSetRealismSuspendedMockingEnabled: (Boolean) -> Unit,
) {
    Text("GPS Realism", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "Controls how the spoofed GPS signal behaves. These options add metadata and variation that real GPS chips produce — some apps and games inspect these signals to detect mock providers.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = uiState.realismBearingHoldIdle,
            onCheckedChange = { onSetRealismBearingHoldIdle(it) },
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text("Hold bearing when stationary")
            Text(
                "Keeps the last known direction when you stop moving instead of snapping to 0° (north). Real GPS chips do the same — a sudden reset to north is a common mock-location tell.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = uiState.realismAltitudeEnabled,
            onCheckedChange = { onSetRealismAltitudeEnabled(it) },
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text("Vary altitude")
            Text(
                "Simulates a plausible altitude with small random drift instead of always reporting 0 m. A flat zero altitude is an obvious signal that the location is synthetic.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = uiState.realismWarmupEnabled,
            onCheckedChange = { onSetRealismWarmupEnabled(it) },
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text("GPS warm-up simulation")
            Text(
                "Starts each session with degraded accuracy (like a cold GPS fix) that converges to normal over ~30 s. Off by default because it temporarily reduces location precision at session start.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = uiState.realismSatelliteExtrasEnabled,
            onCheckedChange = { onSetRealismSatelliteExtrasEnabled(it) },
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text("Realistic satellite count")
            Text(
                "Attaches satellite metadata to each update (7–14 satellites visible, 6–12 in fix) instead of zero. Some apps check for zero satellites as a spoofing signal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = uiState.realismSuspendedMockingEnabled,
            onCheckedChange = { onSetRealismSuspendedMockingEnabled(it) },
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text("Suspended mocking")
            Text(
                "Briefly pauses location updates (~2 s every ~10 s) to mimic real GPS dropouts. Off by default because the pauses cause visible position freezes in many apps. Automatically skipped during route replay.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MapSection(
    uiState: SettingsUiState,
    onSetRememberLastLocation: (Boolean) -> Unit,
    onSetMapFollowsLocation: (Boolean) -> Unit,
) {
    Text("Map", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "Controls how the map behaves while spoofing is active.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = uiState.rememberLastLocation,
            onCheckedChange = { onSetRememberLastLocation(it) },
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text("Remember last location")
            Text(
                "Restores the last spoofed position when the app restarts, so you don't have to re-enter it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = uiState.mapFollowsLocation,
            onCheckedChange = { onSetMapFollowsLocation(it) },
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text("Follow location on map")
            Text(
                "Keeps the map camera centered on the spoofed position as it moves.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WidgetFeatureRow(
    feature: WidgetFeature,
    label: String,
    enabledFeatures: Set<WidgetFeature>,
    onSetWidgetFeatures: (Set<WidgetFeature>) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = feature in enabledFeatures,
            onCheckedChange = { isChecked ->
                val updated = enabledFeatures.toMutableSet()
                if (isChecked) updated.add(feature) else updated.remove(feature)
                onSetWidgetFeatures(updated)
            },
        )
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun FloatingWidgetSection(
    uiState: SettingsUiState,
    onSetWidgetFeatures: (Set<WidgetFeature>) -> Unit,
) {
    Text("Floating Widget", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(8.dp))

    WidgetFeatureRow(WidgetFeature.MAP_FLOATING, "Map shortcut", uiState.enabledWidgetFeatures, onSetWidgetFeatures)
    WidgetFeatureRow(WidgetFeature.JOYSTICK_TOGGLE, "Show/hide joystick", uiState.enabledWidgetFeatures, onSetWidgetFeatures)
    WidgetFeatureRow(WidgetFeature.JOYSTICK_LOCK, "Lock joystick position", uiState.enabledWidgetFeatures, onSetWidgetFeatures)
    WidgetFeatureRow(WidgetFeature.ROUTES_FLOATING, "Routes picker", uiState.enabledWidgetFeatures, onSetWidgetFeatures)
    WidgetFeatureRow(WidgetFeature.FAVORITES_FLOATING, "Favorites picker", uiState.enabledWidgetFeatures, onSetWidgetFeatures)
    WidgetFeatureRow(WidgetFeature.SPEED_CYCLE, "Speed cycle", uiState.enabledWidgetFeatures, onSetWidgetFeatures)
}

@Composable
private fun RoamingSection(
    roamingDefaults: RoamingDefaults,
    isMph: Boolean,
    onUpdateRoamingDefaults: (RoamingDefaults) -> Unit,
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
                onUpdateRoamingDefaults(roamingDefaults.copy(radiusMeters = meters.coerceIn(1_000.0, 100_000.0)))
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
                onUpdateRoamingDefaults(roamingDefaults.copy(distanceMeters = meters.coerceIn(50.0, 50_000.0)))
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
    Row(modifier = Modifier.fillMaxWidth()) {
        listOf("walk" to "Walk", "run" to "Run", "bike" to "Bike").forEach { (id, label) ->
            if (roamingDefaults.speedProfileId == id) {
                OutlinedButton(
                    onClick = { onUpdateRoamingDefaults(roamingDefaults.copy(speedProfileId = id)) },
                    modifier = Modifier.padding(end = 4.dp),
                ) { Text(label) }
            } else {
                FilledTonalButton(
                    onClick = { onUpdateRoamingDefaults(roamingDefaults.copy(speedProfileId = id)) },
                    modifier = Modifier.padding(end = 4.dp),
                ) { Text(label) }
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Checkbox(
            checked = roamingDefaults.followRoads,
            onCheckedChange = { onUpdateRoamingDefaults(roamingDefaults.copy(followRoads = it)) },
        )
        Text("Follow roads", style = MaterialTheme.typography.bodyMedium)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Checkbox(
            checked = roamingDefaults.returnToInitialLocation,
            onCheckedChange = { onUpdateRoamingDefaults(roamingDefaults.copy(returnToInitialLocation = it)) },
        )
        Text("Return to start", style = MaterialTheme.typography.bodyMedium)
    }
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
