package com.locationjoystick.feature.settings.impl

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjSpacing
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.designsystem.component.LjSelectionMode
import com.locationjoystick.core.designsystem.component.LjSelectionRow
import com.locationjoystick.core.designsystem.component.speedProfileLabel
import com.locationjoystick.core.model.AppFeature
import com.locationjoystick.core.model.FeatureSurface
import com.locationjoystick.core.model.SpeedProfile
import com.locationjoystick.feature.settings.impl.R
import kotlin.math.roundToInt

@Composable
internal fun SettingsMenusSubScreen(
    screenState: SettingsScreenState,
    isRooted: Boolean,
    onNavigateBack: () -> Unit,
    isSpoofing: Boolean,
    onToggleSpoofing: () -> Unit,
    locationLabel: String? = null,
    onAction: (SettingsAction) -> Unit,
    bottomBar: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
) {
    SettingsScrollScaffold(
        title = stringResource(R.string.settings_menus_menus),
        screenState = screenState,
        onNavigateBack = onNavigateBack,
        isSpoofing = isSpoofing,
        onToggleSpoofing = onToggleSpoofing,
        locationLabel = locationLabel,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
    ) { uiState ->
        AppFeaturesSection(uiState, isRooted, onAction)
        SettingsSectionDivider()
        SpeedCycleSection(uiState, onAction)
    }
}

@Composable
internal fun SettingsScrollScaffold(
    title: String,
    screenState: SettingsScreenState,
    onNavigateBack: () -> Unit,
    isSpoofing: Boolean,
    onToggleSpoofing: () -> Unit,
    locationLabel: String?,
    bottomBar: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
    content: @Composable ColumnScope.(SettingsUiState) -> Unit,
) {
    LjScaffold(
        title = title,
        isSpoofing = isSpoofing,
        onToggleSpoofing = onToggleSpoofing,
        locationLabel = locationLabel,
        onNavigationClick = onNavigateBack,
        navigationIcon = LjIcons.ArrowBack,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (screenState) {
                SettingsScreenState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is SettingsScreenState.Loaded -> {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(settingsScrollPadding()),
                        content = { content(screenState.state) },
                    )
                }
            }
        }
    }
}

private data class FeatureMeta(
    val label: String,
    val subtitle: String,
    val icon: ImageVector,
    val isRootGated: Boolean = false,
)

@Composable
private fun featureMeta(feature: AppFeature): FeatureMeta =
    when (feature) {
        AppFeature.MAP_FLOATING -> {
            FeatureMeta(
                stringResource(R.string.settings_menus_feature_map_shortcut),
                stringResource(R.string.settings_menus_feature_map_shortcut_desc),
                LjIcons.LocationOn,
            )
        }

        AppFeature.JOYSTICK_TOGGLE -> {
            FeatureMeta(
                stringResource(R.string.settings_menus_feature_show_hide_joystick),
                stringResource(R.string.settings_menus_feature_show_hide_joystick_desc),
                LjIcons.Visibility,
            )
        }

        AppFeature.JOYSTICK_LOCK -> {
            FeatureMeta(
                stringResource(R.string.settings_menus_feature_lock_joystick),
                stringResource(R.string.settings_menus_feature_lock_joystick_desc),
                LjIcons.Lock,
            )
        }

        AppFeature.FAVORITES -> {
            FeatureMeta(
                stringResource(R.string.settings_menus_feature_favorites),
                stringResource(R.string.settings_menus_feature_favorites_desc),
                LjIcons.Favorite,
            )
        }

        AppFeature.ROUTES -> {
            FeatureMeta(
                stringResource(R.string.settings_menus_feature_routes),
                stringResource(R.string.settings_menus_feature_routes_desc),
                LjIcons.Route,
            )
        }

        AppFeature.ROAMING -> {
            FeatureMeta(
                stringResource(R.string.settings_menus_feature_roaming),
                stringResource(R.string.settings_menus_feature_roaming_desc),
                LjIcons.Explore,
            )
        }

        AppFeature.SEARCH -> {
            FeatureMeta(
                stringResource(R.string.settings_menus_feature_search),
                stringResource(R.string.settings_menus_feature_search_desc),
                LjIcons.Search,
            )
        }

        AppFeature.PASTE_COORDINATES -> {
            FeatureMeta(
                stringResource(R.string.menus_paste_coordinates),
                stringResource(R.string.menus_paste_latitude_and_longitude_to_teleport_walk_or_save_as_a_f),
                LjIcons.ContentPaste,
            )
        }

        AppFeature.CAPTURE_COORDINATES -> {
            FeatureMeta(
                stringResource(R.string.menus_capture_coordinates),
                stringResource(R.string.menus_collect_map_links_from_other_apps_into_a_list_then_save_them),
                LjIcons.AddLocationAlt,
            )
        }

        AppFeature.DRAW_LINE -> {
            FeatureMeta(
                stringResource(R.string.settings_menus_feature_draw_line),
                stringResource(R.string.settings_menus_feature_draw_line_desc),
                LjIcons.Gesture,
            )
        }

        AppFeature.SPEED_CYCLE -> {
            FeatureMeta(
                stringResource(R.string.settings_menus_feature_speed_cycle),
                stringResource(R.string.settings_menus_feature_speed_cycle_desc),
                LjIcons.Speed,
            )
        }
    }

