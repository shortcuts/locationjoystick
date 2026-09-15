package com.locationjoystick.feature.settings.impl

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.component.LjButton
import com.locationjoystick.core.designsystem.component.LjCheckboxRow
import com.locationjoystick.core.designsystem.component.LjOutlinedButton
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.designsystem.component.LjTextButton
import com.locationjoystick.core.model.AppFeature
import com.locationjoystick.core.model.FeatureSurface
import com.locationjoystick.core.model.SpeedProfile
import com.locationjoystick.core.model.ThemeMode
import com.locationjoystick.feature.settings.impl.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun SettingsMenusSubScreen(
    uiState: SettingsUiState,
    isRooted: Boolean,
    onNavigateBack: () -> Unit,
    isSpoofing: Boolean,
    onToggleSpoofing: () -> Unit,
    locationLabel: String? = null,
    onAction: (SettingsAction) -> Unit,
    onCheckCompassService: () -> Unit = {},
    onTestCompassDetection: suspend () -> Float? = { null },
    launchableApps: List<InstalledApp> = emptyList(),
    bottomBar: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) onCheckCompassService()
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
                                .padding(16.dp),
                    ) {
                        ThemeSection(uiState, onAction)
                        Spacer(Modifier.height(24.dp))
                        AppFeaturesSection(uiState, isRooted, onAction)
                        Spacer(Modifier.height(24.dp))
                        SpeedCycleSection(uiState, onAction)
                        Spacer(Modifier.height(24.dp))
                        TapToWalkSection(uiState, onAction, onTestCompassDetection, launchableApps)
                        Spacer(Modifier.height(24.dp))
                        PrivacySection(uiState, onAction)
                        Spacer(Modifier.height(24.dp))
                        DebugSection(uiState, onAction)
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    Text(stringResource(R.string.settings_menus_appearance), style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(R.string.settings_menus_switch_to_a_light_theme_for),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.settings_menus_light_mode),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = uiState.themeMode == ThemeMode.LIGHT,
            onCheckedChange = { light ->
                onAction(SettingsAction.SetThemeMode(if (light) ThemeMode.LIGHT else ThemeMode.DARK))
            },
        )
    }
}

