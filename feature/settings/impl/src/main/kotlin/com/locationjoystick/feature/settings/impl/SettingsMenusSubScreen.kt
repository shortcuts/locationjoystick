package com.locationjoystick.feature.settings.impl

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.isOverlayPermissionGranted
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
        title = "Menus",
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
                        TapToWalkSection(uiState, onAction, onTestCompassDetection)
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
    Text("Appearance", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(4.dp))
    Text(
        "Switch to a light theme for better readability in bright/sunny conditions.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Light mode",
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
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val savedStateRegistryOwner = LocalSavedStateRegistryOwner.current
    var showWarning by rememberSaveable { mutableStateOf(false) }
    val enabled = uiState.floatingMapQuickWalk || uiState.tapToWalkOverlayEnabled

    Text("Tap to Walk", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(4.dp))
    Text(
        "Walk to a location by tapping it — no confirmation needed.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Enable Tap to Walk",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = enabled,
            onCheckedChange = { on ->
                if (on) {
                    showWarning = true
                } else {
                    onAction(SettingsAction.SetFloatingMapQuickWalk(false))
                    onAction(SettingsAction.SetTapToWalkOverlayEnabled(false))
                }
            },
        )
    }
    if (enabled) {
        Spacer(Modifier.height(12.dp))
        Text(
            "Map scale (%.2f m/px) — zoom the game fully out for best accuracy".format(uiState.tapToWalkScaleMpx),
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
        Spacer(Modifier.height(4.dp))
        LjOutlinedButton(
            onClick = {
                startCalibrationOverlay(context, lifecycleOwner, savedStateRegistryOwner) { dismiss ->
                    ScaleCalibrationOverlayContent(
                        onApply = { scaleMpx -> onAction(SettingsAction.SetTapToWalkScaleMpx(scaleMpx)) },
                        dismiss = dismiss,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(LjIcons.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Measure scale from screen")
        }
        // takeScreenshot(int, Executor, TakeScreenshotCallback) requires API 30 — no fallback exists.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Spacer(Modifier.height(16.dp))
            CompassOrientationSection(uiState, onAction, onTestCompassDetection)
        }
    }
    if (showWarning) {
        AlertDialog(
            onDismissRequest = { showWarning = false },
            title = { Text("Enable Tap to Walk?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("A screen overlay that intercepts taps may increase detection chance in some games. Use at your own risk.")
                    Text("Accuracy depends on the scale setting — zoom out in the game for better results.")
                }
            },
            confirmButton = {
                LjTextButton(onClick = {
                    showWarning = false
                    onAction(SettingsAction.SetFloatingMapQuickWalk(true))
                    onAction(SettingsAction.SetTapToWalkOverlayEnabled(true))
                }) { Text("Enable anyway") }
            },
            dismissButton = {
                LjTextButton(onClick = { showWarning = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PrivacySection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    Text("Privacy", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(4.dp))
    LjCheckboxRow(
        checked = uiState.hideTeleportFeatures,
        onCheckedChange = { onAction(SettingsAction.SetHideTeleportFeatures(it)) },
        title = "Hide teleport features",
        description =
            "Removes every teleport option across the app — map, favorites, " +
                "routes, group sync, and the widget. Only walking and route replay remain available.",
    )
    Spacer(Modifier.height(8.dp))
    LjCheckboxRow(
        checked = uiState.hideWidgetOverlay,
        onCheckedChange = { onAction(SettingsAction.SetHideWidgetOverlay(it)) },
        title = "Hide floating widget",
        description = "Keeps the floating widget button from appearing while spoofing is active.",
    )
    Spacer(Modifier.height(8.dp))
    LjCheckboxRow(
        checked = uiState.hideForegroundNotification,
        onCheckedChange = { onAction(SettingsAction.SetHideForegroundNotification(it)) },
        title = "Hide notification icon",
        description =
            "Removes the status bar icon for the spoofing notification. Android requires the " +
                "notification to keep existing — it's tucked into the notification shade instead.",
    )
    Spacer(Modifier.height(8.dp))
    LjCheckboxRow(
        checked = uiState.showRouteJumpButtons,
        onCheckedChange = { onAction(SettingsAction.SetShowRouteJumpButtons(it)) },
        title = "Show route jump buttons",
        description =
            "Adds Previous waypoint / Next waypoint buttons to route replay controls, " +
                "for instantly teleporting between waypoints. Off by default.",
    )
    Spacer(Modifier.height(8.dp))
    LjCheckboxRow(
        checked = uiState.altitudeOverrideButtonEnabled,
        onCheckedChange = { onAction(SettingsAction.SetAltitudeOverrideButtonEnabled(it)) },
        title = "Show altitude override button",
        description =
            "Adds a button to the floating widget for typing in a fixed altitude from anywhere. Off by default.",
    )
}

@Composable
private fun DebugSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    Text("Debug", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(4.dp))
    LjCheckboxRow(
        checked = uiState.debugStatsEnabled,
        onCheckedChange = { onAction(SettingsAction.SetDebugStatsEnabled(it)) },
        title = "Debug stats",
        description =
            "Shows live speed, altitude, coordinates, and tick rate in the floating " +
                "widget panel while spoofing is active.",
    )
}

@Composable
private fun CompassOrientationSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onTestCompassDetection: suspend () -> Float? = { null },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    Text("Compass orientation", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(4.dp))
    Text(
        "When enabled, the app detects your game's compass (top-right corner, like Pokémon GO's) before each walk " +
            "to correct the target position — no setup needed. Requires an Accessibility Service. Note: some games " +
            "detect accessibility services.",
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
            Text("Accessibility Service", style = MaterialTheme.typography.bodyLarge)
            Text(
                if (uiState.isCompassServiceGranted) "Enabled" else "Not enabled — tap to open Android Settings",
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
            LjButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            }) { Text("Open Settings") }
        }
    }
    if (uiState.isCompassServiceGranted) {
        Spacer(Modifier.height(8.dp))
        LjCheckboxRow(
            checked = uiState.compassTrackingEnabled,
            onCheckedChange = {
                onAction(SettingsAction.SetCompassTrackingEnabled(it))
                testResult = null
            },
            title = "Detect compass orientation",
            description = "Auto-locates the compass icon — no manual calibration.",
        )
        if (uiState.compassTrackingEnabled) {
            Spacer(Modifier.height(8.dp))
            Text(
                "To verify it works on your device: switch to your game so its compass is visible " +
                    "(top-right corner), then switch back here and tap Test — the app briefly minimizes " +
                    "itself to capture your game's screen, then returns.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LjOutlinedButton(
                    onClick = {
                        isTesting = true
                        testResult = null
                        scope.launch {
                            // Detection reads whatever is CURRENTLY on screen — if our own Settings UI is
                            // in front, that's what gets captured, not the game behind it. Briefly send
                            // ourselves to the back (revealing the game, which is directly behind us in
                            // the task stack per the instructions above) before capturing, then return.
                            (context as? Activity)?.moveTaskToBack(true)
                            delay(700)
                            val angle = onTestCompassDetection()
                            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                                it.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(it)
                            }
                            testResult =
                                if (angle != null) {
                                    "Detected — north is ${Math.toDegrees(angle.toDouble()).roundToInt()}° from up"
                                } else {
                                    "Not detected — make sure your game's compass is visible top-right"
                                }
                            isTesting = false
                        }
                    },
                    enabled = !isTesting,
                ) { Text(if (isTesting) "Testing…" else "Test") }
                if (testResult != null) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        testResult!!,
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (testResult!!.startsWith("Detected")) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                    )
                }
            }
        }
    }
}

