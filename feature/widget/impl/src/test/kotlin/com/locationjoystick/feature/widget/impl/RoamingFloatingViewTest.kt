package com.locationjoystick.feature.widget.impl

import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MapTileSource
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.SpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RoamingFloatingViewTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `start passes the generated path to onStart`() {
        val path = listOf(LatLng(1.0, 1.0), LatLng(1.1, 1.1))
        var generated = false
        var started: List<LatLng>? = null
        composeRule.setContent {
            RoamingFloatingView(
                draft = RoamingDefaults(),
                speedUnit = SpeedUnit.KMH,
                currentPosition = LatLng(1.0, 1.0),
                tileSource = MapTileSource.DEFAULT,
                hasCurrentPosition = true,
                isSpoofingActive = true,
                onDismiss = {},
                onDraftChange = {},
                onGenerate = {
                    generated = true
                    path
                },
                previewMap = { Text("map") },
                onStart = { _, planned -> started = planned },
            )
        }
        composeRule.onNodeWithText("Generate").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("map").assertIsDisplayed()
        composeRule.onNodeWithText("Start").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(path, started)
    }
}
