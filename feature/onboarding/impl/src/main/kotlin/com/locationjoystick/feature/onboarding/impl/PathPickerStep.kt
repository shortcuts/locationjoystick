package com.locationjoystick.feature.onboarding.impl

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjSpacing
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.designsystem.component.AccessibilityDisclosureDialog
import com.locationjoystick.core.designsystem.component.LjGuidedStepCard
import com.locationjoystick.core.designsystem.component.LjLanguageDropdown
import com.locationjoystick.core.designsystem.component.LjPrimaryButton
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.designsystem.component.LjSelectionMode
import com.locationjoystick.core.designsystem.component.LjSelectionRow
import com.locationjoystick.core.designsystem.component.LjSelectionStyle
import com.locationjoystick.core.designsystem.component.WideContentClamp
import com.locationjoystick.core.model.AppLanguage
import com.locationjoystick.core.model.GuidedPath
import com.locationjoystick.feature.onboarding.impl.R

/** Last onboarding step: pick the game path, and (unless Pikmin Bloom) the Tap to Walk disclosure card. */
@Composable
internal fun PathPickerStep(
    uiState: OnboardingUiState,
    onSelectPath: (GuidedPath) -> Unit,
    onAccessibilityDisclosureAnswered: (accepted: Boolean) -> Unit,
    onAccessibilityDisclosureSkipped: () -> Unit,
    onFinish: () -> Unit,
    isSpoofing: Boolean = false,
    onToggleSpoofing: () -> Unit = {},
    locationLabel: String? = null,
    bottomBar: @Composable () -> Unit = {},
    languageTag: String? = null,
    onSetLanguage: (String?) -> Unit = {},
) {
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }
    val showsTapToWalk = uiState.selectedPath.showsTapToWalk

    // Shown when the picker first appears so Play's reviewers meet it in the first-run flow. Onboarding
    // only runs once, so installs that already finished it never reach this prompt.
    LaunchedEffect(uiState.accessibilityDisclosureChoice) {
        if (showsTapToWalk &&
            uiState.accessibilityDisclosureChoice == AppConstants.AccessibilityConstants.DISCLOSURE_UNANSWERED &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        ) {
            showAccessibilityDisclosure = true
        }
    }

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
            Spacer(modifier = Modifier.height(LjSpacing.sm))
            Text(
                text = stringResource(R.string.onboarding_pick_path_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(LjSpacing.xs))
            Text(
                text = stringResource(R.string.onboarding_pick_path_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(LjSpacing.sm))
            Text(
                text = stringResource(R.string.onboarding_step_x_of_4, 4),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(LjSpacing.xs))
            LinearProgressIndicator(
                progress = { 1f },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))

            GuidedPath.entries.forEach { path ->
                LjSelectionRow(
                    mode = LjSelectionMode.Single,
                    style = LjSelectionStyle.Card,
                    title = stringResource(path.titleRes()),
                    description = stringResource(path.descriptionRes()),
                    checked = path == uiState.selectedPath,
                    onCheckedChange = { onSelectPath(path) },
                )
                Spacer(modifier = Modifier.height(LjSpacing.sm))
            }

            if (showsTapToWalk) {
                Spacer(modifier = Modifier.height(LjSpacing.xs))
                Text(
                    text = stringResource(R.string.onboarding_optional),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(LjSpacing.xs))
                LjGuidedStepCard(
                    title = stringResource(R.string.onboarding_tap_to_walk_access),
                    description = stringResource(R.string.onboarding_tap_to_walk_access_desc),
                    isGranted = uiState.screenServiceEnabled,
                    icon = LjIcons.Explore,
                    actionLabel = stringResource(R.string.onboarding_tap_to_walk_access_review),
                    onAction = { showAccessibilityDisclosure = true },
                )
            }

            Spacer(modifier = Modifier.height(LjSpacing.md))
            LjPrimaryButton(
                text = stringResource(R.string.onboarding_start_using_locationjoystick),
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showAccessibilityDisclosure) {
        OnboardingAccessibilityDisclosure(
            onClose = { showAccessibilityDisclosure = false },
            onAnswered = onAccessibilityDisclosureAnswered,
            onSkipped = onAccessibilityDisclosureSkipped,
        )
    }
}

/** Accessibility prominent disclosure with the onboarding answer handlers; shared by the permission and picker steps. */
@Composable
internal fun OnboardingAccessibilityDisclosure(
    onClose: () -> Unit,
    onAnswered: (accepted: Boolean) -> Unit,
    onSkipped: () -> Unit,
) {
    val context = LocalContext.current
    AccessibilityDisclosureDialog(
        onAccept = {
            onClose()
            onAnswered(true)
            // Below API 30 the service is disabled, so the settings screen has nothing to turn on.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        },
        onDecline = {
            onClose()
            onAnswered(false)
        },
        onSkip = {
            onClose()
            onSkipped()
        },
    )
}

private fun GuidedPath.titleRes(): Int =
    when (this) {
        GuidedPath.DEFAULT -> R.string.onboarding_path_default
        GuidedPath.POKEMON_GO -> R.string.onboarding_path_pokemon_go
        GuidedPath.PIKMIN_BLOOM -> R.string.onboarding_path_pikmin_bloom
    }

private fun GuidedPath.descriptionRes(): Int =
    when (this) {
        GuidedPath.DEFAULT -> R.string.onboarding_path_default_desc
        GuidedPath.POKEMON_GO -> R.string.onboarding_path_pokemon_go_desc
        GuidedPath.PIKMIN_BLOOM -> R.string.onboarding_path_pikmin_bloom_desc
    }

@Preview(showBackground = true)
@Composable
private fun PathPickerStepPreview() {
    LjTheme {
        PathPickerStep(
            uiState = OnboardingUiState(),
            onSelectPath = {},
            onAccessibilityDisclosureAnswered = {},
            onAccessibilityDisclosureSkipped = {},
            onFinish = {},
        )
    }
}
