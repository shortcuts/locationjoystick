package com.locationjoystick.feature.settings.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.core.model.WidgetFeature

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState = uiState,
        onSetWalkSpeed = viewModel::setWalkSpeed,
        onSetRunSpeed = viewModel::setRunSpeed,
        onSetBikeSpeed = viewModel::setBikeSpeed,
        onSetSpeedUnit = viewModel::setSpeedUnit,
        onSetWidgetFeatures = viewModel::setWidgetFeatures,
        convertMsToDisplay = viewModel::convertMsToDisplay,
    )
}

@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    onSetWalkSpeed: (Double) -> Unit,
    onSetRunSpeed: (Double) -> Unit,
    onSetBikeSpeed: (Double) -> Unit,
    onSetSpeedUnit: (SpeedUnit) -> Unit,
    onSetWidgetFeatures: (Set<WidgetFeature>) -> Unit,
    convertMsToDisplay: (Double, SpeedUnit) -> Double,
) {
    Box(
        modifier = Modifier.fillMaxSize()
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

                    Spacer(modifier = Modifier.height(8.dp))

                    SpeedProfileInput(
                        label = "Run",
                        displaySpeed = convertMsToDisplay(uiState.runSpeed, uiState.speedUnit),
                        onSpeedChange = { onSetRunSpeed(it) },
                        unit = if (uiState.speedUnit == SpeedUnit.KMH) "km/h" else "mph",
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    SpeedProfileInput(
                        label = "Bike",
                        displaySpeed = convertMsToDisplay(uiState.bikeSpeed, uiState.speedUnit),
                        onSpeedChange = { onSetBikeSpeed(it) },
                        unit = if (uiState.speedUnit == SpeedUnit.KMH) "km/h" else "mph",
                    )

                    val hasHighSpeed = convertMsToDisplay(uiState.walkSpeed, uiState.speedUnit) > 8.0 ||
                        convertMsToDisplay(uiState.runSpeed, uiState.speedUnit) > 8.0 ||
                        convertMsToDisplay(uiState.bikeSpeed, uiState.speedUnit) > 8.0

                    if (hasHighSpeed) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                "High speeds may affect accuracy in some apps",
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
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

                    Button(onClick = { }, modifier = Modifier.fillMaxWidth()) {
                        Text("Export Settings")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(onClick = { }, modifier = Modifier.fillMaxWidth()) {
                        Text("Import Settings")
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
        )
        Text(unit, modifier = Modifier.weight(0.3f))
    }
}
