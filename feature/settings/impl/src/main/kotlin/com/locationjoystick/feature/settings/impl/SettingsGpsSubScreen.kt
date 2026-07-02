package com.locationjoystick.feature.settings.impl

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.component.LjCheckboxRow
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.designsystem.component.LjSegmentedControl
import com.locationjoystick.core.model.AppFeature
import com.locationjoystick.core.model.SpeedUnit

@Composable
internal fun SettingsGpsSubScreen(
    uiState: SettingsUiState,
    isRooted: Boolean,
    onNavigateBack: () -> Unit,
    isSpoofing: Boolean,
    onToggleSpoofing: () -> Unit,
    locationLabel: String? = null,
    onAction: (SettingsAction) -> Unit,
    bottomBar: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
) {
    LjScaffold(
        title = "Movement & GPS",
        isSpoofing = isSpoofing,
        onToggleSpoofing = onToggleSpoofing,
        locationLabel = locationLabel,
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
                        Spacer(modifier = Modifier.height(24.dp))
                        ElevationJitterSection(
                            uiState = uiState,
                            elevationControlsEnabled = AppFeature.ELEVATION_CONTROLS in uiState.enabledWidgetFeatures,
                            onAction = onAction,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        GpsRealismSection(uiState, isRooted, onAction)
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Location Memory", style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Controls whether the app remembers where you left off when reopened.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LjCheckboxRow(
                            checked = uiState.rememberLastLocation,
                            onCheckedChange = { onAction(SettingsAction.SetRememberLastLocation(it)) },
                            title = "Remember last location",
                            description = "Restores your last set position when the app restarts.",
                        )
                    }
                }
            }
        }
    }
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
