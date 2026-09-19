package com.locationjoystick.feature.settings.impl

import com.locationjoystick.core.data.CaptureCoordinatesRepository
import com.locationjoystick.core.testing.FakePreferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsCaptureViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var captureRepository: CaptureCoordinatesRepository
    private lateinit var viewModel: SettingsCaptureViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        captureRepository = CaptureCoordinatesRepository(FakePreferencesDataStore())
        viewModel = SettingsCaptureViewModel(captureRepository)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `rememberPreviousBrowser persists package`() =
        runTest {
            viewModel.rememberPreviousBrowser("com.android.chrome")
            assertEquals("com.android.chrome", captureRepository.previousBrowserPackage.first())
        }
}
