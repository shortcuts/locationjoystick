package com.locationjoystick.core.designsystem.component

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.common.util.CaptureBrowserChoice
import com.locationjoystick.core.common.util.formatCapturedPoint
import com.locationjoystick.core.common.util.formatCapturedPointsForClipboard
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjSpacing
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.designsystem.R
import com.locationjoystick.core.model.LatLng
import kotlinx.coroutines.launch

/** Capture-mode toggle (Step 1: mode + List/Jump) — see docs/features/location-links.md (Opt-in Tier). */
data class CaptureModeState(
    val captureModeEnabled: Boolean,
    val captureEnabled: Boolean,
    val jumpEnabled: Boolean,
    val onCaptureModeEnabledChange: (Boolean) -> Unit,
    val onCaptureEnabledChange: (Boolean) -> Unit,
    val onJumpEnabledChange: (Boolean) -> Unit,
)

/** Captured-points list, its point-order toggle, and its list actions. */
data class CapturePointsState(
    val points: List<LatLng>,
    val onClearPoints: () -> Unit,
    val onRemoveLast: () -> Unit,
    val optimizeProximity: Boolean = true,
    val onOptimizeProximityChange: (Boolean) -> Unit = {},
    val orderedPoints: List<LatLng> = points,
)

/** Route-name field and Save-as-route action. */
data class CaptureRouteSaveState(
    val routeName: String,
    val saved: Boolean,
    val saveError: String?,
    val canSave: Boolean,
    val onRouteNameChange: (String) -> Unit,
    val onSaveRoute: () -> Unit,
)

/**
 * Capture-mode setup guidance (default-browser role, Google Maps links, guide link) plus the
 * passthrough-browser picker. The setup steps render in Settings > Menus; while `isDefaultBrowser`
 * is false the Capture screen shows only a pointer there (`onOpenSetup`) — see
 * docs/features/location-links.md (Opt-in Tier).
 */
data class CaptureSetupState(
    val isDefaultBrowser: Boolean,
    val passThroughBrowserName: String,
    val browserChoices: List<CaptureBrowserChoice>,
    val selectedBrowserPackage: String?,
    val onSelectBrowser: (String) -> Unit,
    val onRequestDefaultBrowser: () -> Unit = {},
    val onOpenMapsLinks: () -> Unit = {},
    val onOpenSetupGuide: () -> Unit,
    val onOpenSetup: () -> Unit = {},
)

