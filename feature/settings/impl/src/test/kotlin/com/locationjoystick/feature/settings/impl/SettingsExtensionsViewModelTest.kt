package com.locationjoystick.feature.settings.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.feature.settings.api.SettingsExtension
import org.junit.Assert.assertEquals
import org.junit.Test

private class StubExtension(
    override val id: String,
    override val isAvailable: Boolean,
) : SettingsExtension {
    override val icon: ImageVector = LjIcons.Link

    @Composable
    override fun title() = id

    @Composable
    override fun description() = id

    @Composable
    override fun Screen(onBack: () -> Unit) = Unit
}

class SettingsExtensionsViewModelTest {
    @Test
    fun `available extension is kept`() {
        val viewModel = SettingsExtensionsViewModel(setOf(StubExtension("a", true)))
        assertEquals(listOf("a"), viewModel.extensions.map { it.id })
    }

    @Test
    fun `unavailable extension is dropped`() {
        val viewModel = SettingsExtensionsViewModel(setOf(StubExtension("a", true), StubExtension("b", false)))
        assertEquals(listOf("a"), viewModel.extensions.map { it.id })
    }
}
