package com.locationjoystick.core.designsystem.component

import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.R
import org.junit.Assert.assertEquals
import org.junit.Test

class LjTopBarTest {
    @Test
    fun `back arrow announces navigate up`() {
        assertEquals(R.string.top_bar_navigate_up_cd, navigationIconDescriptionRes(LjIcons.ArrowBack))
    }

    @Test
    fun `menu icon announces open navigation menu`() {
        assertEquals(R.string.top_bar_open_navigation_menu_cd, navigationIconDescriptionRes(LjIcons.Menu))
    }
}
