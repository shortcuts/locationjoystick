package com.locationjoystick.feature.widget.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.locationjoystick.core.data.CooldownState
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.TapToWalkMove
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TapActionPanelColorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun titleUsesThemeOnSurface() {
        val onSurface = Color(0xFF123456)
        composeRule.setContent {
            MaterialTheme(colorScheme = lightColorScheme(onSurface = onSurface)) {
                Box {
                    TapActionPanel(
                        tap = LatLng(0.0, 0.0),
                        isRouteReplay = false,
                        isEphemeralReplay = false,
                        isWalkActive = false,
                        walkStart = null,
                        walkTarget = null,
                        routeWaypoints = null,
                        cooldownForPosition = null,
                        onTeleport = {},
                        onWalkTo = {},
                        onWalkViaRoads = {},
                        onStopRouteAndTeleport = {},
                        onStopRouteAndWalkTo = {},
                        onFinishRouteAndWalkTo = {},
                        onAddEphemeralWaypoint = { _, _ -> },
                        onDismiss = {},
                        onShareOpened = {},
                    )
                }
            }
        }
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        val node = composeRule.onNodeWithText("Move to this location?")
        node
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsActions.GetTextLayoutResult)
            ?.action
            ?.invoke(results)
        assertEquals(
            onSurface,
            results
                .single()
                .layoutInput.style.color,
        )
    }

    @Test
    fun chooserShowsOnlyTheOfferedMoves() {
        composeRule.setContent { MaterialTheme { MoveButtons(listOf(TapToWalkMove.VIA_ROADS)) {} } }
        composeRule.onNodeWithText("Walk here via roads").assertExists()
        composeRule.onNodeWithText("Walk here").assertDoesNotExist()
        composeRule.onNodeWithText("Teleport here").assertDoesNotExist()
    }

    @Test
    fun moveButtonsDrawTeleportFirst() {
        composeRule.setContent {
            MaterialTheme {
                MoveButtons(listOf(TapToWalkMove.WALK, TapToWalkMove.VIA_ROADS, TapToWalkMove.TELEPORT)) {}
            }
        }
        val teleportTop =
            composeRule
                .onNodeWithText("Teleport here")
                .fetchSemanticsNode()
                .boundsInRoot.top
        val walkTop =
            composeRule
                .onNodeWithText("Walk here")
                .fetchSemanticsNode()
                .boundsInRoot.top
        assertTrue(teleportTop < walkTop)
    }

    @Test
    fun chosenMoveDismissesTheChooser() {
        val moves = mutableListOf<TapToWalkMove>()
        var dismissed = 0
        val tap = GroundTap(LatLng(0.0, 0.0), 0.0, 0.0)
        composeRule.setContent {
            MaterialTheme {
                Box {
                    TapMoveChooser(
                        chooser = PendingChoice(tap, TapDecision.Choose(LatLng(1.0, 1.0), listOf(TapToWalkMove.WALK))),
                        cooldownForPosition = { flowOf(CooldownState.Ready) },
                        onMove = { _, move -> moves += move },
                        onDismiss = { dismissed++ },
                    )
                }
            }
        }
        composeRule.onNodeWithText("Walk here").performClick()
        assertEquals(listOf(TapToWalkMove.WALK), moves)
        assertEquals(1, dismissed)
    }
}