private val FEATURE_ROW_HEIGHT = 64.dp
private val FEATURE_ROW_SPACING = LjSpacing.sm
private val FEATURE_TOGGLE_INDENT = 48.dp

@Composable
private fun AppFeaturesSection(
    uiState: SettingsUiState,
    isRooted: Boolean,
    onAction: (SettingsAction) -> Unit,
) {
    SettingsSectionHeader(stringResource(R.string.settings_menus_app_features))
    Spacer(modifier = Modifier.height(LjSpacing.xs))
    Text(
        stringResource(R.string.settings_menus_choose_which_quick_access_features_appea),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(LjSpacing.sm))

    Row(modifier = Modifier.fillMaxWidth().padding(start = FEATURE_TOGGLE_INDENT), horizontalArrangement = Arrangement.End) {
        Text(
            stringResource(R.string.settings_menus_widget),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(56.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.settings_menus_map),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(56.dp),
            textAlign = TextAlign.Center,
        )
    }

    val order = uiState.settings.featureOrder
    val rowHeightPx = with(LocalDensity.current) { (FEATURE_ROW_HEIGHT + FEATURE_ROW_SPACING).toPx() }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragDeltaY by remember { mutableFloatStateOf(0f) }

    Column(verticalArrangement = Arrangement.spacedBy(FEATURE_ROW_SPACING)) {
        order.forEachIndexed { index, feature ->
            val isDragging = draggingIndex == index
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging) 1f else 0f)
                        .let { mod ->
                            if (isDragging) {
                                mod.graphicsLayerTranslationY(dragDeltaY)
                            } else {
                                mod
                            }
                        },
            ) {
                FeatureRow(
                    feature = feature,
                    isRooted = isRooted,
                    uiState = uiState,
                    onAction = onAction,
                    dragModifier =
                        Modifier.pointerInput(feature) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingIndex = index
                                    dragDeltaY = 0f
                                },
                                onDragEnd = {
                                    val targetIndex =
                                        (index + (dragDeltaY / rowHeightPx).roundToInt()).coerceIn(0, order.lastIndex)
                                    if (targetIndex != index) {
                                        val newOrder = order.toMutableList()
                                        val moved = newOrder.removeAt(index)
                                        newOrder.add(targetIndex, moved)
                                        onAction(SettingsAction.SetFeatureOrder(newOrder))
                                    }
                                    draggingIndex = null
                                    dragDeltaY = 0f
                                },
                                onDragCancel = {
                                    draggingIndex = null
                                    dragDeltaY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragDeltaY += dragAmount.y
                                },
                            )
                        },
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(LjSpacing.sm))
    LjSelectionRow(
        mode = LjSelectionMode.Multi,
        checked = uiState.settings.showRouteJumpButtons,
        onCheckedChange = { onAction(SettingsAction.SetShowRouteJumpButtons(it)) },
        title = stringResource(R.string.settings_menus_show_route_jump_buttons),
        description = stringResource(R.string.settings_menus_show_route_jump_buttons_desc),
    )
    Spacer(modifier = Modifier.height(LjSpacing.sm))
    LjSelectionRow(
        mode = LjSelectionMode.Multi,
        checked = uiState.settings.altitudeOverrideButtonEnabled,
        onCheckedChange = { onAction(SettingsAction.SetAltitudeOverrideButtonEnabled(it)) },
        title = stringResource(R.string.settings_menus_show_altitude_override_button),
        description = stringResource(R.string.settings_menus_show_altitude_override_button_desc),
    )
    Spacer(modifier = Modifier.height(LjSpacing.sm))
    LjSelectionRow(
        mode = LjSelectionMode.Multi,
        checked = uiState.settings.pauseResumeButtonEnabled,
        onCheckedChange = { onAction(SettingsAction.SetPauseResumeButtonEnabled(it)) },
        title = stringResource(R.string.settings_menus_show_pause_resume_button),
        description = stringResource(R.string.settings_menus_show_pause_resume_button_desc),
    )
    Spacer(modifier = Modifier.height(LjSpacing.sm))
    LjSelectionRow(
        mode = LjSelectionMode.Multi,
        checked = uiState.settings.floatingMapShareButtonEnabled,
        onCheckedChange = { onAction(SettingsAction.SetFloatingMapShareButtonEnabled(it)) },
        title = stringResource(R.string.settings_menus_show_floating_map_share_button),
        description = stringResource(R.string.settings_menus_show_floating_map_share_button_desc),
    )
    Spacer(modifier = Modifier.height(LjSpacing.sm))
    LjSelectionRow(
        mode = LjSelectionMode.Multi,
        checked = uiState.settings.floatingMapNorthLockButtonEnabled,
        onCheckedChange = { onAction(SettingsAction.SetFloatingMapNorthLockButtonEnabled(it)) },
        title = stringResource(R.string.settings_menus_show_floating_map_north_lock_button),
        description = stringResource(R.string.settings_menus_show_floating_map_north_lock_button_desc),
    )
}

