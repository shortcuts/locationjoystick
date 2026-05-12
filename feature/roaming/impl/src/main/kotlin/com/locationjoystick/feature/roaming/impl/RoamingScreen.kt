package com.locationjoystick.feature.roaming.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locationjoystick.core.ui.component.LjTopBar

@Composable
fun RoamingRoute(
    viewModel: RoamingViewModel,
    onOpenDrawer: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RoamingScreen(
        uiState = uiState,
        onOpenDrawer = onOpenDrawer,
        onUpdateRadius = viewModel::updateRadius,
        onUpdateDuration = viewModel::updateDuration,
        onToggleOsrm = viewModel::toggleOsrmRouting,
        onUpdateTransportMode = viewModel::updateTransportMode,
        onStartRoaming = viewModel::startRoaming,
        onStopRoaming = viewModel::stopRoaming,
    )
}

@Composable
internal fun RoamingScreen(
    uiState: RoamingUiState,
    onOpenDrawer: () -> Unit = {},
    onUpdateRadius: (Double) -> Unit,
    onUpdateDuration: (Int) -> Unit,
    onToggleOsrm: (Boolean) -> Unit,
    onUpdateTransportMode: (String) -> Unit,
    onStartRoaming: () -> Unit,
    onStopRoaming: () -> Unit,
) {
    Scaffold(
        topBar = {
            LjTopBar(title = "Lj", onMenuClick = onOpenDrawer)
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
            ) {
                Text("Radius: ${(uiState.config.radiusMeters / 1000).toInt()} km")
                Slider(
                    value = uiState.config.radiusMeters.toFloat(),
                    onValueChange = { onUpdateRadius(it.toDouble()) },
                    valueRange = 100f..50000f,
                )

                Text("Duration: ${(uiState.config.durationSeconds / 60).toInt()} min")
                Slider(
                    value = uiState.config.durationSeconds.toFloat(),
                    onValueChange = { onUpdateDuration((it / 60).toInt()) },
                    valueRange = 60f..14400f,
                )

                Text("Transport Mode")
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    FilterChip(
                        selected = uiState.transportMode == "walk",
                        onClick = { onUpdateTransportMode("walk") },
                        label = { Text("Walk") },
                    )
                    FilterChip(
                        selected = uiState.transportMode == "run",
                        onClick = { onUpdateTransportMode("run") },
                        label = { Text("Run") },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    FilterChip(
                        selected = uiState.transportMode == "bike",
                        onClick = { onUpdateTransportMode("bike") },
                        label = { Text("Bike") },
                    )
                }

                Switch(
                    checked = uiState.config.useRoadSnapping,
                    onCheckedChange = onToggleOsrm,
                )
                Text("Follow roads")

                if (uiState.isRoaming) {
                    Text("Roaming: ${(uiState.elapsedSeconds / 60).toInt()} / ${(uiState.config.durationSeconds / 60).toInt()} min")
                    Button(onClick = onStopRoaming) {
                        Text("Stop")
                    }
                } else {
                    Button(
                        onClick = onStartRoaming,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text("Start Roaming")
                    }
                }
            }
        }
    }
}
