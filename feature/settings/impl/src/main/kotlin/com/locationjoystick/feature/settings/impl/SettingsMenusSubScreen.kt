package com.locationjoystick.feature.settings.impl

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.component.CompassDisclosureDialog
import com.locationjoystick.core.designsystem.component.LjButton
import com.locationjoystick.core.designsystem.component.LjCheckboxRow
import com.locationjoystick.core.designsystem.component.LjOutlinedButton
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.designsystem.component.speedProfileLabel
import com.locationjoystick.core.location.CalibrationState
import com.locationjoystick.core.model.AppFeature
import com.locationjoystick.core.model.FeatureSurface
import com.locationjoystick.core.model.SpeedProfile
import com.locationjoystick.core.model.StoredGroundCalibration
import com.locationjoystick.feature.settings.impl.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    onCheckCompassService: () -> Unit = {},
    onTestCompassDetection: suspend () -> Float? = { null },
    calibrationState: CalibrationState = CalibrationState.Idle,
    groundCalibration: StoredGroundCalibration? = null,
    onStartCalibration: (String) -> Unit = {},
    onCancelCalibration: () -> Unit = {},
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
                                .padding(settingsScrollPadding()),
                    ) {
                        AppFeaturesSection(uiState, isRooted, onAction)
                        SettingsSectionDivider()
                        SpeedCycleSection(uiState, onAction)
                        if (uiState.guidedPath.showsTapToWalk) {
                            SettingsSectionDivider()
                            TapToWalkSection(
                                uiState,
                                onAction,
                                onTestCompassDetection,
                                calibrationState,
                                groundCalibration,
                                onStartCalibration,
                                onCancelCalibration,
                                launchableApps,
                            )
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
    onTestCompassDetection: suspend () -> Float? = { null },
    calibrationState: CalibrationState = CalibrationState.Idle,
    groundCalibration: StoredGroundCalibration? = null,
    onStartCalibration: (String) -> Unit = {},
    onCancelCalibration: () -> Unit = {},
    launchableApps: List<InstalledApp> = emptyList(),
) {
    val context = LocalContext.current
    var showEnableDisclosure by rememberSaveable { mutableStateOf(false) }
    val enabled = uiState.tapToWalkOverlayEnabled
    val needsCompassDisclosure =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !uiState.compassDisclosureAccepted &&
            !uiState.isCompassServiceGranted

    SettingsSectionHeader(stringResource(R.string.settings_menus_tap_to_walk))
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
                    showEnableDisclosure = true
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
            if (uiState.isCompassServiceGranted) {
                Spacer(Modifier.height(16.dp))
                GroundCalibrationSection(uiState, calibrationState, groundCalibration, onStartCalibration, onCancelCalibration)
            }
        }
    }
    if (showEnableDisclosure) {
        CompassDisclosureDialog(
            title = stringResource(R.string.settings_menus_enable_tap_to_walk_2),
            // Play requires Play-recommended Accept/Decline wording whenever the Accessibility disclosure
            // is on screen; the caveats-only variant (below API 30) keeps "Enable anyway".
            acceptLabel =
                if (needsCompassDisclosure) {
                    stringResource(DesignR.string.compass_disclosure_accept)
                } else {
                    stringResource(R.string.settings_menus_enable_anyway)
                },
            caveats =
                listOf(
                    stringResource(R.string.settings_menus_a_screen_overlay_that_intercepts_taps),
                    stringResource(R.string.settings_menus_accuracy_depends_on_the_scale_setting),
                ),
            showAccessibilityDisclosure = needsCompassDisclosure,
            onAccept = {
                showEnableDisclosure = false
                onAction(SettingsAction.SetTapToWalkOverlayEnabled(true))
                if (needsCompassDisclosure) {
                    onAction(SettingsAction.SetCompassDisclosureAccepted(true))
                    openAccessibilitySettings(context)
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.settings_menus_reconnect_accessory_automatically),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = uiState.reconnectTapEnabled,
            onCheckedChange = { on ->
                if (on && !uiState.reconnectTapDisclosureAccepted) {
                    showDisclosure = true
                } else {
                    onAction(SettingsAction.SetReconnectTapEnabled(on))
                }
            },
        )
    }
    if (showDisclosure) {
        CompassDisclosureDialog(
            title = stringResource(R.string.settings_menus_reconnect_accessory),
            onAccept = {
                showDisclosure = false
                onAction(SettingsAction.AcceptReconnectTapDisclosure)
                if (!uiState.isCompassServiceGranted) openAccessibilitySettings(context)
            },
            onDecline = { showDisclosure = false },
        )
    }
}

