package com.locationjoystick.feature.settings.impl

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.component.LjCheckboxRow
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.designsystem.component.LjSegmentedControl
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.SpeedUnit

@Composable
internal fun SettingsRoamingSubScreen(
    uiState: SettingsUiState,
    roamingDefaults: RoamingDefaults,
    onNavigateBack: () -> Unit,
    isSpoofing: Boolean,
    onToggleSpoofing: () -> Unit,
    locationLabel: String? = null,
    onAction: (SettingsAction) -> Unit,
    bottomBar: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
) {
    LjScaffold(
        title = "Roaming",
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
                        RoamingSection(roamingDefaults, isMph, onAction)
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
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
        description = "Follows actual roads and paths instead of cutting straight across. May not be available in all areas.",
    )
    LjCheckboxRow(
        checked = roamingDefaults.returnToInitialLocation,
        onCheckedChange = { onAction(SettingsAction.UpdateRoamingDefaults(roamingDefaults.copy(returnToInitialLocation = it))) },
        title = "Return to start",
        description = "Walks back to the starting position after the roaming session completes.",
    )
}