@Composable
private fun TapToWalkSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onTestCompassDetection: suspend () -> Float? = { null },
    launchableApps: List<InstalledApp> = emptyList(),
) {
    var showWarning by rememberSaveable { mutableStateOf(false) }
    val enabled = uiState.tapToWalkOverlayEnabled

    Text(stringResource(R.string.settings_menus_tap_to_walk), style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(R.string.settings_menus_walk_to_a_location_by_tapping),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.settings_menus_enable_tap_to_walk),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = enabled,
            onCheckedChange = { on ->
                if (on) {
                    showWarning = true
                } else {
                    onAction(SettingsAction.SetTapToWalkOverlayEnabled(false))
                }
            },
        )
    }
    if (enabled) {
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.settings_menus_map_scale_mpx, uiState.tapToWalkScaleMpx),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = uiState.tapToWalkScaleMpx.toFloat(),
            onValueChange = { v ->
                onAction(
                    SettingsAction.SetTapToWalkScaleMpx(
                        v.toDouble().coerceIn(
                            AppConstants.TapToWalkConstants.MIN_SCALE_MPX,
                            AppConstants.TapToWalkConstants.MAX_SCALE_MPX,
                        ),
                    ),
                )
            },
            valueRange = AppConstants.TapToWalkConstants.MIN_SCALE_MPX.toFloat()..AppConstants.TapToWalkConstants.MAX_SCALE_MPX.toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
        // takeScreenshot(int, Executor, TakeScreenshotCallback) requires API 30 — no fallback exists.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Spacer(Modifier.height(16.dp))
            CompassOrientationSection(uiState, onAction, onTestCompassDetection, launchableApps)
        }
    }
    if (showWarning) {
        AlertDialog(
            onDismissRequest = { showWarning = false },
            title = { Text(stringResource(R.string.settings_menus_enable_tap_to_walk_2)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_menus_a_screen_overlay_that_intercepts_taps))
                    Text(stringResource(R.string.settings_menus_accuracy_depends_on_the_scale_setting))
                }
            },
            confirmButton = {
                LjTextButton(onClick = {
                    showWarning = false
                    onAction(SettingsAction.SetTapToWalkOverlayEnabled(true))
                }) { Text(stringResource(R.string.settings_menus_enable_anyway)) }
            },
            dismissButton = {
                LjTextButton(onClick = { showWarning = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun PrivacySection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    Text(stringResource(R.string.settings_menus_privacy), style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(4.dp))
    LjCheckboxRow(
        checked = uiState.hideTeleportFeatures,
        onCheckedChange = { onAction(SettingsAction.SetHideTeleportFeatures(it)) },
        title = stringResource(R.string.settings_menus_hide_teleport_features),
        description = stringResource(R.string.settings_menus_hide_teleport_features_desc),
    )
    Spacer(Modifier.height(8.dp))
    LjCheckboxRow(
        checked = uiState.floatingMapQuickWalk,
        onCheckedChange = { onAction(SettingsAction.SetFloatingMapQuickWalk(it)) },
        title = stringResource(R.string.settings_menus_floating_map_quick_walk),
        description = stringResource(R.string.settings_menus_floating_map_quick_walk_desc),
    )
    Spacer(Modifier.height(8.dp))
    LjCheckboxRow(
        checked = uiState.hideWidgetOverlay,
        onCheckedChange = { onAction(SettingsAction.SetHideWidgetOverlay(it)) },
        title = stringResource(R.string.settings_menus_hide_floating_widget),
        description = stringResource(R.string.settings_menus_hide_floating_widget_desc),
    )
    Spacer(Modifier.height(8.dp))
    LjCheckboxRow(
        checked = uiState.hideForegroundNotification,
        onCheckedChange = { onAction(SettingsAction.SetHideForegroundNotification(it)) },
        title = stringResource(R.string.settings_menus_hide_notification_icon),
        description = stringResource(R.string.settings_menus_hide_notification_icon_desc),
    )
    Spacer(Modifier.height(8.dp))
    LjCheckboxRow(
        checked = uiState.showRouteJumpButtons,
        onCheckedChange = { onAction(SettingsAction.SetShowRouteJumpButtons(it)) },
        title = stringResource(R.string.settings_menus_show_route_jump_buttons),
        description = stringResource(R.string.settings_menus_show_route_jump_buttons_desc),
    )
    Spacer(Modifier.height(8.dp))
    LjCheckboxRow(
        checked = uiState.altitudeOverrideButtonEnabled,
        onCheckedChange = { onAction(SettingsAction.SetAltitudeOverrideButtonEnabled(it)) },
        title = stringResource(R.string.settings_menus_show_altitude_override_button),
        description = stringResource(R.string.settings_menus_show_altitude_override_button_desc),
    )
}

@Composable
private fun DebugSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    Text(stringResource(R.string.settings_menus_debug), style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(4.dp))
    LjCheckboxRow(
        checked = uiState.debugStatsEnabled,
        onCheckedChange = { onAction(SettingsAction.SetDebugStatsEnabled(it)) },
        title = stringResource(R.string.settings_menus_debug_stats),
        description = stringResource(R.string.settings_menus_debug_stats_desc),
    )
}

// Play's Accessibility API policy requires an in-app disclosure with an explicit accept tap
// before the user reaches Android's accessibility settings; dismissing must not count as consent.
@Composable
private fun AccessibilityDisclosureDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text(stringResource(R.string.settings_menus_accessibility_service_use)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.settings_menus_locationjoystick_uses_android_s_accessib),
                )
                Text(
                    stringResource(R.string.settings_menus_what_it_accesses_a_screenshot_of),
                )
                Text(
                    stringResource(R.string.settings_menus_why_to_find_your_game_s),
                )
                Text(
                    stringResource(R.string.settings_menus_the_screenshot_is_processed_on_your),
                )
                Text(stringResource(R.string.settings_menus_you_can_turn_the_service_off))
            }
        },
        confirmButton = { LjTextButton(onClick = onAccept) { Text(stringResource(R.string.settings_menus_agree)) } },
        dismissButton = { LjTextButton(onClick = onDecline) { Text(stringResource(R.string.settings_menus_no_thanks)) } },
    )
}

@Preview(showBackground = true)
@Composable
private fun AccessibilityDisclosureDialogPreview() {
    AccessibilityDisclosureDialog(onAccept = {}, onDecline = {})
}