private fun openAccessibilitySettings(context: android.content.Context) {
    context.startActivity(
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
    )
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

    SettingsSectionHeader(stringResource(R.string.settings_menus_compass_orientation))
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
            LjButton(
                // Play requires the disclosure before every trip to Android's accessibility consent
                // screen, so a prior answer must not short-circuit it.
                onClick = { showDisclosure = true },
            ) { Text(stringResource(R.string.settings_menus_open_settings)) }
        }
    }
    if (showDisclosure) {
        CompassDisclosureDialog(
            onAccept = {
                showDisclosure = false
                onAction(SettingsAction.SetCompassDisclosureAccepted(true))
                openAccessibilitySettings(context)
            },
            onDecline = {
                showDisclosure = false
                onAction(SettingsAction.SetCompassDisclosureAccepted(false))
            },
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
    LjCheckboxRow(
        checked = uiState.showRouteJumpButtons,
        onCheckedChange = { onAction(SettingsAction.SetShowRouteJumpButtons(it)) },
        title = stringResource(R.string.settings_menus_show_route_jump_buttons),
        description = stringResource(R.string.settings_menus_show_route_jump_buttons_desc),
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
            LjCheckboxRow(
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

@Composable
private fun GroundCalibrationSection(
    uiState: SettingsUiState,
    state: CalibrationState,
    stored: StoredGroundCalibration?,
    onStart: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val running = state is CalibrationState.Walking || state is CalibrationState.Analysing
    val hasGame = uiState.compassTestTargetPackage.isNotEmpty()
    // Same stale-screen test as the tap overlay: a record only applies to the size it was fitted on.
    val wrongScreen =
        stored != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds.let {
                !stored.matches(it.width(), it.height())
            }

    SettingsSectionHeader(stringResource(R.string.settings_menus_calibration_header))
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(R.string.settings_menus_calibration_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    when (state) {
        is CalibrationState.Walking -> {
            Text(
                stringResource(R.string.settings_menus_calibration_walking, state.leg, state.legCount),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(progress = { (state.leg - 1f) / state.legCount }, modifier = Modifier.fillMaxWidth())
        }
        is CalibrationState.Analysing -> {
            Text(
                stringResource(R.string.settings_menus_calibration_analysing, state.step, state.stepCount),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(progress = { state.step.toFloat() / state.stepCount }, modifier = Modifier.fillMaxWidth())
        }
        is CalibrationState.Failed -> {
            Text(
                stringResource(calibrationFailureMessageRes(state.reason)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        else -> {
            Text(
                when {
                    stored == null -> stringResource(R.string.settings_menus_calibration_not_calibrated)
                    wrongScreen -> stringResource(R.string.settings_menus_calibration_wrong_screen)
                    else -> calibrationDoneText(context, stored)
                },
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (stored != null && !wrongScreen) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    if (running) {
        LjOutlinedButton(onClick = onCancel) { Text(stringResource(R.string.settings_menus_calibration_cancel)) }
    } else {
        LjButton(onClick = { onStart(uiState.compassTestTargetPackage) }, enabled = hasGame) {
            Text(
                stringResource(
                    if (stored != null) R.string.settings_menus_calibration_recalibrate else R.string.settings_menus_calibration_start,
                ),
            )
        }
        if (!hasGame) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.settings_menus_calibration_need_app),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
