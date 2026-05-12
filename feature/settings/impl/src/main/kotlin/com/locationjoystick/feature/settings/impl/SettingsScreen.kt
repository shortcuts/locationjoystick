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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.core.model.WidgetFeature
import com.locationjoystick.core.ui.component.LjTopBar

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onOpenDrawer: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingImportUri = uri
    }

    if (pendingImportUri != null) {
        val uri = pendingImportUri!!
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("Import settings") },
            text = { Text("This will replace all existing data. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.importSettings(context, uri)
                    pendingImportUri = null
                }) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) { Text("Cancel") }
            },
        )
    }

    SettingsScreen(
        uiState = uiState,
        onOpenDrawer = onOpenDrawer,
        onSetWalkSpeed = viewModel::setWalkSpeed,
        onSetRunSpeed = viewModel::setRunSpeed,
        onSetBikeSpeed = viewModel::setBikeSpeed,
        onSetSpeedUnit = viewModel::setSpeedUnit,
        onSetWidgetFeatures = viewModel::setWidgetFeatures,
        convertMsToDisplay = viewModel::convertMsToDisplay,
        onExport = { viewModel.exportSettings(context) },
        onImport = { importLauncher.launch(arrayOf("application/json")) },
        onSaveChanges = viewModel::saveChanges,
        onDiscardChanges = viewModel::discardChanges,
    )
}

@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    onOpenDrawer: () -> Unit = {},
    onSetWalkSpeed: (Double) -> Unit,
    onSetRunSpeed: (Double) -> Unit,
    onSetBikeSpeed: (Double) -> Unit,
    onSetSpeedUnit: (SpeedUnit) -> Unit,
    onSetWidgetFeatures: (Set<WidgetFeature>) -> Unit,
    convertMsToDisplay: (Double, SpeedUnit) -> Double,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onSaveChanges: () -> Unit = {},
    onDiscardChanges: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            LjTopBar(title = "locationjoystick", onMenuClick = onOpenDrawer)
        },
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
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
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
                            if (isKmh) {
                                FilledTonalButton(
                                    onClick = { onSetSpeedUnit(SpeedUnit.KMH) },
                                    modifier = Modifier
                                        .weight(0.5f)
                                        .padding(end = 4.dp),
                                ) {
                                    Text("km/h")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { onSetSpeedUnit(SpeedUnit.KMH) },
                                    modifier = Modifier
                                        .weight(0.5f)
                                        .padding(end = 4.dp),
                                ) {
                                    Text("km/h")
                                }
                            }
                            if (!isKmh) {
                                FilledTonalButton(
                                    onClick = { onSetSpeedUnit(SpeedUnit.MPH) },
                                    modifier = Modifier
                                        .weight(0.5f)
                                        .padding(start = 4.dp),
                                ) {
                                    Text("mph")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { onSetSpeedUnit(SpeedUnit.MPH) },
                                    modifier = Modifier
                                        .weight(0.5f)
                                        .padding(start = 4.dp),
                                ) {
                                    Text("mph")
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
                    if (uiState.walkSpeed > 8.0) {
                        Text(
                            text = "Speed exceeds 8 m/s — may trigger anti-cheat in some games",
                            color = MaterialTheme.colorScheme.errorContainer,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    SpeedProfileInput(
                        label = "Run",
                        displaySpeed = convertMsToDisplay(uiState.runSpeed, uiState.speedUnit),
                        onSpeedChange = { onSetRunSpeed(it) },
                        unit = if (uiState.speedUnit == SpeedUnit.KMH) "km/h" else "mph",
                    )
                    if (uiState.runSpeed > 8.0) {
                        Text(
                            text = "Speed exceeds 8 m/s — may trigger anti-cheat in some games",
                            color = MaterialTheme.colorScheme.errorContainer,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    SpeedProfileInput(
                        label = "Bike",
                        displaySpeed = convertMsToDisplay(uiState.bikeSpeed, uiState.speedUnit),
                        onSpeedChange = { onSetBikeSpeed(it) },
                        unit = if (uiState.speedUnit == SpeedUnit.KMH) "km/h" else "mph",
                    )
                    if (uiState.bikeSpeed > 8.0) {
                        Text(
                            text = "Speed exceeds 8 m/s — may trigger anti-cheat in some games",
                            color = MaterialTheme.colorScheme.errorContainer,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text("Floating Widget", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Joystick toggle
                    Row(
                        modifier = Modifier
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
                        modifier = Modifier
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = WidgetFeature.ROUTES_PICKER in uiState.enabledWidgetFeatures,
                            onCheckedChange = { isChecked ->
                                val updated = uiState.enabledWidgetFeatures.toMutableSet()
                                if (isChecked) {
                                    updated.add(WidgetFeature.ROUTES_PICKER)
                                } else {
                                    updated.remove(WidgetFeature.ROUTES_PICKER)
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = WidgetFeature.FAVORITES_PICKER in uiState.enabledWidgetFeatures,
                            onCheckedChange = { isChecked ->
                                val updated = uiState.enabledWidgetFeatures.toMutableSet()
                                if (isChecked) {
                                    updated.add(WidgetFeature.FAVORITES_PICKER)
                                } else {
                                    updated.remove(WidgetFeature.FAVORITES_PICKER)
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
                        modifier = Modifier
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

                    Text("Data Management", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                        Text("Export Settings")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                        Text("Import Settings")
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
    var localValue by remember(displaySpeed) {
        mutableStateOf(String.format("%.1f", displaySpeed))
    }
    var isDirty by remember(displaySpeed) { mutableStateOf(false) }

    val parsedValue = localValue.toDoubleOrNull()
    val isValid = parsedValue != null && parsedValue in 0.1..15.0

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(0.2f))
        Row(
            modifier = Modifier
                .weight(0.5f)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = localValue,
                onValueChange = { newValue ->
                    localValue = newValue
                    isDirty = true
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(unit, modifier = Modifier.width(40.dp))
        }
        Button(
            onClick = {
                if (parsedValue != null) {
                    val clamped = parsedValue.coerceIn(0.1, 15.0)
                    onSpeedChange(clamped)
                    isDirty = false
                }
            },
            enabled = isDirty && isValid,
            modifier = Modifier.weight(0.3f),
        ) {
            Text("Save")
        }
    }
}
