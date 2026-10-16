package com.locationjoystick.feature.settings.impl

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjSpacing
import com.locationjoystick.core.designsystem.component.LjLanguageDropdown
import com.locationjoystick.core.designsystem.component.LjOutlinedButton
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.designsystem.component.LjSelectionMode
import com.locationjoystick.core.designsystem.component.LjSelectionRow
import com.locationjoystick.core.designsystem.component.LjSelectionStyle
import com.locationjoystick.core.model.AppLanguage
import com.locationjoystick.core.model.GuidedPath
import com.locationjoystick.core.model.MapTileSource
import com.locationjoystick.core.model.ThemeMode
import com.locationjoystick.feature.settings.impl.R

@Composable
internal fun SettingsGeneralSubScreen(
    uiState: SettingsUiState,
    languageTag: String? = null,
    onNavigateBack: () -> Unit,
    isSpoofing: Boolean,
    onToggleSpoofing: () -> Unit,
    locationLabel: String? = null,
    onAction: (SettingsAction) -> Unit,
    launchableApps: List<InstalledApp> = emptyList(),
    scrollToCapture: Boolean = false,
    bottomBar: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
) {
    val captureBringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(scrollToCapture, uiState.isLoading) {
        if (scrollToCapture && !uiState.isLoading) captureBringIntoView.bringIntoView()
    }

    LjScaffold(
        title = stringResource(R.string.settings_general_general),
        isSpoofing = isSpoofing,
        onToggleSpoofing = onToggleSpoofing,
        locationLabel = locationLabel,
        onNavigationClick = onNavigateBack,
        navigationIcon = LjIcons.ArrowBack,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = { SettingsSaveDiscardFab(uiState.isDirty, onAction) },
        actions = {
            LjLanguageDropdown(
                selected = AppLanguage.fromTag(languageTag),
                onSelect = { language -> onAction(SettingsAction.SetLanguage(language.languageTag)) },
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                else -> {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(remember { ScrollState(0) })
                                .padding(settingsScrollPadding()),
                    ) {
                        GuidedPathSection(uiState, onAction)
                        SettingsSectionDivider()
                        ThemeSection(uiState, onAction)
                        SettingsSectionDivider()
                        MapSourceSection(uiState, onAction)
                        SettingsSectionDivider()
                        GeocodingSection(uiState, onAction)
                        SettingsSectionDivider()
                        PrivacySection(uiState, onAction)
                        SettingsSectionDivider()
                        CaptureSection(uiState, onAction, launchableApps, Modifier.bringIntoViewRequester(captureBringIntoView))
                        SettingsSectionDivider()
                        DebugSection(uiState, onAction)
                    }
                }
            }
        }
    }
}

@Composable
private fun GuidedPathSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    SettingsSectionHeader(stringResource(R.string.settings_menus_guided_path))
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(R.string.settings_menus_guided_path_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GuidedPath.entries.forEach { path ->
            LjSelectionRow(
                mode = LjSelectionMode.Single,
                style = LjSelectionStyle.Card,
                title = stringResource(path.titleRes()),
                description = stringResource(path.descriptionRes()),
                checked = path == uiState.guidedPath,
                onCheckedChange = { onAction(SettingsAction.SetGuidedPath(path)) },
            )
        }
    }
}

private fun GuidedPath.titleRes(): Int =
    when (this) {
        GuidedPath.DEFAULT -> R.string.settings_menus_guided_path_default
        GuidedPath.POKEMON_GO -> R.string.settings_menus_guided_path_pokemon_go
        GuidedPath.PIKMIN_BLOOM -> R.string.settings_menus_guided_path_pikmin_bloom
    }

private fun GuidedPath.descriptionRes(): Int =
    when (this) {
        GuidedPath.DEFAULT -> R.string.settings_menus_guided_path_default_desc
        GuidedPath.POKEMON_GO -> R.string.settings_menus_guided_path_pokemon_go_desc
        GuidedPath.PIKMIN_BLOOM -> R.string.settings_menus_guided_path_pikmin_bloom_desc
    }

