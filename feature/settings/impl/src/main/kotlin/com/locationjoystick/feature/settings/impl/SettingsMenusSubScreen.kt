package com.locationjoystick.feature.settings.impl

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.component.AccessibilityDisclosureDialog
import com.locationjoystick.core.designsystem.component.LjButton
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.designsystem.component.LjSelectionMode
import com.locationjoystick.core.designsystem.component.LjSelectionRow
import com.locationjoystick.core.designsystem.component.speedProfileLabel
import com.locationjoystick.core.model.AppFeature
import com.locationjoystick.core.model.FeatureSurface
import com.locationjoystick.core.model.SpeedProfile
import com.locationjoystick.feature.settings.impl.R
import kotlin.math.roundToInt
import com.locationjoystick.core.designsystem.R as DesignR

@Composable
internal fun SettingsMenusSubScreen(
    uiState: SettingsUiState,
    isRooted: Boolean,
    onNavigateBack: () -> Unit,
    isSpoofing: Boolean,
    onToggleSpoofing: () -> Unit,
    locationLabel: String? = null,
    onAction: (SettingsAction) -> Unit,
    onCheckScreenService: () -> Unit = {},
    bottomBar: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) onCheckScreenService()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LjScaffold(
        title = stringResource(R.string.settings_menus_menus),
        isSpoofing = isSpoofing,
        onToggleSpoofing = onToggleSpoofing,
        locationLabel = locationLabel,
        onNavigationClick = onNavigateBack,
        navigationIcon = LjIcons.ArrowBack,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = { SettingsSaveDiscardFab(uiState.isDirty, onAction) },
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
                        AppFeaturesSection(uiState, isRooted, onAction)
                        SettingsSectionDivider()
                        SpeedCycleSection(uiState, onAction)
                        if (showsScreenAccessBlock(Build.VERSION.SDK_INT, tapToWalkOn(uiState), reconnectOn(uiState))) {
                            SettingsSectionDivider()
                            ScreenAccessSection(uiState, onAction)
                        }
                        if (uiState.guidedPath.showsTapToWalk) {
                            SettingsSectionDivider()
                            TapToWalkSection(uiState, onAction)
                        }
                        // Same API 30 floor as the screenshot the check depends on.
                        if (uiState.guidedPath.showsReconnectAccessory && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            SettingsSectionDivider()
                            ReconnectTapSection(uiState, onAction)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TapToWalkSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    val context = LocalContext.current
    var showEnableDisclosure by rememberSaveable { mutableStateOf(false) }
    val enabled = uiState.tapToWalkOverlayEnabled
    val needsAccessibilityDisclosure =
        showsFullAccessibilityDisclosure(Build.VERSION.SDK_INT, uiState.accessibilityDisclosureAccepted)

    SettingsSectionHeader(stringResource(R.string.settings_menus_tap_to_walk))
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(R.string.settings_menus_walk_to_a_location_by_tapping),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    LjSelectionRow(
        mode = LjSelectionMode.Toggle,
        title = stringResource(R.string.settings_menus_enable_tap_to_walk),
        checked = enabled,
        onCheckedChange = { on ->
            val enable = tapToWalkToggleResult(on, uiState.accessibilityDisclosureAccepted)
            if (enable == null) {
                showEnableDisclosure = true
            } else {
                onAction(SettingsAction.SetTapToWalkOverlayEnabled(enable))
            }
        },
    )
    if (enabled) {
        // takeScreenshot(int, Executor, TakeScreenshotCallback) requires API 30 — no fallback exists.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (showsServiceOffHint(enabled, uiState.isScreenServiceGranted)) {
                Spacer(Modifier.height(12.dp))
                ServiceOffHint(uiState, onAction)
            }
        }
    }
    if (showEnableDisclosure) {
        AccessibilityDisclosureDialog(
            title = stringResource(R.string.settings_menus_enable_tap_to_walk_2),
            // Play requires Play-recommended Accept/Decline wording whenever the Accessibility disclosure
            // is on screen; the caveats-only variant (below API 30) keeps "Enable anyway".
            acceptLabel =
                if (needsAccessibilityDisclosure) {
                    stringResource(DesignR.string.accessibility_disclosure_accept)
                } else {
                    stringResource(R.string.settings_menus_enable_anyway)
                },
            caveats =
                listOf(
                    stringResource(R.string.settings_menus_a_screen_overlay_that_intercepts_taps),
                ),
            showAccessibilityDisclosure = needsAccessibilityDisclosure,
            onAccept = {
                showEnableDisclosure = false
                onAction(SettingsAction.SetTapToWalkOverlayEnabled(true))
                if (needsAccessibilityDisclosure) {
                    onAction(SettingsAction.SetAccessibilityDisclosureAccepted(true))
                    if (!uiState.isScreenServiceGranted) openAccessibilitySettings(context)
                }
            },
            onDecline = { showEnableDisclosure = false },
        )
    }
}