@Composable
private fun CompassOrientationSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onTestCompassDetection: suspend () -> Float? = { null },
    launchableApps: List<InstalledApp> = emptyList(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasTested by remember { mutableStateOf(false) }
    var testAngle by remember { mutableStateOf<Float?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var appPickerExpanded by remember { mutableStateOf(false) }
    var showDisclosure by rememberSaveable { mutableStateOf(false) }
    val selectedApp = launchableApps.find { it.packageName == uiState.compassTestTargetPackage }

    Text(stringResource(R.string.settings_menus_compass_orientation), style = MaterialTheme.typography.headlineSmall)
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
                if (uiState.isCompassServiceGranted) {
                    stringResource(R.string.settings_menus_compass_service_enabled)
                } else {
                    stringResource(R.string.settings_menus_compass_service_not_enabled)
                },
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (uiState.isCompassServiceGranted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
        if (!uiState.isCompassServiceGranted) {
            Spacer(Modifier.width(8.dp))
            LjButton(onClick = { showDisclosure = true }) { Text(stringResource(R.string.settings_menus_open_settings)) }
        }
    }
    if (showDisclosure) {
        AccessibilityDisclosureDialog(
            onAccept = {
                showDisclosure = false
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            },
            onDecline = { showDisclosure = false },
        )
    }
    if (uiState.isCompassServiceGranted) {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.settings_menus_game_app), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            val chevronRotation by animateFloatAsState(
                targetValue = if (appPickerExpanded) 180f else 0f,
                label = "gameAppChevronRotation",
            )
            LjOutlinedButton(onClick = { appPickerExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    selectedApp?.label ?: stringResource(R.string.settings_menus_select_app),
                    modifier = Modifier.weight(1f),
                    color =
                        if (selectedApp != null) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
                Icon(
                    LjIcons.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
                )
            }
            DropdownMenu(expanded = appPickerExpanded, onDismissRequest = { appPickerExpanded = false }) {
                launchableApps.forEach { app ->
                    DropdownMenuItem(
                        text = { Text(app.label) },
                        onClick = {
                            onAction(SettingsAction.SetCompassTestTargetPackage(app.packageName))
                            appPickerExpanded = false
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (selectedApp != null) {
                stringResource(R.string.settings_menus_tap_test_to_switch_to, selectedApp.label)
            } else {
                stringResource(R.string.settings_menus_select_the_game_above)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LjOutlinedButton(
                onClick = {
                    isTesting = true
                    hasTested = false
                    scope.launch {
                        // Detection reads whatever is CURRENTLY on screen. With a selected app we launch
                        // it directly; otherwise fall back to the old behavior — send ourselves to the
                        // back, revealing whatever the user switched to themselves beforehand.
                        val targetIntent = selectedApp?.let { context.packageManager.getLaunchIntentForPackage(it.packageName) }
                        if (targetIntent != null) {
                            context.startActivity(targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } else {
                            (context as? Activity)?.moveTaskToBack(true)
                        }
                        delay(700)
                        val angle = onTestCompassDetection()
                        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                            it.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(it)
                        }
                        testAngle = angle
                        hasTested = true
                        isTesting = false
                    }
                },
                enabled = !isTesting,
            ) {
                Text(
                    if (isTesting) {
                        stringResource(R.string.settings_menus_testing)
                    } else {
                        stringResource(R.string.settings_menus_test)
                    },
                )
            }
            if (hasTested) {
                Spacer(Modifier.width(12.dp))
                Text(
                    if (testAngle != null) {
                        stringResource(
                            R.string.settings_menus_compass_detected,
                            Math.toDegrees(testAngle!!.toDouble()).roundToInt(),
                        )
                    } else {
                        stringResource(R.string.settings_menus_compass_not_detected)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (testAngle != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                )
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
    Text(stringResource(R.string.settings_menus_app_features), style = MaterialTheme.typography.headlineSmall)
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
}

private fun Modifier.graphicsLayerTranslationY(ty: Float): Modifier = this.then(Modifier.graphicsLayer { translationY = ty })

@Composable
private fun SpeedCycleSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    Text(stringResource(R.string.settings_menus_speed_cycle), style = MaterialTheme.typography.headlineSmall)
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
            LjCheckboxRow(
                checked = checked,
                title = profile.name,
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
