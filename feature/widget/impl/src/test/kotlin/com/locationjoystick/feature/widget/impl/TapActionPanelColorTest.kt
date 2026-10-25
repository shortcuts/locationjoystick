package com.locationjoystick.feature.widget.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.locationjoystick.core.model.LatLng
import org.junit.Assert.assertEquals
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
}
