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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.toLocaleDoubleOrNull
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjSpacing
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.designsystem.component.LjSegmentedControl
import com.locationjoystick.core.designsystem.component.LjSelectionMode
import com.locationjoystick.core.designsystem.component.LjSelectionRow
import com.locationjoystick.core.designsystem.component.speedProfileLabel
import com.locationjoystick.core.model.SpeedProfile
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.feature.settings.impl.R
import java.util.Locale

@Composable
internal fun SettingsGpsSubScreen(
    uiState: SettingsUiState,
    onNavigateBack: () -> Unit,
    isSpoofing: Boolean,
    onToggleSpoofing: () -> Unit,
    locationLabel: String? = null,
    onAction: (SettingsAction) -> Unit,
    bottomBar: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
) {
    LjScaffold(
        title = stringResource(R.string.settings_gps_movement_and_gps),
        isSpoofing = isSpoofing,
        onToggleSpoofing = onToggleSpoofing,
        locationLabel = locationLabel,
        onNavigationClick = onNavigateBack,
        navigationIcon = LjIcons.ArrowBack,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
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
                                .padding(settingsScrollPadding()),
                    ) {
                        SpeedProfilesSection(uiState, onAction)
                        SettingsSectionDivider()
                        GpsJitterSection(uiState, isMph, onAction)
                        SettingsSectionDivider()
                        GpsRealismSection(uiState, isMph, onAction)
                        SettingsSectionDivider()
                        SettingsSectionHeader(stringResource(R.string.settings_gps_location_memory))
                        Spacer(modifier = Modifier.height(LjSpacing.xs))
                        Text(
                            stringResource(R.string.settings_gps_controls_whether_the_app_remembers_where),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(LjSpacing.sm))
                        LjSelectionRow(
                            mode = LjSelectionMode.Multi,
                            checked = uiState.rememberLastLocation,
                            onCheckedChange = { onAction(SettingsAction.SetRememberLastLocation(it)) },
                            title = stringResource(R.string.settings_gps_remember_last_location),
                            description = stringResource(R.string.settings_gps_remember_last_location_desc),
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
    SettingsSectionHeader(stringResource(R.string.settings_gps_speed_profiles))
    Spacer(modifier = Modifier.height(LjSpacing.xs))
    Text(
        stringResource(R.string.settings_gps_movement_speed_used_by_the_joystick),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(LjSpacing.sm))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.settings_gps_unit), modifier = Modifier.weight(0.3f))
        LjSegmentedControl(
            options =
                listOf(
                    SpeedUnit.KMH to stringResource(R.string.settings_gps_unit_kmh),
                    SpeedUnit.MPH to stringResource(R.string.settings_gps_unit_mph),
                ),
            selected = uiState.speedUnit,
            onSelect = { onAction(SettingsAction.SetSpeedUnit(it)) },
            modifier = Modifier.weight(0.7f),
        )
    }

    Spacer(modifier = Modifier.height(LjSpacing.md))

    val profiles = SpeedProfile.defaultProfiles()
    profiles.forEachIndexed { index, profile ->
        val speedMs = uiState.speeds.getValue(profile.id)
        val unit =
            if (uiState.speedUnit == SpeedUnit.KMH) {
                stringResource(R.string.settings_gps_unit_kmh)
            } else {
                stringResource(R.string.settings_gps_unit_mph)
            }
        SpeedProfileInput(
            label = speedProfileLabel(profile.id),
            displaySpeed = convertMsToDisplay(speedMs, uiState.speedUnit),
            onSpeedChange = { onAction(SettingsAction.SetSpeed(profile.id, it)) },
            unit = unit,
        )
        if (speedMs > AppConstants.ProfileConstants.ANTI_CHEAT_WARNING_THRESHOLD_MS) {
            AntiCheatWarning(
                convertMsToDisplay(AppConstants.ProfileConstants.ANTI_CHEAT_WARNING_THRESHOLD_MS, uiState.speedUnit),
                unit,
            )
        }
        if (index != profiles.lastIndex) Spacer(modifier = Modifier.height(LjSpacing.sm))
    }
}

@Composable
private fun SpeedProfileInput(
    label: String,
    displaySpeed: Double,
    onSpeedChange: (Double) -> Unit,
    unit: String,
) {
    var localValue by remember { mutableStateOf(String.format(Locale.US, "%.1f", displaySpeed)) }
    var lastSentValue by remember { mutableDoubleStateOf(displaySpeed) }

    LaunchedEffect(displaySpeed) {
        if (displaySpeed != lastSentValue) {
            localValue = String.format(Locale.US, "%.1f", displaySpeed)
            lastSentValue = displaySpeed
        }
    }

    val parsedValue = localValue.toLocaleDoubleOrNull()
    val isValid = parsedValue != null && parsedValue > 0.0

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(0.2f))

        Row(
            modifier = Modifier.weight(0.8f).padding(horizontal = LjSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = localValue,
                onValueChange = { newValue ->
                    localValue = newValue
                    val parsed = newValue.toLocaleDoubleOrNull()
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
            Spacer(modifier = Modifier.width(LjSpacing.xs))
            Text(unit, modifier = Modifier.widthIn(min = 40.dp))
        }
    }
}

@Composable
private fun AntiCheatWarning(
    threshold: Double,
    unit: String,
) {
    Text(
        text = stringResource(R.string.settings_gps_speed_exceeds_8_m_s_may, threshold, unit),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(start = LjSpacing.xs, top = 2.dp),
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