@Composable
private fun ReconnectTapSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    val context = LocalContext.current
    var showDisclosure by rememberSaveable { mutableStateOf(false) }

    SettingsSectionHeader(stringResource(R.string.settings_menus_reconnect_accessory))
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(R.string.settings_menus_reconnect_accessory_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    LjSelectionRow(
        mode = LjSelectionMode.Toggle,
        title = stringResource(R.string.settings_menus_reconnect_accessory_automatically),
        checked = uiState.reconnectTapEnabled,
        onCheckedChange = { on ->
            val acceptReconnect = {
                onAction(SettingsAction.AcceptReconnectTapDisclosure)
                if (!uiState.isScreenServiceGranted) openAccessibilitySettings(context)
            }
            when {
                on && showsReconnectDisclosure(uiState.reconnectTapDisclosureAccepted, uiState.accessibilityDisclosureAccepted) ->
                    showDisclosure = true
                on && !uiState.reconnectTapDisclosureAccepted -> acceptReconnect()
                else -> onAction(SettingsAction.SetReconnectTapEnabled(on))
            }
        },
    )
    if (showsServiceOffHint(uiState.reconnectTapEnabled, uiState.isScreenServiceGranted)) {
        Spacer(Modifier.height(12.dp))
        ServiceOffHint(uiState, onAction)
    }
    if (showDisclosure) {
        AccessibilityDisclosureDialog(
            title = stringResource(R.string.settings_menus_reconnect_accessory),
            onAccept = {
                showDisclosure = false
                onAction(SettingsAction.AcceptReconnectTapDisclosure)
                if (!uiState.isScreenServiceGranted) openAccessibilitySettings(context)
            },
            onDecline = { showDisclosure = false },
        )
    }
}

// Per-feature disclosure dialogs are re-entry points for consent that is not yet given. Once the
// relevant accepted flag is set they are skipped. The reconnect gate also accepts accessibility consent,
// because reconnect is only allowed while accessibility consent is on.
internal fun showsTapToWalkEnableDisclosure(accessibilityAccepted: Boolean): Boolean = !accessibilityAccepted

/** Service state is deliberately not an input: enabling the service in Android settings records no consent. */
internal fun showsFullAccessibilityDisclosure(
    sdkInt: Int,
    accessibilityAccepted: Boolean,
): Boolean = sdkInt >= Build.VERSION_CODES.R && !accessibilityAccepted

/** Value to store for the switch, or null when the enable disclosure must show first. */
internal fun tapToWalkToggleResult(
    on: Boolean,
    accessibilityAccepted: Boolean,
): Boolean? =
    when {
        !on -> false
        showsTapToWalkEnableDisclosure(accessibilityAccepted) -> null
        else -> true
    }

internal fun showsReconnectDisclosure(
    reconnectAccepted: Boolean,
    accessibilityAccepted: Boolean,
): Boolean = !reconnectAccepted && !accessibilityAccepted

internal fun showsScreenAccessBlock(
    sdkInt: Int,
    tapToWalkOn: Boolean,
    reconnectOn: Boolean,
): Boolean = sdkInt >= Build.VERSION_CODES.R && (tapToWalkOn || reconnectOn)

internal fun showsServiceOffHint(
    featureOn: Boolean,
    serviceGranted: Boolean,
): Boolean = featureOn && !serviceGranted

// A switch on a section the guided path hides does not count: the user cannot see it.
private fun tapToWalkOn(ui: SettingsUiState) = ui.guidedPath.showsTapToWalk && ui.tapToWalkOverlayEnabled

private fun reconnectOn(ui: SettingsUiState) = ui.guidedPath.showsReconnectAccessory && ui.reconnectTapEnabled

internal fun showsAccessibilityOpenSettingsDisclosure(accessibilityAccepted: Boolean): Boolean = !accessibilityAccepted

private fun openAccessibilitySettings(context: android.content.Context) {
    context.startActivity(
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
    )
}

@Composable
private fun ScreenAccessSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    SettingsSectionHeader(stringResource(R.string.settings_menus_screen_access))
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(R.string.settings_menus_when_enabled_the_app_detects_your),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_menus_accessibility_service), style = MaterialTheme.typography.bodyLarge)
            Text(
                if (uiState.isScreenServiceGranted) {
                    stringResource(R.string.settings_menus_screen_service_enabled)
                } else {
                    stringResource(R.string.settings_menus_screen_service_not_enabled)
                },
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (uiState.isScreenServiceGranted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
        if (!uiState.isScreenServiceGranted) {
            Spacer(Modifier.width(8.dp))
            OpenSettingsButton(uiState, onAction)
        }
    }
}

