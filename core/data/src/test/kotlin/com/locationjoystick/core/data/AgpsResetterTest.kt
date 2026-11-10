package com.locationjoystick.core.data

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AgpsResetterTest {
    private val settings = SettingsRepository(FakeAppPreferencesDataSource())
    private val real = mockk<RealLocationRepository>()
    private val resetter = AgpsResetter(settings, real)

    @Test
    fun `setting off never resets`() =
        runTest {
            resetter.resetIfEnabled()

            verify(exactly = 0) { real.resetAidingData() }
        }

    @Test
    fun `setting on resets once`() =
        runTest {
            settings.setAgpsResetOnStartEnabled(true)
            every { real.resetAidingData() } returns true

            resetter.resetIfEnabled()

            verify(exactly = 1) { real.resetAidingData() }
        }
}
