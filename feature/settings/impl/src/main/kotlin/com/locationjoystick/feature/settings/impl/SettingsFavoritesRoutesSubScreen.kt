package com.locationjoystick.feature.settings.impl

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.component.LjCheckboxRow
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.model.SpeedUnit

@Composable
internal fun SettingsFavoritesRoutesSubScreen(
    uiState: SettingsUiState,
    hotLocationTree: HotItemTree,
    hotRouteTree: HotItemTree,
    onNavigateBack: () -> Unit,
    isSpoofing: Boolean,
    onToggleSpoofing: () -> Unit,
    locationLabel: String? = null,
    onAction: (SettingsAction) -> Unit,
    bottomBar: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
) {
    LjScaffold(
        title = "Favorites & Routes",
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
                        FavoritesSection(uiState, hotLocationTree, onAction)
                        Spacer(modifier = Modifier.height(24.dp))
                        RoutesSection(uiState, hotRouteTree, onAction)
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritesSection(
    uiState: SettingsUiState,
    hotLocationTree: HotItemTree,
    onAction: (SettingsAction) -> Unit,
) {
    Text("Favorites", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "Options for the favorites list.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))
    LjCheckboxRow(
        checked = uiState.hotLocationsEnabled,
        onCheckedChange = { onAction(SettingsAction.SetHotLocationsEnabled(it)) },
        title = "Show hot locations",
        description = "Adds a curated list of popular locations to your favorites. Select which ones to include below.",
    )
    if (uiState.hotLocationsEnabled && hotLocationTree.allIds.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        HotItemTreeSection(
            headerLabel = "Locations",
            tree = hotLocationTree,
            selectedIds = uiState.selectedHotLocationIds,
            onSelectionChange = { onAction(SettingsAction.SetSelectedHotLocationIds(it)) },
        )
    }
}

@Composable
private fun RoutesSection(
    uiState: SettingsUiState,
    hotRouteTree: HotItemTree,
    onAction: (SettingsAction) -> Unit,
) {
    Text("Routes", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "Options for the routes list.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))
    LjCheckboxRow(
        checked = uiState.hotRoutesEnabled,
        onCheckedChange = { onAction(SettingsAction.SetHotRoutesEnabled(it)) },
        title = "Show hot routes",
        description = "Adds a curated set of pre-built routes to your routes list. Select which ones to include below.",
    )
    if (uiState.hotRoutesEnabled && hotRouteTree.allIds.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        HotItemTreeSection(
            headerLabel = "Routes",
            tree = hotRouteTree,
            selectedIds = uiState.selectedHotRouteIds,
            onSelectionChange = { onAction(SettingsAction.SetSelectedHotRouteIds(it)) },
        )
    }
}
