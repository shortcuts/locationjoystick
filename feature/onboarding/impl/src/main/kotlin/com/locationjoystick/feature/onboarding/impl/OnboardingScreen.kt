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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.designsystem.component.AppIcon
import com.locationjoystick.core.designsystem.component.LjPrimaryButton
import com.locationjoystick.feature.onboarding.api.ONBOARDING_ROUTE

private val AmberPending = Color(0xFFF59E0B)
private val AmberPendingContainer = Color(0xFF451A03)

fun NavGraphBuilder.onboardingScreen(onSetupComplete: () -> Unit) {
    composable(route = ONBOARDING_ROUTE) {
        OnboardingRoute(onSetupComplete = onSetupComplete)
    }
}

@Composable
fun OnboardingRoute(
    onSetupComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
    )
}

@Composable
internal fun OnboardingScreen(
    uiState: OnboardingUiState,
    onCheckPermissions: () -> Unit,
    onSetupComplete: () -> Unit,
) {
    val context = LocalContext.current

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { onCheckPermissions() },
        )

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            AppIcon()

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Set up locationjoystick",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
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
                icon = Icons.Rounded.LocationOn,
                actionLabel = "Grant Permission",
                isOptional = false,
                onAction = { locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
            )

            Spacer(modifier = Modifier.height(12.dp))

            OnboardingStepCard(
                title = "Display over other apps",
                description = "Required for the floating joystick and quick-access widget while other apps are open.",
                isGranted = uiState.overlayPermissionGranted,
                icon = Icons.Outlined.Layers,
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
                description = "In Developer Options → Select mock location app, choose locationjoystick. Tap \"Check again\" after.",
                isGranted = uiState.mockLocationEnabled,
                icon = Icons.Rounded.DeveloperMode,
                actionLabel = "Open Developer Options",
                isOptional = false,
                extraActionLabel = "Check again",
                onAction = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                onExtraAction = onCheckPermissions,
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
        targetValue = if (isGranted) MaterialTheme.colorScheme.secondary else AmberPending,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "statusColor",
    )
    val statusContainerColor by animateColorAsState(
        targetValue = if (isGranted) MaterialTheme.colorScheme.secondaryContainer else AmberPendingContainer,
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
                        imageVector = if (isGranted) Icons.Rounded.CheckCircle else icon,
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
