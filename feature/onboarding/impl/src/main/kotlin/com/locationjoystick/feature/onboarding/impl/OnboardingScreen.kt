package com.locationjoystick.feature.onboarding.impl

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.locationjoystick.core.designsystem.LjWarning
import com.locationjoystick.core.designsystem.LjWarningContainer
import com.locationjoystick.core.designsystem.component.AppIcon
import com.locationjoystick.core.designsystem.component.LjPrimaryButton
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.location.SpoofToggleViewModel
import com.locationjoystick.feature.onboarding.api.ONBOARDING_ROUTE

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
    spoofToggleViewModel: SpoofToggleViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isSpoofing by spoofToggleViewModel.isSpoofing.collectAsStateWithLifecycle()

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
        onSetupComplete = {
            viewModel.onSetupComplete()
            onSetupComplete()
        },
        isSpoofing = isSpoofing,
        onToggleSpoofing = spoofToggleViewModel::toggle,
        bottomBar = bottomBar,
    )
}

@Composable
internal fun OnboardingScreen(
    uiState: OnboardingUiState,
    onCheckPermissions: () -> Unit,
    onSetupComplete: () -> Unit,
    isSpoofing: Boolean = false,
    onToggleSpoofing: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
) {
    val context = LocalContext.current

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { onCheckPermissions() },
        )

    LjScaffold(
        title = "",
        isSpoofing = isSpoofing,
        onToggleSpoofing = onToggleSpoofing,
        onNavigationClick = null,
        bottomBar = bottomBar,
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(remember { ScrollState(0) })
                    .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon()

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Set up locationjoystick",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "v${AppConstants.AppInfo.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Complete the steps below to start simulating your GPS location.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(32.dp))

            OnboardingStepCard(
                title = "Location permission",
                description = "Required to read your real position for map centering and route recording.",
                isGranted = uiState.locationPermissionGranted,
                icon = LjIcons.LocationOn,
                actionLabel = "Grant Permission",
                isOptional = false,
                onAction = { locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
            )

            Spacer(modifier = Modifier.height(12.dp))

            OnboardingStepCard(
                title = "Display over other apps",
                description = "Required for the floating joystick and quick-access widget while other apps are open.",
                isGranted = uiState.overlayPermissionGranted,
                icon = LjIcons.Layers,
                actionLabel = "Open Settings",
                isOptional = false,
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

            OnboardingStepCard(
                title = "Mock location app",
                description = "In Developer Options → Select mock location app, choose locationjoystick.",
                isGranted = uiState.mockLocationEnabled,
                icon = LjIcons.DeveloperMode,
                actionLabel = "Open Developer Options",
                isOptional = false,
                onAction = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )

            Spacer(modifier = Modifier.height(32.dp))

            LjPrimaryButton(
                text = "Start using locationjoystick",
                onClick = onSetupComplete,
                enabled = uiState.canProceed || uiState.isDebugBuild,
                modifier = Modifier.fillMaxWidth(),
            )

            if (uiState.isDebugBuild && !uiState.canProceed) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Debug build — permissions optional",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OnboardingStepCard(
    title: String,
    description: String,
    isGranted: Boolean,
    icon: ImageVector,
    actionLabel: String,
    isOptional: Boolean,
    modifier: Modifier = Modifier,
    extraActionLabel: String? = null,
    onAction: () -> Unit,
    onExtraAction: (() -> Unit)? = null,
) {
    val statusColor by animateColorAsState(
        targetValue = if (isGranted) MaterialTheme.colorScheme.secondary else LjWarning,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "statusColor",
    )
    val statusContainerColor by animateColorAsState(
        targetValue = if (isGranted) MaterialTheme.colorScheme.secondaryContainer else LjWarningContainer,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "statusContainerColor",
    )

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(statusContainerColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isGranted) LjIcons.CheckCircle else icon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isOptional) {
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = "Optional",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!isGranted) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onAction,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }

                    if (extraActionLabel != null && onExtraAction != null) {
                        TextButton(onClick = onExtraAction) {
                            Text(
                                text = extraActionLabel,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }
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
