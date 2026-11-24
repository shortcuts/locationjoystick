package com.locationjoystick.feature.widget.impl

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.locationjoystick.core.model.ActiveMode
import com.locationjoystick.core.model.AppFeature
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.MovementState
import org.junit.Assert.assertEquals
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
        spoofingActive: Boolean = true,
        sections: List<WidgetPanelSection> = emptyList(),
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
                        movement = if (routePaused) MovementState.Paused(ActiveMode.WALK_TO) else MovementState.Running(ActiveMode.WALK_TO),
                        hideTeleportFeatures = false,
                        showRouteJumpButtons = false,
                        onIconClick = {},
                        onPauseResume = {},
                        onStop = {},
                        onJumpNext = {},
                        onJumpPrevious = {},
                    ),
                roamingControls = RoamingControlsState(false, MovementState.Manual(MockMode.TELEPORT), {}, {}, {}),
                isPanelExpanded = true,
                hasPendingCompletion = hasPendingCompletion,
                masterToggle = MasterToggleState(spoofingActive, {}),
                onFeatureClicked = {},
                pasteCapture = PasteCaptureState(false, {}, {}),
                sections = sections,
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

    @Test
    fun `stop and pause are shown while spoofing`() {
        var stops = 0
        setPanel(sections = listOf(WidgetPanelSection.PauseResume(false) {}, WidgetPanelSection.Stop { stops++ }))
        composeRule.onNodeWithContentDescription("Pause spoofing").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Stop spoofing").assertIsDisplayed().performClick()
        assertEquals(1, stops)
    }

    @Test
    fun `stop stays enabled while parked`() {
        var stops = 0
        setPanel(
            spoofingActive = false,
            sections = listOf(WidgetPanelSection.PauseResume(true) {}, WidgetPanelSection.Stop { stops++ }),
        )
        composeRule.onNodeWithContentDescription("Resume spoofing").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Stop spoofing").assertIsEnabled().performClick()
        assertEquals(1, stops)
    }
}
