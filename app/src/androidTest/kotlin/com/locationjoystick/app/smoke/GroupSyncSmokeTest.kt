package com.locationjoystick.app.smoke

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class GroupSyncSmokeTest : BaseSmokeTest() {
    @get:Rule(order = -1)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Before
    override fun setup() {
        super.setup()
        composeRule.waitForIdleScreen()
        composeRule.navigateViaDrawer("Group Sync")
    }

    @Test
    fun group_sync_screen_loads() {
        composeRule
            .onNodeWithText(
                "Sync your fake location across multiple devices on the same Wi-Fi network. No account needed.",
            ).assertIsDisplayed()
    }

    @Test
    fun shows_create_and_join_options() {
        composeRule.onNodeWithText("Create group — I'm the leader").assertIsDisplayed()
        composeRule.onNodeWithText("Scan QR").assertIsDisplayed()
        composeRule.onNodeWithText("Enter code").assertIsDisplayed()
    }

    @Test
    fun enter_code_dialog_opens() {
        composeRule.onNodeWithText("Enter code").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Enter group code").assertIsDisplayed()
        composeRule.onNodeWithText("Code").assertIsDisplayed()
    }

    @Test
    fun enter_code_dialog_cancel_dismisses() {
        composeRule.onNodeWithText("Enter code").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Create group — I'm the leader").assertIsDisplayed()
    }

    @Test
    fun scan_qr_opens_scanner_screen_then_back() {
        composeRule.onNodeWithText("Scan QR").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Create group — I'm the leader").assertIsDisplayed()
    }
}
