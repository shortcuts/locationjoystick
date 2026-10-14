package com.locationjoystick.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class LjShapesTest {
    @Test
    fun `quiet utility radii`() {
        assertEquals(RoundedCornerShape(8.dp), LjShapes.small)
        assertEquals(RoundedCornerShape(16.dp), LjShapes.large)
    }
}