/**
 * Opens a full-screen system overlay for calibration, so it draws on top of whatever app the user
 * switches to (a game left running) instead of only working inside Settings. Falls back to
 * requesting SYSTEM_ALERT_WINDOW if not yet granted — the same permission joystick/widget need.
 */
private fun startCalibrationOverlay(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    savedStateRegistryOwner: SavedStateRegistryOwner,
    content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    if (!isOverlayPermissionGranted(context)) {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
        )
        return
    }
    val overlay =
        CalibrationOverlay(context.applicationContext, lifecycleOwner, savedStateRegistryOwner) { dismiss ->
            content(dismiss)
        }
    overlay.show()
}

/**
 * Derives m/px from a known real-world distance between two tapped points on the live screen,
 * instead of the user guessing-and-checking against the manual slider. Points are tapped directly
 * in full-screen overlay coordinates, so no scale-up from a shrunk preview is needed.
 */
@Composable
private fun ScaleCalibrationOverlayContent(
    onApply: (Double) -> Unit,
    dismiss: () -> Unit,
) {
    var pointA by remember { mutableStateOf<Offset?>(null) }
    var pointB by remember { mutableStateOf<Offset?>(null) }
    var distanceText by remember { mutableStateOf("") }

    val pixelDistance =
        if (pointA != null && pointB != null) {
            val dx = pointA!!.x - pointB!!.x
            val dy = pointA!!.y - pointB!!.y
            sqrt(dx * dx + dy * dy)
        } else {
            null
        }
    val distanceMeters = distanceText.toDoubleOrNull()
    val computedScale =
        if (pixelDistance != null && pixelDistance > 0f && distanceMeters != null && distanceMeters > 0.0) {
            distanceMeters / pixelDistance
        } else {
            null
        }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (pointA == null || pointB != null) {
                        pointA = down.position
                        pointB = null
                    } else {
                        pointB = down.position
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            listOfNotNull(pointA, pointB).forEach { p ->
                drawCircle(color = Color.Red, center = p, radius = 10.dp.toPx())
            }
            if (pointA != null && pointB != null) {
                drawLine(Color.Red, pointA!!, pointB!!, strokeWidth = 3.dp.toPx())
            }
        }
        Text(
            "Tap two landmarks on your game, then enter the real-world distance below",
            color = Color.White,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp)
                    .background(Color.Black.copy(alpha = 0.7f), MaterialTheme.shapes.small)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(16.dp),
        ) {
            OutlinedTextField(
                value = distanceText,
                onValueChange = { distanceText = it },
                label = { Text("Real-world distance (meters)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                computedScale?.let { "Computed scale: %.3f m/px".format(it) }
                    ?: "Tap two points and enter a distance to compute the scale.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                LjTextButton(onClick = dismiss) { Text("Cancel") }
                LjTextButton(
                    onClick = {
                        computedScale
                            ?.coerceIn(AppConstants.TapToWalkConstants.MIN_SCALE_MPX, AppConstants.TapToWalkConstants.MAX_SCALE_MPX)
                            ?.let(onApply)
                        dismiss()
                    },
                    enabled = computedScale != null,
                ) { Text("Apply") }
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

private fun featureMeta(feature: AppFeature): FeatureMeta =
    when (feature) {
        AppFeature.MAP_FLOATING -> {
            FeatureMeta("Map shortcut", "Opens a compact map view without switching to the main app.", LjIcons.LocationOn)
        }

        AppFeature.JOYSTICK_TOGGLE -> {
            FeatureMeta("Show/hide joystick", "Toggles the floating joystick overlay on or off.", LjIcons.Visibility)
        }

        AppFeature.JOYSTICK_LOCK -> {
            FeatureMeta(
                "Lock joystick",
                "Keeps the joystick moving in the last held direction after you release.",
                LjIcons.Lock,
            )
        }

        AppFeature.FAVORITES -> {
            FeatureMeta("Favorites", "Teleport or walk to a saved location.", LjIcons.Favorite)
        }

        AppFeature.ROUTES -> {
            FeatureMeta("Routes", "Lists saved routes and starts replay.", LjIcons.Route)
        }

        AppFeature.ROAMING -> {
            FeatureMeta("Roaming", "Configure and start random walking within a radius.", LjIcons.Explore)
        }

        AppFeature.SEARCH -> {
            FeatureMeta("Search", "Find and jump to a place by name.", LjIcons.Search)
        }

        AppFeature.SPEED_CYCLE -> {
            FeatureMeta("Speed cycle", "Cycles through all speed profiles with a single tap.", LjIcons.Speed)
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
    Text("App Features", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "Choose which quick-access features appear in the floating widget and on the map screen, " +
            "and drag to reorder them. Both surfaces share the same order by default.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))

    Row(modifier = Modifier.fillMaxWidth().padding(start = 48.dp), horizontalArrangement = Arrangement.End) {
        Text("Widget", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(56.dp), textAlign = TextAlign.Center)
        Text("Map", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(56.dp), textAlign = TextAlign.Center)
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
    Text("Speed Cycle", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "Choose which speed profiles the widget's Speed Cycle button cycles through.",
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
            contentDescription = "Drag to reorder ${meta.label}",
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
            Checkbox(
                checked = feature in uiState.enabledWidgetFeatures,
                enabled = rowEnabled,
                modifier = Modifier.width(56.dp).semantics { contentDescription = "${meta.label} on widget" },
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
            Checkbox(
                checked = feature in uiState.enabledMapFeatures,
                modifier = Modifier.width(56.dp).semantics { contentDescription = "${meta.label} on map" },
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