@Composable
private fun OpenSettingsButton(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    val context = LocalContext.current
    var showDisclosure by rememberSaveable { mutableStateOf(false) }
    LjButton(
        onClick = {
            if (showsAccessibilityOpenSettingsDisclosure(uiState.accessibilityDisclosureAccepted)) {
                showDisclosure = true
            } else {
                openAccessibilitySettings(context)
            }
        },
    ) { Text(stringResource(R.string.settings_menus_open_settings)) }
    if (showDisclosure) {
        AccessibilityDisclosureDialog(
            onAccept = {
                showDisclosure = false
                onAction(SettingsAction.SetAccessibilityDisclosureAccepted(true))
                openAccessibilitySettings(context)
            },
            onDecline = {
                showDisclosure = false
                onAction(SettingsAction.SetAccessibilityDisclosureAccepted(false))
            },
        )
    }
}

@Composable
private fun ServiceOffHint(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            stringResource(R.string.settings_menus_screen_service_off_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        OpenSettingsButton(uiState, onAction)
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

        AppFeature.SPEED_CYCLE -> {
            FeatureMeta(
                stringResource(R.string.settings_menus_feature_speed_cycle),
                stringResource(R.string.settings_menus_feature_speed_cycle_desc),
                LjIcons.Speed,
            )
        }
    }

private val FEATURE_ROW_HEIGHT = 64.dp
private val FEATURE_ROW_SPACING = 8.dp

@Composable
private fun AppFeaturesSection(
    uiState: SettingsUiState,
    isRooted: Boolean,
    onAction: (SettingsAction) -> Unit,
) {
    SettingsSectionHeader(stringResource(R.string.settings_menus_app_features))
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        stringResource(R.string.settings_menus_choose_which_quick_access_features_appea),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))

    Row(modifier = Modifier.fillMaxWidth().padding(start = 48.dp), horizontalArrangement = Arrangement.End) {
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

    val order = uiState.featureOrder
    val rowHeightPx = with(LocalDensity.current) { (FEATURE_ROW_HEIGHT + FEATURE_ROW_SPACING).toPx() }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragDeltaY by remember { mutableStateOf(0f) }

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
    Spacer(modifier = Modifier.height(8.dp))
    LjSelectionRow(
        mode = LjSelectionMode.Multi,
        checked = uiState.showRouteJumpButtons,
        onCheckedChange = { onAction(SettingsAction.SetShowRouteJumpButtons(it)) },
        title = stringResource(R.string.settings_menus_show_route_jump_buttons),
        description = stringResource(R.string.settings_menus_show_route_jump_buttons_desc),
    )
    Spacer(modifier = Modifier.height(8.dp))
    LjSelectionRow(
        mode = LjSelectionMode.Multi,
        checked = uiState.altitudeOverrideButtonEnabled,
        onCheckedChange = { onAction(SettingsAction.SetAltitudeOverrideButtonEnabled(it)) },
        title = stringResource(R.string.settings_menus_show_altitude_override_button),
        description = stringResource(R.string.settings_menus_show_altitude_override_button_desc),
    )
}

private fun Modifier.graphicsLayerTranslationY(ty: Float): Modifier = this.then(Modifier.graphicsLayer { translationY = ty })

@Composable
private fun SpeedCycleSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    SettingsSectionHeader(stringResource(R.string.settings_menus_speed_cycle))
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        stringResource(R.string.settings_menus_choose_which_speed_profiles_the_widget),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))

    Column {
        SpeedProfile.defaultProfiles().forEach { profile ->
            val checked = profile.id in uiState.enabledSpeedProfileIds
            LjSelectionRow(
                mode = LjSelectionMode.Multi,
                checked = checked,
                title = speedProfileLabel(profile.id),
                onCheckedChange = { isChecked ->
                    val updated = uiState.enabledSpeedProfileIds.toMutableSet()
                    if (isChecked) updated.add(profile.id) else updated.remove(profile.id)
                    onAction(SettingsAction.SetEnabledSpeedProfileIds(updated))
                },
            )
        }
    }
}

@Composable
private fun FeatureRow(
    feature: AppFeature,
    isRooted: Boolean,
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    dragModifier: Modifier,
) {
    val meta = featureMeta(feature)
    val rowEnabled = !meta.isRootGated || isRooted
    Row(
        modifier = Modifier.fillMaxWidth().height(FEATURE_ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = LjIcons.DragHandle,
            contentDescription = stringResource(R.string.settings_menus_drag_to_reorder_cd, meta.label),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = dragModifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = meta.icon,
            contentDescription = null,
            tint = if (rowEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
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
                checked = feature in uiState.enabledWidgetFeatures,
                enabled = rowEnabled,
                modifier = Modifier.width(56.dp).semantics { contentDescription = widgetCd },
                onCheckedChange = { checked ->
                    val updated = uiState.enabledWidgetFeatures.toMutableSet()
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
                checked = feature in uiState.enabledMapFeatures,
                modifier = Modifier.width(56.dp).semantics { contentDescription = mapCd },
                onCheckedChange = { checked ->
                    val updated = uiState.enabledMapFeatures.toMutableSet()
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