@Composable
private fun MapSourceSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    SettingsSectionHeader(stringResource(R.string.settings_menus_map_section))
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(R.string.settings_menus_map_source_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.settings_menus_map_source), style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(4.dp))
    var sourcePickerExpanded by remember { mutableStateOf(false) }
    var sourcePickerWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    Box(modifier = Modifier.fillMaxWidth()) {
        val chevronRotation by animateFloatAsState(
            targetValue = if (sourcePickerExpanded) 180f else 0f,
            label = "mapSourceChevronRotation",
        )
        LjOutlinedButton(
            onClick = { sourcePickerExpanded = true },
            modifier = Modifier.fillMaxWidth().onSizeChanged { sourcePickerWidth = it.width },
        ) {
            Text(
                mapTileSourceLabel(uiState.mapTileSource),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                LjIcons.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
            )
        }
        // Same width as the button and the same horizontal padding as LjOutlinedButton's content,
        // so item labels line up with the selected label and the check sits under the chevron.
        DropdownMenu(
            expanded = sourcePickerExpanded,
            onDismissRequest = { sourcePickerExpanded = false },
            modifier = Modifier.width(with(density) { sourcePickerWidth.toDp() }),
        ) {
            MapTileSource.entries.forEach { source ->
                val isSelected = source == uiState.mapTileSource
                DropdownMenuItem(
                    text = { Text(mapTileSourceLabel(source)) },
                    trailingIcon = {
                        if (isSelected) {
                            Icon(
                                LjIcons.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    contentPadding = PaddingValues(horizontal = LjSpacing.lg),
                    onClick = {
                        onAction(SettingsAction.SetMapTileSource(source))
                        sourcePickerExpanded = false
                    },
                )
            }
        }
    }
    if (uiState.mapTileSource == MapTileSource.AMAP) {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.settings_menus_map_source_amap_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Provider — default start city", e.g. "OpenStreetMap — Paris, France". */
@Composable
private fun mapTileSourceLabel(source: MapTileSource): String =
    when (source) {
        MapTileSource.OSM ->
            stringResource(
                R.string.settings_menus_map_source_item,
                stringResource(R.string.settings_menus_map_source_osm),
                stringResource(R.string.settings_menus_map_source_osm_default_city),
            )
        MapTileSource.AMAP ->
            stringResource(
                R.string.settings_menus_map_source_item,
                stringResource(R.string.settings_menus_map_source_amap),
                stringResource(R.string.settings_menus_map_source_amap_default_city),
            )
    }

@Composable
private fun ThemeSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    SettingsSectionHeader(stringResource(R.string.settings_menus_appearance))
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(R.string.settings_menus_switch_to_a_light_theme_for),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    LjSelectionRow(
        mode = LjSelectionMode.Toggle,
        title = stringResource(R.string.settings_menus_light_mode),
        checked = uiState.themeMode == ThemeMode.LIGHT,
        onCheckedChange = { light ->
            onAction(SettingsAction.SetThemeMode(if (light) ThemeMode.LIGHT else ThemeMode.DARK))
        },
    )
}

@Composable
private fun PrivacySection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    SettingsSectionHeader(stringResource(R.string.settings_menus_privacy))
    Spacer(Modifier.height(4.dp))
    LjSelectionRow(
        mode = LjSelectionMode.Multi,
        checked = uiState.hideTeleportFeatures,
        onCheckedChange = { onAction(SettingsAction.SetHideTeleportFeatures(it)) },
        title = stringResource(R.string.settings_menus_hide_teleport_features),
        description = stringResource(R.string.settings_menus_hide_teleport_features_desc),
    )
    Spacer(Modifier.height(8.dp))
    LjSelectionRow(
        mode = LjSelectionMode.Multi,
        checked = uiState.floatingMapQuickWalk,
        onCheckedChange = { onAction(SettingsAction.SetFloatingMapQuickWalk(it)) },
        title = stringResource(R.string.settings_menus_floating_map_quick_walk),
        description = stringResource(R.string.settings_menus_floating_map_quick_walk_desc),
    )
    Spacer(Modifier.height(8.dp))
    LjSelectionRow(
        mode = LjSelectionMode.Multi,
        checked = uiState.hideWidgetOverlay,
        onCheckedChange = { onAction(SettingsAction.SetHideWidgetOverlay(it)) },
        title = stringResource(R.string.settings_menus_hide_floating_widget),
        description = stringResource(R.string.settings_menus_hide_floating_widget_desc),
    )
    Spacer(Modifier.height(8.dp))
    LjSelectionRow(
        mode = LjSelectionMode.Multi,
        checked = uiState.hideForegroundNotification,
        onCheckedChange = { onAction(SettingsAction.SetHideForegroundNotification(it)) },
        title = stringResource(R.string.settings_menus_hide_notification_icon),
        description = stringResource(R.string.settings_menus_hide_notification_icon_desc),
    )
}

@Composable
private fun DebugSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    SettingsSectionHeader(stringResource(R.string.settings_menus_debug))
    Spacer(Modifier.height(4.dp))
    LjSelectionRow(
        mode = LjSelectionMode.Multi,
        checked = uiState.debugStatsEnabled,
        onCheckedChange = { onAction(SettingsAction.SetDebugStatsEnabled(it)) },
        title = stringResource(R.string.settings_menus_debug_stats),
        description = stringResource(R.string.settings_menus_debug_stats_desc),
    )
}
