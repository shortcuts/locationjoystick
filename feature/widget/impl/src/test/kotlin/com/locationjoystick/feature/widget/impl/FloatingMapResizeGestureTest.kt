package com.locationjoystick.feature.widget.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FloatingMapResizeGestureTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setHandle(events: MutableList<String>) {
        composeRule.setContent {
            Box(
                Modifier
                    .size(100.dp)
                    .testTag("handle")
                    .floatingMapResizeGesture(
                        onResizeStart = { _, _ -> events += "start" },
                        onResize = { _, _ -> events += "resize" },
                        onResizeEnd = { events += "end" },
                    ),
            )
        }
    }

    @Test
    fun `drag resizes without a long press`() {
        val events = mutableListOf<String>()
        setHandle(events)
        composeRule.onNodeWithTag("handle").performTouchInput {
            down(center)
            moveBy(
                androidx.compose.ui.geometry
                    .Offset(-40f, 40f),
            )
            up()
        }
        assertEquals(listOf("start", "resize", "end"), events)
    }
}
