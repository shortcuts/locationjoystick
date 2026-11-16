package com.locationjoystick.feature.settings.impl

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.locationjoystick.core.designsystem.LjSpacing
import com.locationjoystick.core.designsystem.component.AccessibilityDisclosureDialog
import com.locationjoystick.core.designsystem.component.LjButton
import com.locationjoystick.core.designsystem.component.LjSelectionMode
import com.locationjoystick.core.designsystem.component.LjSelectionRow
import com.locationjoystick.core.model.GuidedPath
import com.locationjoystick.core.model.ScreenFeatureGates
import com.locationjoystick.core.model.TapToWalkMove
import com.locationjoystick.core.model.withMove
import com.locationjoystick.feature.settings.impl.R
import com.locationjoystick.core.designsystem.R as DesignR

@Composable
internal fun SettingsScreenHelpersSubScreen(
    screenState: SettingsScreenState,
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

    SettingsScrollScaffold(
        title = stringResource(R.string.settings_hub_screen_helpers),
        screenState = screenState,
        onNavigateBack = onNavigateBack,
        isSpoofing = isSpoofing,
        onToggleSpoofing = onToggleSpoofing,
        locationLabel = locationLabel,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
    ) { uiState ->
        if (uiState.settings.guidedPath.showsTapToWalk) {
            TapToWalkSection(uiState, onAction)
        }
        // Same API 30 floor as the screenshot the check depends on.
        if (uiState.settings.guidedPath.showsReconnectAccessory && Build.VERSION.SDK_INT >= ScreenFeatureGates.MIN_SDK) {
            if (uiState.settings.guidedPath.showsTapToWalk) SettingsSectionDivider()
            ReconnectTapSection(uiState, onAction)
        }
        if (showsScreenAccessBlock(Build.VERSION.SDK_INT, tapToWalkOn(uiState), reconnectOn(uiState))) {
            SettingsSectionDivider()
            ScreenAccessSection(uiState, onAction)
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
    val enabled = uiState.settings.tapToWalkOverlayEnabled
    val needsAccessibilityDisclosure =
        showsFullAccessibilityDisclosure(Build.VERSION.SDK_INT, uiState.accessibilityDisclosureAccepted)

    SettingsSectionHeader(stringResource(R.string.settings_menus_tap_to_walk))
    Spacer(Modifier.height(LjSpacing.xs))
    Text(
        stringResource(R.string.settings_menus_walk_to_a_location_by_tapping),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(LjSpacing.sm))
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
        Spacer(Modifier.height(LjSpacing.sm))
        TapToWalkMoveRows(uiState, onAction)
        // takeScreenshot(int, Executor, TakeScreenshotCallback) requires API 30 — no fallback exists.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (showsServiceOffHint(enabled, uiState.isScreenServiceGranted)) {
                Spacer(Modifier.height(LjSpacing.smMd))
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
private fun TapToWalkMoveRows(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    val labels =
        mapOf(
            TapToWalkMove.WALK to stringResource(R.string.settings_menus_tap_to_walk_move_walk),
            TapToWalkMove.VIA_ROADS to stringResource(R.string.settings_menus_tap_to_walk_move_via_roads),
            TapToWalkMove.TELEPORT to stringResource(R.string.settings_menus_tap_to_walk_move_teleport),
        )
    TapToWalkMove.entries.filter { it != TapToWalkMove.TELEPORT || !uiState.settings.hideTeleportFeatures }.forEach { move ->
        LjSelectionRow(
            mode = LjSelectionMode.Multi,
            title = labels.getValue(move),
            checked = move in uiState.settings.tapToWalkMoves,
            enabled = tapToWalkRowEnabled(move, uiState.settings.tapToWalkMoves, uiState.settings.hideTeleportFeatures),
            onCheckedChange = { on -> onAction(SettingsAction.SetTapToWalkMoves(uiState.settings.tapToWalkMoves.withMove(move, on))) },
        )
    }
    Text(
        stringResource(R.string.settings_menus_tap_to_walk_moves_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ReconnectTapSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    val context = LocalContext.current
    var showDisclosure by rememberSaveable { mutableStateOf(false) }

    SettingsSectionHeader(stringResource(R.string.settings_menus_reconnect_accessory))
    Spacer(Modifier.height(LjSpacing.xs))
    Text(
        stringResource(R.string.settings_menus_reconnect_accessory_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(LjSpacing.sm))
    LjSelectionRow(
        mode = LjSelectionMode.Toggle,
        title = stringResource(R.string.settings_menus_reconnect_accessory_automatically),
        checked = uiState.settings.reconnectTapEnabled,
        onCheckedChange = { on ->
            if (on && !uiState.accessibilityDisclosureAccepted) {
                showDisclosure = true
            } else {
                onAction(SettingsAction.SetReconnectTapEnabled(on))
            }
        },
    )
    if (showsServiceOffHint(uiState.settings.reconnectTapEnabled, uiState.isScreenServiceGranted)) {
        Spacer(Modifier.height(LjSpacing.smMd))
        ServiceOffHint(uiState, onAction)
    }
    if (showDisclosure || showsReconnectConsentPrompt(uiState.settings.reconnectTapEnabled, uiState.accessibilityDisclosureAccepted)) {
        AccessibilityDisclosureDialog(
            title = stringResource(R.string.settings_menus_reconnect_accessory),
            onAccept = {
                showDisclosure = false
                onAction(SettingsAction.SetAccessibilityDisclosureAccepted(true))
                onAction(SettingsAction.SetReconnectTapEnabled(true))
                if (!uiState.isScreenServiceGranted) openAccessibilitySettings(context)
            },
            onDecline = {
                showDisclosure = false
                if (uiState.settings.reconnectTapEnabled) onAction(SettingsAction.SetReconnectTapEnabled(false))
            },
        )
    }
}

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
        !accessibilityAccepted -> null
        else -> true
    }

internal fun showsScreenAccessBlock(
    sdkInt: Int,
    tapToWalkOn: Boolean,
    reconnectOn: Boolean,
): Boolean = sdkInt >= Build.VERSION_CODES.R && (tapToWalkOn || reconnectOn)

/** An import can restore Reconnect on without consent, which the backup never carries. */
internal fun showsReconnectConsentPrompt(
    reconnectOn: Boolean,
    accepted: Boolean,
): Boolean = reconnectOn && !accepted

/** False only for the one checked row the user can see: the last visible checked box cannot be unchecked. */
internal fun tapToWalkRowEnabled(
    move: TapToWalkMove,
    stored: Set<TapToWalkMove>,
    hideTeleport: Boolean,
): Boolean {
    val visibleChecked = stored.filter { it != TapToWalkMove.TELEPORT || !hideTeleport }
    return !(visibleChecked.size == 1 && visibleChecked.single() == move)
}

internal fun showsServiceOffHint(
    featureOn: Boolean,
    serviceGranted: Boolean,
): Boolean = featureOn && !serviceGranted

// A switch on a section the guided path hides does not count: the user cannot see it.
private fun tapToWalkOn(ui: SettingsUiState) =
    ScreenFeatureGates.tapToWalkShown(ui.settings.tapToWalkOverlayEnabled, ui.settings.guidedPath)

private fun reconnectOn(ui: SettingsUiState) = ScreenFeatureGates.reconnectShown(ui.settings.reconnectTapEnabled, ui.settings.guidedPath)

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
    Spacer(Modifier.height(LjSpacing.xs))
    Text(
        stringResource(R.string.settings_menus_when_enabled_the_app_detects_your),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(LjSpacing.sm))
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
            Spacer(Modifier.width(LjSpacing.sm))
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
            if (!uiState.accessibilityDisclosureAccepted) {
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
        Spacer(Modifier.width(LjSpacing.sm))
        OpenSettingsButton(uiState, onAction)
    }
}

internal fun showsScreenHelpersCard(path: GuidedPath): Boolean = path.showsTapToWalk || path.showsReconnectAccessory