@Composable
fun CaptureCoordinatesForm(
    captureMode: CaptureModeState,
    captureSetup: CaptureSetupState,
    capturePoints: CapturePointsState,
    routeSave: CaptureRouteSaveState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = LjSpacing.md, vertical = LjSpacing.md),
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.capture_copied_count, capturePoints.orderedPoints.size)
    var showClearPrompt by remember { mutableStateOf(false) }

    fun requestCaptureMode(enabled: Boolean) {
        if (enabled && !captureMode.captureModeEnabled && capturePoints.points.isNotEmpty()) {
            showClearPrompt = true
        } else {
            captureMode.onCaptureModeEnabledChange(enabled)
        }
    }

    if (showClearPrompt) {
        LjAlertDialog(
            onDismissRequest = { showClearPrompt = false },
            title = { Text(stringResource(R.string.capture_coordinates_form_clear_captured_locations)) },
            text = { Text(stringResource(R.string.capture_previous_points, capturePoints.points.size)) },
            confirmButton = {
                LjButton(
                    onClick = {
                        capturePoints.onClearPoints()
                        captureMode.onCaptureModeEnabledChange(true)
                        showClearPrompt = false
                    },
                ) {
                    Text(stringResource(R.string.capture_coordinates_form_clear))
                }
            },
            dismissButton = {
                LjTextButton(
                    onClick = {
                        captureMode.onCaptureModeEnabledChange(true)
                        showClearPrompt = false
                    },
                ) {
                    Text(stringResource(R.string.capture_coordinates_form_keep))
                }
            },
        )
    }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .imePadding(),
        verticalArrangement = Arrangement.spacedBy(LjSpacing.md),
    ) {
        if (captureSetup.isDefaultBrowser) {
            Text(
                text = stringResource(R.string.capture_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CaptureToggleStep(
                captureModeEnabled = captureMode.captureModeEnabled,
                captureEnabled = captureMode.captureEnabled,
                jumpEnabled = captureMode.jumpEnabled,
                onCaptureModeEnabledChange = ::requestCaptureMode,
                onCaptureEnabledChange = captureMode.onCaptureEnabledChange,
                onJumpEnabledChange = captureMode.onJumpEnabledChange,
            )
            CapturePassThroughRow(state = captureSetup)
            LjCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(LjSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(LjSpacing.sm),
                ) {
                    Text(
                        text =
                            if (capturePoints.points.isEmpty()) {
                                stringResource(R.string.capture_empty)
                            } else {
                                stringResource(R.string.capture_point_count, capturePoints.points.size)
                            },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (capturePoints.points.isNotEmpty()) {
                        LjOutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier =
                                    Modifier
                                        .heightIn(max = 240.dp)
                                        .verticalScroll(rememberScrollState())
                                        .padding(LjSpacing.md),
                                verticalArrangement = Arrangement.spacedBy(LjSpacing.xs),
                            ) {
                                capturePoints.points.forEachIndexed { index, point ->
                                    Text(
                                        text = "${index + 1}. ${formatCapturedPoint(point)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                    CapturePointActions(
                        enabled = capturePoints.points.isNotEmpty(),
                        onCopy = {
                            val text = formatCapturedPointsForClipboard(capturePoints.orderedPoints)
                            scope.launch { clipboard.writePlainText(text) }
                            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                        },
                        onRemoveLast = capturePoints.onRemoveLast,
                        onClearPoints = capturePoints.onClearPoints,
                    )
                    Text(
                        stringResource(R.string.capture_coordinates_form_point_order),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = LjSpacing.sm),
                    )
                    LjSegmentedControl(
                        options =
                            listOf(
                                false to stringResource(R.string.capture_original_order),
                                true to stringResource(R.string.capture_optimize_order),
                            ),
                        selected = capturePoints.optimizeProximity,
                        onSelect = capturePoints.onOptimizeProximityChange,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            LjCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(LjSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(LjSpacing.sm),
                ) {
                    OutlinedTextField(
                        value = routeSave.routeName,
                        onValueChange = routeSave.onRouteNameChange,
                        label = { Text(stringResource(R.string.capture_coordinates_form_route_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = {
                            Text(
                                routeSave.saveError
                                    ?: if (routeSave.saved) {
                                        stringResource(R.string.capture_saved)
                                    } else {
                                        stringResource(R.string.capture_need_points)
                                    },
                            )
                        },
                        isError = routeSave.saveError != null,
                    )
                    LjPrimaryButton(
                        text = stringResource(R.string.capture_coordinates_form_save_as_route),
                        onClick = routeSave.onSaveRoute,
                        enabled = routeSave.canSave,
                    )
                }
            }
        } else {
            CaptureSetupNeeded(onOpenSetup = captureSetup.onOpenSetup)
        }
    }
}

/** Public so it can also be embedded as a Settings > Menus section — see docs/features/location-links.md (Opt-in Tier). */
@Composable
fun CaptureToggleStep(
    captureModeEnabled: Boolean,
    captureEnabled: Boolean,
    jumpEnabled: Boolean,
    onCaptureModeEnabledChange: (Boolean) -> Unit,
    onCaptureEnabledChange: (Boolean) -> Unit,
    onJumpEnabledChange: (Boolean) -> Unit,
) {
    LjCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(LjSpacing.md),
            verticalArrangement = Arrangement.spacedBy(LjSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onCaptureModeEnabledChange(!captureModeEnabled) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.capture_coordinates_form_capture_mode),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = captureModeEnabled, onCheckedChange = onCaptureModeEnabledChange)
            }
            LjCheckboxRow(
                checked = captureEnabled,
                onCheckedChange = onCaptureEnabledChange,
                title = stringResource(R.string.capture_coordinates_form_list),
                description = stringResource(R.string.capture_coordinates_form_add_each_location_to_the_list),
                enabled = captureModeEnabled,
            )
            LjCheckboxRow(
                checked = jumpEnabled,
                onCheckedChange = onJumpEnabledChange,
                title = stringResource(R.string.capture_coordinates_form_jump),
                description = stringResource(R.string.capture_coordinates_form_teleport_to_each_location_immediately),
                enabled = captureModeEnabled,
            )
        }
    }
}

@Composable
private fun CaptureSetupNeeded(onOpenSetup: () -> Unit) {
    LjCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(LjSpacing.md),
            verticalArrangement = Arrangement.spacedBy(LjSpacing.sm),
        ) {
            Text(
                text = stringResource(R.string.capture_setup_needed_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.capture_setup_needed_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LjButton(onClick = onOpenSetup, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.capture_setup_needed_action))
            }
        }
    }
}

/** Confirm dialog for "Restore default browser" — see docs/features/location-links.md (Opt-in Tier). */
@Composable
fun CaptureRestoreDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var understood by rememberSaveable { mutableStateOf(false) }
    LjAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.capture_restore_dialog_title)) },
        text = {
            LjCheckboxRow(
                checked = understood,
                onCheckedChange = { understood = it },
                title = stringResource(R.string.capture_restore_dialog_checkbox),
            )
        },
        confirmButton = {
            LjTextButton(onClick = onConfirm, enabled = understood) {
                Text(stringResource(R.string.capture_restore_dialog_confirm))
            }
        },
        dismissButton = {
            LjTextButton(onClick = onDismiss) { Text(stringResource(R.string.capture_restore_dialog_cancel)) }
        },
    )
}

