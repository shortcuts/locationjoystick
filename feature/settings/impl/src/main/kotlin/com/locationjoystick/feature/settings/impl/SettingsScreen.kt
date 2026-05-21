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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
        onSetRealismBearingHoldIdle = viewModel::setRealismBearingHoldIdle,
        onSetRealismAltitudeEnabled = viewModel::setRealismAltitudeEnabled,
        onSetRealismWarmupEnabled = viewModel::setRealismWarmupEnabled,
        onSetRealismSatelliteExtrasEnabled = viewModel::setRealismSatelliteExtrasEnabled,
        onSetRealismSuspendedMockingEnabled = viewModel::setRealismSuspendedMockingEnabled,
        convertMsToDisplay = viewModel::convertMsToDisplay,
        onUpdateRoamingDefaults = viewModel::updateRoamingDefaults,
        onExport = { exportLauncher.launch("${AppConstants.ExportConstants.FILENAME_PREFIX}-${System.currentTimeMillis()}.json") },
        onImport = { importLauncher.launch(arrayOf(AppConstants.ExportConstants.MIME_TYPE)) },
        onImportGpsJoystick = { importGpsJoystickLauncher.launch(arrayOf("*/*")) },
        onImportYamla = { importYamlaLauncher.launch(arrayOf("application/json")) },
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
        onSetRealismBearingHoldIdle = {},
        onSetRealismAltitudeEnabled = {},
        onSetRealismWarmupEnabled = {},
        onSetRealismSatelliteExtrasEnabled = {},
        onSetRealismSuspendedMockingEnabled = {},
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
    onSetRealismBearingHoldIdle: (Boolean) -> Unit,
    onSetRealismAltitudeEnabled: (Boolean) -> Unit,
    onSetRealismWarmupEnabled: (Boolean) -> Unit,
    onSetRealismSatelliteExtrasEnabled: (Boolean) -> Unit,
    onSetRealismSuspendedMockingEnabled: (Boolean) -> Unit,
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
) {
    LjScaffold(
        title = "Lj",
        onNavigationClick = onOpenDrawer,
        bottomBar = bottomBar,
        floatingActionButton = {
            if (uiState.isDirty) {
                FloatingActionButton(
                    onClick = onSaveChanges,
                    containerColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Save changes")
                }
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
                        Text("Speed Profiles", style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Unit: ", modifier = Modifier.weight(0.3f))
                            Row(modifier = Modifier.weight(0.7f)) {
                                val isKmh = uiState.speedUnit == SpeedUnit.KMH

                                Row(
                                    modifier = Modifier.weight(0.7f),
                                ) {
                                    if (isKmh) {
                                        OutlinedButton(
                                            onClick = { onSetSpeedUnit(SpeedUnit.KMH) },
                                            modifier =
                                                Modifier
                                                    .weight(0.5f)
                                                    .padding(end = 4.dp),
                                        ) {
                                            Text("km/h")
                                        }
                                    } else {
                                        FilledTonalButton(
                                            onClick = { onSetSpeedUnit(SpeedUnit.KMH) },
                                            modifier =
                                                Modifier
                                                    .weight(0.5f)
                                                    .padding(end = 4.dp),
                                        ) {
                                            Text("km/h")
                                        }
                                    }

                                    if (!isKmh) {
                                        OutlinedButton(
                                            onClick = { onSetSpeedUnit(SpeedUnit.MPH) },
                                            modifier =
                                                Modifier
                                                    .weight(0.5f)
                                                    .padding(start = 4.dp),
                                        ) {
                                            Text("mph")
                                        }
                                    } else {
                                        FilledTonalButton(
                                            onClick = { onSetSpeedUnit(SpeedUnit.MPH) },
                                            modifier =
                                                Modifier
                                                    .weight(0.5f)
                                                    .padding(start = 4.dp),
                                        ) {
                                            Text("mph")
                                        }
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

                        Spacer(modifier = Modifier.height(24.dp))

                        val isMph = uiState.speedUnit == SpeedUnit.MPH

                        Text("GPS Jitter", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Adds noise to each location update. Set 0 to disable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        JitterInput(
                            value =
                                if (isMph) {
                                    (uiState.jitterIdleRadiusMeters * 3.28084).roundToInt()
                                } else {
                                    uiState.jitterIdleRadiusMeters
                                        .toInt()
                                },
                            onValueChange = {
                                onSetJitterIdleRadius(if (isMph) it / 3.28084 else it.toDouble())
                            },
                            label = if (isMph) "Idle radius (ft)" else "Idle radius (m)",
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        JitterInput(
                            value =
                                if (isMph) {
                                    (uiState.jitterMovingRadiusMeters * 3.28084).roundToInt()
                                } else {
                                    uiState.jitterMovingRadiusMeters
                                        .toInt()
                                },
                            onValueChange = { onSetJitterMovingRadius(if (isMph) it / 3.28084 else it.toDouble()) },
                            label = if (isMph) "Moving radius (ft)" else "Moving radius (m)",
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        JitterInput(
                            value = uiState.jitterIntervalSeconds,
                            onValueChange = { onSetJitterIntervalSeconds(it) },
                            label = "Update interval (s)",
                        )
                        Text(
                            "Recommended idle radius: ${AppConstants.RealismConstants.RECOMMENDED_IDLE_RADIUS_METERS} m for realistic stationary drift",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text("GPS Realism", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Fine-tune how the spoofed location behaves to mimic a real GPS chip.",
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
                                    "Keeps the last bearing instead of resetting to north when you stop.",
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
                                    "Reports a plausible altitude with small drift instead of 0m.",
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
                                    "Starts each session with degraded accuracy that converges over 30s.",
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
                                    "Reports 7–14 satellites in fix instead of zero.",
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
                                    "Briefly pauses location updates (~2s every ~10s) to mimic real GPS dropouts. Skipped during route replay.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text("Map", style = MaterialTheme.typography.headlineSmall)
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
                            Text(
                                "Remember last location",
                                modifier = Modifier.padding(start = 8.dp),
                            )
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
                            Text(
                                "Follow location on map",
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }

                        Text("Floating Widget", style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Map shortcut
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = WidgetFeature.MAP_FLOATING in uiState.enabledWidgetFeatures,
                                onCheckedChange = { isChecked ->
                                    val updated = uiState.enabledWidgetFeatures.toMutableSet()
                                    if (isChecked) {
                                        updated.add(WidgetFeature.MAP_FLOATING)
                                    } else {
                                        updated.remove(WidgetFeature.MAP_FLOATING)
                                    }
                                    onSetWidgetFeatures(updated)
                                },
                            )
                            Text(
                                "Map shortcut",
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }

                        // Joystick toggle
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = WidgetFeature.JOYSTICK_TOGGLE in uiState.enabledWidgetFeatures,
                                onCheckedChange = { isChecked ->
                                    val updated = uiState.enabledWidgetFeatures.toMutableSet()
                                    if (isChecked) {
                                        updated.add(WidgetFeature.JOYSTICK_TOGGLE)
                                    } else {
                                        updated.remove(WidgetFeature.JOYSTICK_TOGGLE)
                                    }
                                    onSetWidgetFeatures(updated)
                                },
                            )
                            Text(
                                "Show/hide joystick",
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }

                        // Joystick lock
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = WidgetFeature.JOYSTICK_LOCK in uiState.enabledWidgetFeatures,
                                onCheckedChange = { isChecked ->
                                    val updated = uiState.enabledWidgetFeatures.toMutableSet()
                                    if (isChecked) {
                                        updated.add(WidgetFeature.JOYSTICK_LOCK)
                                    } else {
                                        updated.remove(WidgetFeature.JOYSTICK_LOCK)
                                    }
                                    onSetWidgetFeatures(updated)
                                },
                            )
                            Text(
                                "Lock joystick position",
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }

                        // Routes picker
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = WidgetFeature.ROUTES_FLOATING in uiState.enabledWidgetFeatures,
                                onCheckedChange = { isChecked ->
                                    val updated = uiState.enabledWidgetFeatures.toMutableSet()
                                    if (isChecked) {
                                        updated.add(WidgetFeature.ROUTES_FLOATING)
                                    } else {
                                        updated.remove(WidgetFeature.ROUTES_FLOATING)
                                    }
                                    onSetWidgetFeatures(updated)
                                },
                            )
                            Text(
                                "Routes picker",
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }

                        // Favorites picker
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = WidgetFeature.FAVORITES_FLOATING in uiState.enabledWidgetFeatures,
                                onCheckedChange = { isChecked ->
                                    val updated = uiState.enabledWidgetFeatures.toMutableSet()
                                    if (isChecked) {
                                        updated.add(WidgetFeature.FAVORITES_FLOATING)
                                    } else {
                                        updated.remove(WidgetFeature.FAVORITES_FLOATING)
                                    }
                                    onSetWidgetFeatures(updated)
                                },
                            )
                            Text(
                                "Favorites picker",
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }

                        // Speed cycle
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = WidgetFeature.SPEED_CYCLE in uiState.enabledWidgetFeatures,
                                onCheckedChange = { isChecked ->
                                    val updated = uiState.enabledWidgetFeatures.toMutableSet()
                                    if (isChecked) {
                                        updated.add(WidgetFeature.SPEED_CYCLE)
                                    } else {
                                        updated.remove(WidgetFeature.SPEED_CYCLE)
                                    }
                                    onSetWidgetFeatures(updated)
                                },
                            )
                            Text(
                                "Speed cycle",
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text("Default Roaming", style = MaterialTheme.typography.headlineSmall)
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

                        Spacer(modifier = Modifier.height(24.dp))

                        Text("Data Management", style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(8.dp))

                        Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                            Text("Export Settings")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        var showImportMenu by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(
                                onClick = onImport,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Import Settings")
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Box {
                                IconButton(onClick = { showImportMenu = true }) {
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "More import options")
                                }
                                DropdownMenu(
                                    expanded = showImportMenu,
                                    onDismissRequest = { showImportMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("From GPS Joystick") },
                                        onClick = {
                                            showImportMenu = false
                                            onImportGpsJoystick()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("From YAMLA") },
                                        onClick = {
                                            showImportMenu = false
                                            onImportYamla()
                                        },
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(onClick = onQrShare, modifier = Modifier.fillMaxWidth()) {
                            Text("Transfer via QR")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(onClick = onQrScan, modifier = Modifier.fillMaxWidth()) {
                            Text("Import from QR")
                        }

                        if (uiState.isDirty) {
                            Spacer(modifier = Modifier.height(24.dp))
                            OutlinedButton(
                                onClick = onDiscardChanges,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Discard", modifier = Modifier.padding(end = 8.dp))
                                Text("Discard Changes")
                            }
                        }
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
private fun JitterInput(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
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
        modifier = Modifier.fillMaxWidth(),
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
            TextButton(onClick = onReplace) { Text("Replace all") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onAdd) { Text("Add to existing") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
