package com.locationjoystick.core.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.locationjoystick.core.model.GuidedPath
import com.locationjoystick.core.testing.FakePreferencesDataStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class LaunchAfterLinkUseCaseTest {
    private val pm = mockk<PackageManager>()
    private val context =
        mockk<Context>(relaxed = true) {
            every { packageName } returns "com.locationjoystick.app"
            every { packageManager } returns pm
        }
    private val intent = mockk<Intent>(relaxed = true)
    private lateinit var repository: CaptureCoordinatesRepository
    private val settings = SettingsRepository(FakeAppPreferencesDataSource())
    private lateinit var useCase: LaunchAfterLinkUseCase

    @Before
    fun setUp() {
        repository = CaptureCoordinatesRepository(FakePreferencesDataStore())
        useCase = LaunchAfterLinkUseCase(context, repository, settings)
        every { intent.addFlags(any()) } returns intent
        every { pm.getLaunchIntentForPackage("pogo") } returns intent
        every { pm.getLaunchIntentForPackage("gone") } returns null
        every { pm.getLaunchIntentForPackage("com.nianticlabs.pokemongo") } returns intent
    }

    @Test
    fun `launches picked app in a new task`() =
        runTest {
            repository.setLaunchAfterLinkPackage("pogo")
            useCase.launch()
            verify(exactly = 1) { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            verify(exactly = 1) { context.startActivity(intent) }
        }

    @Test
    fun `does nothing when unset own package or not launchable`() =
        runTest {
            useCase.launch()
            repository.setLaunchAfterLinkPackage("com.locationjoystick.app")
            useCase.launch()
            repository.setLaunchAfterLinkPackage("gone")
            useCase.launch()
            verify(exactly = 0) { context.startActivity(any()) }
        }

    @Test
    fun `swallows ActivityNotFoundException`() =
        runTest {
            repository.setLaunchAfterLinkPackage("pogo")
            every { context.startActivity(any()) } throws ActivityNotFoundException()
            useCase.launch()
        }

    @Test
    fun `unset follows the guided path game when installed`() =
        runTest {
            settings.setGuidedPath(GuidedPath.POKEMON_GO)
            useCase.launch()
            verify(exactly = 1) { context.startActivity(intent) }
        }

    @Test
    fun `explicit none launches nothing on a game path`() =
        runTest {
            settings.setGuidedPath(GuidedPath.POKEMON_GO)
            repository.setLaunchAfterLinkPackage(null)
            useCase.launch()
            verify(exactly = 0) { context.startActivity(any()) }
        }
}