/** Public so Settings > Menus can host the setup — see docs/features/location-links.md (Opt-in Tier). */
@Composable
fun CaptureSetupSteps(
    state: CaptureSetupState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LjSpacing.md)) {
        Text(
            text = stringResource(R.string.capture_setup_section_title),
            style = MaterialTheme.typography.titleMedium,
        )
        LjGuidedStepCard(
            title = stringResource(R.string.capture_setup_step_browser_title),
            description = stringResource(R.string.capture_setup_step_browser_desc_needed),
            isGranted = false,
            icon = LjIcons.OpenInNew,
            actionLabel = stringResource(R.string.capture_setup_step_browser_action),
            onAction = state.onRequestDefaultBrowser,
        )
        Text(
            text = stringResource(R.string.capture_setup_step_this_app_links_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LjGuidedStepCard(
            title = stringResource(R.string.capture_setup_step_maps_links_title),
            description = stringResource(R.string.capture_setup_step_maps_links_desc),
            isGranted = false,
            icon = LjIcons.Map,
            actionLabel = stringResource(R.string.capture_setup_step_maps_links_action),
            onAction = state.onOpenMapsLinks,
        )
        LjTextButton(onClick = state.onOpenSetupGuide, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.capture_setup_guide))
        }
    }
}

/** Public so it can also be embedded as a Settings > Menus section — see docs/features/location-links.md (Opt-in Tier). */
@Composable
fun ColumnScope.CapturePassThroughRow(state: CaptureSetupState) {
    var showBrowserPicker by remember { mutableStateOf(false) }

    LjOutlinedButton(
        onClick = { showBrowserPicker = state.browserChoices.isNotEmpty() },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.capture_passthrough_label, state.passThroughBrowserName))
    }

    if (showBrowserPicker) {
        LjAlertDialog(
            onDismissRequest = { showBrowserPicker = false },
            title = { Text(stringResource(R.string.capture_setup_form_pass_through_browser)) },
            text = {
                Column {
                    state.browserChoices.forEach { choice ->
                        Row(
                            modifier =
                                Modifier.clickable {
                                    state.onSelectBrowser(choice.packageName)
                                    showBrowserPicker = false
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = choice.packageName == state.selectedBrowserPackage,
                                onClick = {
                                    state.onSelectBrowser(choice.packageName)
                                    showBrowserPicker = false
                                },
                            )
                            Text(choice.label)
                        }
                    }
                }
            },
            confirmButton = {
                LjTextButton(onClick = { showBrowserPicker = false }) {
                    Text(stringResource(R.string.capture_setup_form_cancel))
                }
            },
        )
    }
}

