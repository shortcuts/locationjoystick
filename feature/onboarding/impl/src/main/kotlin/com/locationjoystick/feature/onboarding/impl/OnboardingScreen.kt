package com.locationjoystick.feature.onboarding.impl

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.designsystem.component.AppIcon
import com.locationjoystick.core.designsystem.component.LjGuidedStepCard
import com.locationjoystick.core.designsystem.component.LjLanguageDropdown
import com.locationjoystick.core.designsystem.component.LjPrimaryButton
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.designsystem.component.WideContentClamp
import com.locationjoystick.core.location.rememberSpoofToggleState
import com.locationjoystick.core.model.AppLanguage
import com.locationjoystick.feature.onboarding.api.ONBOARDING_ROUTE
import com.locationjoystick.feature.onboarding.impl.R

fun NavGraphBuilder.onboardingScreen(onSetupComplete: () -> Unit) {
    composable(route = ONBOARDING_ROUTE) {
        OnboardingRoute(onSetupComplete = onSetupComplete)
    }
}

@Composable
fun OnboardingRoute(
    onSetupComplete: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val languageTag by viewModel.languageTag.collectAsStateWithLifecycle()
    val spoofToggle = rememberSpoofToggleState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.checkPermissions()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    OnboardingScreen(
        uiState = uiState,
        onCheckPermissions = viewModel::checkPermissions,
        onSkipMockLocationCheck = viewModel::skipMockLocationCheck,
        onSetupComplete = {
            viewModel.onSetupComplete()
            onSetupComplete()
        },
        isSpoofing = spoofToggle.isSpoofing,
        onToggleSpoofing = spoofToggle.onToggle,
        locationLabel = spoofToggle.locationLabel,
        bottomBar = bottomBar,
        languageTag = languageTag,
        onSetLanguage = { tag ->
            viewModel.setLanguage(tag)
            (context as? Activity)?.recreate()
        },
    )
}

@Composable
internal fun OnboardingScreen(
    uiState: OnboardingUiState,
    onCheckPermissions: () -> Unit,
    onSkipMockLocationCheck: () -> Unit = {},
    onSetupComplete: () -> Unit,
    isSpoofing: Boolean = false,
    onToggleSpoofing: () -> Unit = {},
    locationLabel: String? = null,
    bottomBar: @Composable () -> Unit = {},
    languageTag: String? = null,
    onSetLanguage: (String?) -> Unit = {},
) {
    val context = LocalContext.current
    var showSkipMockLocationDialog by remember { mutableStateOf(false) }

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { onCheckPermissions() },
        )

    LjScaffold(
        title = "",
        isSpoofing = isSpoofing,
        onToggleSpoofing = onToggleSpoofing,
        locationLabel = locationLabel,
        onNavigationClick = null,
        bottomBar = bottomBar,
        containerColor = MaterialTheme.colorScheme.background,
        showSpoofToggle = false,
        actions = {
            LjLanguageDropdown(
                selected = AppLanguage.fromTag(languageTag),
                onSelect = { language -> onSetLanguage(language.languageTag) },
            )
        },
    ) { paddingValues ->
        WideContentClamp(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentModifier = Modifier.verticalScroll(remember { ScrollState(0) }).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon()

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.onboarding_set_up_locationjoystick),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.onboarding_version, AppConstants.AppInfo.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.onboarding_complete_the_steps_below_to_start),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.onboarding_trouble_setting_up),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(AppConstants.AppInfo.DOCS_URL)),
                        )
                    },
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_getting_started),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    text = stringResource(R.string.onboarding_separator_dot),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(AppConstants.AppInfo.TROUBLESHOOTING_URL)),
                        )
                    },
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_troubleshooting),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val grantedStepCount =
                listOf(
                    uiState.locationPermissionGranted,
                    uiState.overlayPermissionGranted,
                    uiState.mockLocationEnabled,
                ).count { it }

            Text(
                text = stringResource(R.string.onboarding_step_x_of_3, grantedStepCount.coerceAtMost(2) + 1),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(4.dp))

            LinearProgressIndicator(
                progress = { grantedStepCount / 3f },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(modifier = Modifier.height(20.dp))

            LjGuidedStepCard(
                title = stringResource(R.string.onboarding_location_permission),
                description = stringResource(R.string.onboarding_location_permission_desc),
                isGranted = uiState.locationPermissionGranted,
                icon = LjIcons.LocationOn,
                actionLabel = stringResource(R.string.onboarding_grant_permission),
                onAction = { locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
            )

            Spacer(modifier = Modifier.height(12.dp))

            LjGuidedStepCard(
                title = stringResource(R.string.onboarding_display_over_other_apps),
                description = stringResource(R.string.onboarding_display_over_other_apps_desc),
                isGranted = uiState.overlayPermissionGranted,
                icon = LjIcons.Layers,
                actionLabel = stringResource(R.string.onboarding_open_settings),
                onAction = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                },
            )

            Spacer(modifier = Modifier.height(12.dp))

            LjGuidedStepCard(
                title = stringResource(R.string.onboarding_set_as_fake_gps_app),
                description = stringResource(R.string.onboarding_set_as_fake_gps_app_desc),
                isGranted = uiState.mockLocationEnabled,
                icon = LjIcons.DeveloperMode,
                actionLabel = stringResource(R.string.onboarding_open_developer_options),
                onAction = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                extraActionLabel = stringResource(R.string.onboarding_skip),
                onExtraAction = { showSkipMockLocationDialog = true },
            )

            Spacer(modifier = Modifier.height(32.dp))

            LjPrimaryButton(
                text = stringResource(R.string.onboarding_start_using_locationjoystick),
                onClick = onSetupComplete,
                enabled = uiState.canProceed || uiState.isDebugBuild,
                modifier = Modifier.fillMaxWidth(),
            )

            if (uiState.isDebugBuild && !uiState.canProceed) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.onboarding_debug_build_permissions_optional),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showSkipMockLocationDialog) {
        SkipMockLocationConfirmDialog(
            onDismiss = { showSkipMockLocationDialog = false },
            onConfirm = {
                showSkipMockLocationDialog = false
                onSkipMockLocationCheck()
            },
        )
    }
}

@Composable
private fun SkipMockLocationConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.onboarding_skip_this_check)) },
        text = {
            Text(
                stringResource(R.string.onboarding_most_devices_need_locationjoystick_set_a),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.onboarding_skip_anyway))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.onboarding_cancel))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun OnboardingScreenPreview() {
    LjTheme {
        OnboardingScreen(
            uiState =
                OnboardingUiState(
                    locationPermissionGranted = true,
                    overlayPermissionGranted = false,
                    mockLocationEnabled = false,
                ),
            onCheckPermissions = {},
            onSetupComplete = {},
        )
    }
}

@Preview(showBackground = true, name = "Debug — permissions missing")
@Composable
private fun OnboardingScreenDebugPreview() {
    LjTheme {
        OnboardingScreen(
            uiState =
                OnboardingUiState(
                    locationPermissionGranted = false,
                    overlayPermissionGranted = false,
                    mockLocationEnabled = false,
                    isDebugBuild = true,
                ),
            onCheckPermissions = {},
            onSetupComplete = {},
        )
    }
}
