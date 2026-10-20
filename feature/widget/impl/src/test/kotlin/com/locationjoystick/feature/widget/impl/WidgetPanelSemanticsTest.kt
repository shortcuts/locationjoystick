package com.locationjoystick.feature.widget.impl

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.locationjoystick.core.model.AppFeature
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WidgetPanelSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setPanel(
        routePaused: Boolean = false,
        hasPendingCompletion: Boolean = false,
    ) {
        composeRule.setContent {
            WidgetPanel(
                features = listOf(AppFeature.ROUTES),
                joystickLocked = false,
                joystickMoving = false,
                activeProfileId = "",
                routeControls =
                    RouteControlsState(
                        expanded = false,
                        isActive = true,
                        isPaused = routePaused,
                        isPausable = true,
                        isReplay = false,
                        hideTeleportFeatures = false,
                        showRouteJumpButtons = false,
                        onIconClick = {},
                        onPauseResume = {},
                        onStop = {},
                        onJumpNext = {},
                        onJumpPrevious = {},
                    ),
                roamingControls = RoamingControlsState(false, false, false, {}, {}, {}),
                isPanelExpanded = true,
                hasPendingCompletion = hasPendingCompletion,
                masterToggle = MasterToggleState(true, false, {}, {}, {}, {}, {}),
                onFeatureClicked = {},
                pasteCapture = PasteCaptureState(false, {}, {}),
                sections = emptyList(),
                onDrag = { _, _ -> },
            )
        }
    }

    @Test
    fun `route icon exposes paused stateDescription`() {
        setPanel(routePaused = true)
        composeRule
            .onNodeWithContentDescription("Routes picker")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Paused"))
    }

    @Test
    fun `pending completion dot has a description`() {
        setPanel(hasPendingCompletion = true)
        composeRule.onNodeWithContentDescription("Route finished").assertIsDisplayed()
    }
}