@Composable
private fun CapturePointActions(
    enabled: Boolean,
    onCopy: () -> Unit,
    onRemoveLast: () -> Unit,
    onClearPoints: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val copyDescription = stringResource(R.string.capture_coordinates_form_copy_coordinates)
    val removeDescription = stringResource(R.string.capture_coordinates_form_remove_last)
    val clearDescription = stringResource(R.string.capture_coordinates_form_clear_list)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LjSpacing.sm),
    ) {
        PointActionButton(
            LjIcons.ContentCopy,
            stringResource(R.string.capture_coordinates_form_copy),
            copyDescription,
            enabled,
            onCopy,
            Modifier.weight(1f),
        )
        PointActionButton(
            LjIcons.Undo,
            stringResource(R.string.capture_coordinates_form_last),
            removeDescription,
            enabled,
            onRemoveLast,
            Modifier.weight(1f),
        )
        PointActionButton(
            LjIcons.Delete,
            stringResource(R.string.capture_coordinates_form_clear),
            clearDescription,
            enabled,
            onClearPoints,
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun PointActionButton(
    icon: ImageVector,
    label: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LjOutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics { contentDescription = description },
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(LjSpacing.xs))
        Text(label, maxLines = 1)
    }
}

@Preview
@Composable
private fun CaptureCoordinatesFormPreview() {
    LjTheme {
        CaptureCoordinatesForm(
            captureMode =
                CaptureModeState(
                    captureModeEnabled = true,
                    captureEnabled = true,
                    jumpEnabled = false,
                    onCaptureModeEnabledChange = {},
                    onCaptureEnabledChange = {},
                    onJumpEnabledChange = {},
                ),
            captureSetup =
                CaptureSetupState(
                    isDefaultBrowser = true,
                    passThroughBrowserName = "Chrome",
                    browserChoices = emptyList(),
                    selectedBrowserPackage = null,
                    onSelectBrowser = {},
                    onOpenSetupGuide = {},
                ),
            capturePoints =
                CapturePointsState(
                    points = listOf(LatLng(36.977695, 128.363905), LatLng(36.982194, 128.370129)),
                    onClearPoints = {},
                    onRemoveLast = {},
                ),
            routeSave =
                CaptureRouteSaveState(
                    routeName = "Morning route",
                    saved = false,
                    saveError = null,
                    canSave = true,
                    onRouteNameChange = {},
                    onSaveRoute = {},
                ),
        )
    }
}

@Preview
@Composable
private fun CaptureCoordinatesFormSetupPreview() {
    LjTheme {
        CaptureCoordinatesForm(
            captureMode =
                CaptureModeState(
                    captureModeEnabled = false,
                    captureEnabled = false,
                    jumpEnabled = false,
                    onCaptureModeEnabledChange = {},
                    onCaptureEnabledChange = {},
                    onJumpEnabledChange = {},
                ),
            captureSetup =
                CaptureSetupState(
                    isDefaultBrowser = false,
                    passThroughBrowserName = "Chrome",
                    browserChoices = emptyList(),
                    selectedBrowserPackage = null,
                    onSelectBrowser = {},
                    onOpenSetupGuide = {},
                ),
            capturePoints =
                CapturePointsState(
                    points = emptyList(),
                    onClearPoints = {},
                    onRemoveLast = {},
                ),
            routeSave =
                CaptureRouteSaveState(
                    routeName = "",
                    saved = false,
                    saveError = null,
                    canSave = false,
                    onRouteNameChange = {},
                    onSaveRoute = {},
                ),
        )
    }
}

@Preview
@Composable
private fun CaptureSetupStepsPreview() {
    LjTheme {
        CaptureSetupSteps(
            state =
                CaptureSetupState(
                    isDefaultBrowser = false,
                    passThroughBrowserName = "Chrome",
                    browserChoices = emptyList(),
                    selectedBrowserPackage = null,
                    onSelectBrowser = {},
                    onOpenSetupGuide = {},
                ),
        )
    }
}
