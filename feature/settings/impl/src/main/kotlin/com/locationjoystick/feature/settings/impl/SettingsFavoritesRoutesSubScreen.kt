package com.locationjoystick.feature.settings.impl

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.res.stringResource
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjSpacing
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.designsystem.component.LjSelectionMode
import com.locationjoystick.core.designsystem.component.LjSelectionRow
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.feature.settings.impl.R

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
        title = stringResource(R.string.settings_favorites_routes_title),
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
                        FavoritesSection(uiState, hotLocationTree, onAction)
                        SettingsSectionDivider()
                        RoutesSection(uiState, hotRouteTree, onAction)
                        SettingsSectionDivider()
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
    SettingsSectionHeader(stringResource(R.string.settings_favorites_routes_favorites))
    Spacer(modifier = Modifier.height(LjSpacing.xs))
    Text(
        stringResource(R.string.settings_favorites_routes_options_for_the_favorites_list),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(LjSpacing.sm))
    LjSelectionRow(
        mode = LjSelectionMode.Multi,
        checked = uiState.lastKnownPositionEnabled,
        onCheckedChange = { onAction(SettingsAction.SetLastKnownPositionEnabled(it)) },
        title = stringResource(R.string.settings_favorites_routes_show_last_known_position),
        description = stringResource(R.string.settings_favorites_routes_show_last_known_position_desc),
    )
    Spacer(modifier = Modifier.height(LjSpacing.sm))
    LjSelectionRow(
        mode = LjSelectionMode.Multi,
        checked = uiState.hotLocationsEnabled,
        onCheckedChange = { onAction(SettingsAction.SetHotLocationsEnabled(it)) },
        title = stringResource(R.string.settings_favorites_routes_show_hot_locations),
        description = stringResource(R.string.settings_favorites_routes_show_hot_locations_desc),
    )
    if (uiState.hotLocationsEnabled && hotLocationTree.allIds.isNotEmpty()) {
        Spacer(modifier = Modifier.height(LjSpacing.sm))
        HotItemTreeSection(
            headerLabel = stringResource(R.string.settings_favorites_routes_locations),
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
    SettingsSectionHeader(stringResource(R.string.settings_favorites_routes_routes))
    Spacer(modifier = Modifier.height(LjSpacing.xs))
    Text(
        stringResource(R.string.settings_favorites_routes_options_for_the_routes_list),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(LjSpacing.sm))
    LjSelectionRow(
        mode = LjSelectionMode.Multi,
        checked = uiState.hotRoutesEnabled,
        onCheckedChange = { onAction(SettingsAction.SetHotRoutesEnabled(it)) },
        title = stringResource(R.string.settings_favorites_routes_show_hot_routes),
        description = stringResource(R.string.settings_favorites_routes_show_hot_routes_desc),
    )
    if (uiState.hotRoutesEnabled && hotRouteTree.allIds.isNotEmpty()) {
        Spacer(modifier = Modifier.height(LjSpacing.sm))
        HotItemTreeSection(
            headerLabel = stringResource(R.string.settings_favorites_routes_routes),
            tree = hotRouteTree,
            selectedIds = uiState.selectedHotRouteIds,
            onSelectionChange = { onAction(SettingsAction.SetSelectedHotRouteIds(it)) },
        )
    }
}
