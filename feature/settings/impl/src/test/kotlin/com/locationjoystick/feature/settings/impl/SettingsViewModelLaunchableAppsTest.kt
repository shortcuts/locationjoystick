package com.locationjoystick.feature.settings.impl

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import com.locationjoystick.core.common.root.SensorPermissionBootstrap
import com.locationjoystick.core.common.util.NsdCodeManager
import com.locationjoystick.core.data.CaptureCoordinatesRepository
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.HotLocationsRepository
import com.locationjoystick.core.data.HotRoutesRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.testing.FakeFavoriteDao
import com.locationjoystick.core.testing.FakePreferencesDataStore
import com.locationjoystick.core.testing.FakeRouteDao
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsViewModelLaunchableAppsTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class ThreadRecordingIcon : Drawable() {
        @Volatile var drawThread: Thread? = null

        override fun draw(canvas: Canvas) {
            drawThread = Thread.currentThread()
        }

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Required override")
        override fun getOpacity() = PixelFormat.OPAQUE

        override fun getIntrinsicWidth() = 8

        override fun getIntrinsicHeight() = 8
    }

    @Test
    fun `launchableApps starts empty and decodes icons off the calling thread`() {
        val context = RuntimeEnvironment.getApplication()
        val pkg = "com.example.game"
        val icon = ThreadRecordingIcon()
        val pmShadow = shadowOf(context.packageManager)
        val appInfo = ApplicationInfo().apply { packageName = pkg }
        pmShadow.installPackage(
            PackageInfo().apply {
                packageName = pkg
                applicationInfo = appInfo
            },
        )
        pmShadow.setApplicationIcon(pkg, icon)
        val resolve =
            ResolveInfo().apply {
                activityInfo =
                    ActivityInfo().apply {
                        packageName = pkg
                        name = "Main"
                        applicationInfo = appInfo
                    }
            }
        pmShadow.addResolveInfoForIntent(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            resolve,
        )
        val viewModel =
            SettingsViewModel(
                settingsRepository = SettingsRepository(SaveTestPreferencesDataSource()),
                captureCoordinatesRepository = CaptureCoordinatesRepository(FakePreferencesDataStore()),
                favoriteRepository = FavoriteRepository(FakeFavoriteDao()),
                hotLocationsRepository = HotLocationsRepository(context),
                hotRoutesRepository = HotRoutesRepository(context),
                routeRepository = RouteRepository(routeDao = FakeRouteDao()),
                backupRepository = mockk(relaxed = true),
                sensorPermissionBootstrap = SensorPermissionBootstrap(context),
                importExportRepository = ImportExportRepository(context),
                exportSyncServer = ExportSyncServer(),
                exportSyncClient = ExportSyncClient(),
                nsdCodeManager = NsdCodeManager(context),
                context = context,
            )

        assertEquals(emptyList<InstalledApp>(), viewModel.launchableApps.value)
        val apps = runBlocking { withTimeout(10_000) { viewModel.launchableApps.first { it.isNotEmpty() } } }

        assertEquals(listOf(pkg), apps.map { it.packageName })
        assertNotEquals(Thread.currentThread(), icon.drawThread)
    }
}
