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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
) {
    Scaffold(
        topBar = {
            LjTopBar(title = "locationjoystick", onMenuClick = onOpenDrawer)
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
                            OutlinedButton(
                                onClick = { onSetSpeedUnit(SpeedUnit.KMH) },
                                modifier = Modifier
                                    .weight(0.5f)
                                    .padding(end = 4.dp),
                            ) {
                                Text("km/h")
                            }
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

                    Spacer(modifier = Modifier.height(16.dp))

                    SpeedProfileInput(
                        label = "Walk",
                        displaySpeed = convertMsToDisplay(uiState.walkSpeed, uiState.speedUnit),
                        onSpeedChange = { onSetWalkSpeed(it) },
                        unit = if (uiState.speedUnit == SpeedUnit.KMH) "km/h" else "mph",
                    )
                    if (uiState.walkSpeed > 8.0) {
                        Text(
                            text = "Having a speed higher than 8 m/s can alter the gameplay or trigger an anti-cheat warning in some games",
                            color = MaterialTheme.colorScheme.error,
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
                            text = "Having a speed higher than 8 m/s can alter the gameplay or trigger an anti-cheat warning in some games",
                            color = MaterialTheme.colorScheme.error,
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
                            text = "Having a speed higher than 8 m/s can alter the gameplay or trigger an anti-cheat warning in some games",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text("Floating Widget", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(8.dp))

                    WidgetFeature.entries.forEach { feature ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = feature in uiState.enabledWidgetFeatures,
                                onCheckedChange = { isChecked ->
                                    val updated = uiState.enabledWidgetFeatures.toMutableSet()
                                    if (isChecked) {
                                        updated.add(feature)
                                    } else {
                                        updated.remove(feature)
                                    }
                                    onSetWidgetFeatures(updated)
                                },
                            )
                            Text(
                                feature.name.replace("_", " ").lowercase()
                                    .replaceFirstChar { it.uppercaseChar() },
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
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
    var textValue by remember(displaySpeed) {
        mutableStateOf(String.format("%.1f", displaySpeed))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(0.2f))
        OutlinedTextField(
            value = textValue,
            onValueChange = { newValue ->
                textValue = newValue
                newValue.toDoubleOrNull()?.let { onSpeedChange(it) }
            },
            modifier = Modifier
                .weight(0.5f)
                .padding(horizontal = 8.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Text(unit, modifier = Modifier.weight(0.3f))
    }
}
