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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.common.util.CaptureBrowserChoice
import com.locationjoystick.core.common.util.formatCapturedPoint
import com.locationjoystick.core.common.util.formatCapturedPointsForClipboard
import com.locationjoystick.core.designsystem.LjAccent
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjSpacing
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.designsystem.R
import com.locationjoystick.core.model.LatLng
import kotlinx.coroutines.launch

/** Capture-mode toggle (Step 1: mode + List/Jump) — see docs/features/capture-coordinates.md. */
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
 * passthrough-browser picker. While `isDefaultBrowser` is false only the setup is shown — see
 * docs/features/capture-coordinates.md.
 */
data class CaptureSetupState(
    val isDefaultBrowser: Boolean,
    val passThroughBrowserName: String,
    val browserChoices: List<CaptureBrowserChoice>,
    val selectedBrowserPackage: String?,
    val onSelectBrowser: (String) -> Unit,
    val onRequestDefaultBrowser: () -> Unit,
    val onOpenMapsLinks: () -> Unit,
    val onOpenSetupGuide: () -> Unit,
)

@Composable
fun CaptureCoordinatesForm(
    captureMode: CaptureModeState,
    captureSetup: CaptureSetupState,
    capturePoints: CapturePointsState,
    routeSave: CaptureRouteSaveState,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    showClose: Boolean = true,
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
        AlertDialog(
            onDismissRequest = { showClearPrompt = false },
            title = { Text(stringResource(R.string.capture_coordinates_form_clear_captured_locations)) },
            text = { Text(stringResource(R.string.capture_previous_points, capturePoints.points.size)) },
            confirmButton = {
                Button(
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
                TextButton(
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
        verticalArrangement = Arrangement.spacedBy(LjSpacing.lg),
    ) {
        if (showTitle) {
            Text(
                text = stringResource(R.string.capture_coordinates_form_capture_coordinates),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
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
            Column(verticalArrangement = Arrangement.spacedBy(LjSpacing.sm)) {
                Text(
                    text =
                        if (capturePoints.points.isEmpty()) {
                            stringResource(R.string.capture_empty)
                        } else {
                            stringResource(R.string.capture_point_count, capturePoints.points.size)
                        },
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedTextField(
                    value =
                        capturePoints.points
                            .mapIndexed { index, point -> "${index + 1}. ${formatCapturedPoint(point)}" }
                            .joinToString("\n"),
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.capture_empty)) },
                    minLines = 3,
                    maxLines = 8,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LjAccent,
                            unfocusedBorderColor = LjAccent,
                            disabledBorderColor = LjAccent,
                        ),
                )
                Text(
                    stringResource(R.string.capture_coordinates_form_point_order),
                    style = MaterialTheme.typography.titleMedium,
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
            Column(verticalArrangement = Arrangement.spacedBy(LjSpacing.sm)) {
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
                Button(
                    onClick = routeSave.onSaveRoute,
                    enabled = routeSave.canSave,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.capture_coordinates_form_save_as_route))
                }
            }
        } else {
            CaptureSetupSteps(state = captureSetup)
        }
        if (showClose) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.capture_coordinates_form_close))
            }
        }
    }
}

@Composable
private fun CaptureToggleStep(
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
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = captureModeEnabled, onCheckedChange = onCaptureModeEnabledChange)
            }
            CaptureFunctionToggle(
                label = stringResource(R.string.capture_coordinates_form_list),
                description = stringResource(R.string.capture_coordinates_form_add_each_location_to_the_list),
                checked = captureEnabled,
                enabled = captureModeEnabled,
                onCheckedChange = onCaptureEnabledChange,
            )
            CaptureFunctionToggle(
                label = stringResource(R.string.capture_coordinates_form_jump),
                description = stringResource(R.string.capture_coordinates_form_teleport_to_each_location_immediately),
                checked = jumpEnabled,
                enabled = captureModeEnabled,
                onCheckedChange = onJumpEnabledChange,
            )
        }
    }
}

@Composable
private fun CaptureSetupSteps(
    state: CaptureSetupState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
        TextButton(onClick = state.onOpenSetupGuide, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.capture_setup_guide))
        }
    }
}

@Composable
private fun ColumnScope.CapturePassThroughRow(state: CaptureSetupState) {
    var showBrowserPicker by remember { mutableStateOf(false) }

    TextButton(
        onClick = { showBrowserPicker = state.browserChoices.isNotEmpty() },
        modifier = Modifier.align(Alignment.End),
    ) {
        Text(stringResource(R.string.capture_passthrough_label, state.passThroughBrowserName))
    }

    if (showBrowserPicker) {
        AlertDialog(
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
                TextButton(onClick = { showBrowserPicker = false }) {
                    Text(stringResource(R.string.capture_setup_form_cancel))
                }
            },
        )
    }
}

@Composable
private fun CaptureFunctionToggle(
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Checkbox(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
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
    val buttonPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    val copyDescription = stringResource(R.string.capture_coordinates_form_copy_coordinates)
    val removeDescription = stringResource(R.string.capture_coordinates_form_remove_last)
    val clearDescription = stringResource(R.string.capture_coordinates_form_clear_list)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onCopy,
            enabled = enabled,
            contentPadding = buttonPadding,
            modifier = Modifier.semantics { contentDescription = copyDescription },
        ) {
            Icon(
                imageVector = LjIcons.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.capture_coordinates_form_copy))
        }
        Spacer(modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = onRemoveLast,
            enabled = enabled,
            contentPadding = buttonPadding,
            modifier = Modifier.semantics { contentDescription = removeDescription },
        ) {
            Icon(
                imageVector = LjIcons.Undo,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.capture_coordinates_form_last))
        }
        Spacer(modifier = Modifier.width(LjSpacing.sm))
        OutlinedButton(
            onClick = onClearPoints,
            enabled = enabled,
            contentPadding = buttonPadding,
            modifier = Modifier.semantics { contentDescription = clearDescription },
        ) {
            Icon(
                imageVector = LjIcons.Delete,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.capture_coordinates_form_clear))
        }
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
                    onRequestDefaultBrowser = {},
                    onOpenMapsLinks = {},
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
            onDismiss = {},
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
                    onRequestDefaultBrowser = {},
                    onOpenMapsLinks = {},
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
            onDismiss = {},
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
                    onRequestDefaultBrowser = {},
                    onOpenMapsLinks = {},
                    onOpenSetupGuide = {},
                ),
        )
    }
}