private fun Modifier.graphicsLayerTranslationY(ty: Float): Modifier = this.then(Modifier.graphicsLayer { translationY = ty })

@Composable
private fun SpeedCycleSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    SettingsSectionHeader(stringResource(R.string.settings_menus_speed_cycle))
    Spacer(modifier = Modifier.height(LjSpacing.xs))
    Text(
        stringResource(R.string.settings_menus_choose_which_speed_profiles_the_widget),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(LjSpacing.sm))

    Column {
        SpeedProfile.defaultProfiles().forEach { profile ->
            val checked = profile.id in uiState.settings.enabledSpeedProfileIds
            LjSelectionRow(
                mode = LjSelectionMode.Multi,
                checked = checked,
                title = speedProfileLabel(profile.id),
                onCheckedChange = { isChecked ->
                    val updated = uiState.settings.enabledSpeedProfileIds.toMutableSet()
                    if (isChecked) updated.add(profile.id) else updated.remove(profile.id)
                    onAction(SettingsAction.SetEnabledSpeedProfileIds(updated))
                },
            )
        }
    }
}

// dragModifier targets the drag icon, not the root, so it cannot be named modifier.
@Suppress("ModifierParameter")
@Composable
private fun FeatureRow(
    feature: AppFeature,
    isRooted: Boolean,
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    dragModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val meta = featureMeta(feature)
    val rowEnabled = !meta.isRootGated || isRooted
    Row(
        modifier = modifier.fillMaxWidth().height(FEATURE_ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = LjIcons.DragHandle,
            contentDescription = stringResource(R.string.settings_menus_drag_to_reorder_cd, meta.label),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = dragModifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(LjSpacing.sm))
        Icon(
            imageVector = meta.icon,
            contentDescription = null,
            tint = if (rowEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(start = LjSpacing.smMd, end = LjSpacing.sm)) {
            Text(
                text = meta.label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (rowEnabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            Text(
                text = meta.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (meta.isRootGated) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (FeatureSurface.WIDGET in feature.surfaces) {
            val widgetCd = stringResource(R.string.settings_menus_feature_on_widget_cd, meta.label)
            Checkbox(
                checked = feature in uiState.settings.enabledWidgetFeatures,
                enabled = rowEnabled,
                modifier = Modifier.width(56.dp).semantics { contentDescription = widgetCd },
                onCheckedChange = { checked ->
                    val updated = uiState.settings.enabledWidgetFeatures.toMutableSet()
                    if (checked) {
                        updated.add(feature)
                    } else {
                        updated.remove(feature)
                    }
                    onAction(SettingsAction.SetWidgetFeatures(updated))
                },
            )
        } else {
            Checkbox(
                checked = false,
                enabled = false,
                modifier = Modifier.width(56.dp),
                onCheckedChange = {},
            )
        }
        if (FeatureSurface.MAP in feature.surfaces) {
            val mapCd = stringResource(R.string.settings_menus_feature_on_map_cd, meta.label)
            Checkbox(
                checked = feature in uiState.settings.enabledMapFeatures,
                modifier = Modifier.width(56.dp).semantics { contentDescription = mapCd },
                onCheckedChange = { checked ->
                    val updated = uiState.settings.enabledMapFeatures.toMutableSet()
                    if (checked) updated.add(feature) else updated.remove(feature)
                    onAction(SettingsAction.SetMapFeatures(updated))
                },
            )
        } else {
            Checkbox(
                checked = false,
                enabled = false,
                modifier = Modifier.width(56.dp),
                onCheckedChange = {},
            )
        }
    }
}
