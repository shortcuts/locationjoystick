package com.locationjoystick.feature.widget.impl

import androidx.compose.ui.semantics.SemanticsActions
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
        features: List<AppFeature> = listOf(AppFeature.ROUTES),
        onFeatureClicked: (AppFeature) -> Unit = {},
    ) {
        composeRule.setContent {
            WidgetPanel(
                features = features,
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
                onFeatureClicked = onFeatureClicked,
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
    fun `pause is shown while spoofing`() {
        setPanel(sections = listOf(WidgetPanelSection.PauseResume(false) {}))
        composeRule.onNodeWithContentDescription("Pause spoofing").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Stop spoofing").assertDoesNotExist()
    }

    @Test
    fun `resume stays enabled while parked`() {
        var clicks = 0
        setPanel(spoofingActive = false, sections = listOf(WidgetPanelSection.PauseResume(true) { clicks++ }))
        composeRule.onNodeWithContentDescription("Resume spoofing").assertIsEnabled().performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `capture button opens capture`() {
        val clicked = mutableListOf<AppFeature>()
        setPanel(features = listOf(AppFeature.CAPTURE_COORDINATES), onFeatureClicked = { clicked += it })
        composeRule.onNodeWithContentDescription("Open capture").assertIsDisplayed().performClick()
        assertEquals(listOf(AppFeature.CAPTURE_COORDINATES), clicked)
    }

    @Test
    fun `paste button has no long click`() {
        setPanel(features = listOf(AppFeature.PASTE_COORDINATES))
        composeRule
            .onNodeWithContentDescription("Paste coordinates")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
    }
}
