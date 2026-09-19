package com.locationjoystick.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.LocaleContextWrapper
import com.locationjoystick.core.common.util.loadGpxForOpen
import com.locationjoystick.core.data.DeepLinkRepository
import com.locationjoystick.core.data.GoogleMapsShortLinkResolver
import com.locationjoystick.core.data.GpxOpenRepository
import com.locationjoystick.core.data.GroupRepository
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.model.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "MainActivity"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var deepLinkRepository: DeepLinkRepository

    @Inject lateinit var groupRepository: GroupRepository

    @Inject lateinit var shortLinkResolver: GoogleMapsShortLinkResolver

    @Inject lateinit var gpxOpenRepository: GpxOpenRepository

    companion object {
        const val ACTION_MOVE_TO_BACK = "com.locationjoystick.app.ACTION_MOVE_TO_BACK"
    }

    private val navigateToMapMutableFlow = MutableSharedFlow<Unit>(replay = 1)
    internal val navigateToMapFlow = navigateToMapMutableFlow.asSharedFlow()
    private val navigateToRouteCreatorMutableFlow = MutableSharedFlow<Unit>(replay = 1)
    internal val navigateToRouteCreatorFlow = navigateToRouteCreatorMutableFlow.asSharedFlow()
    private val navigateToFavoritesMutableFlow = MutableSharedFlow<Unit>(replay = 1)
    internal val navigateToFavoritesFlow = navigateToFavoritesMutableFlow.asSharedFlow()
    private val navigateToRoutesMutableFlow = MutableSharedFlow<Unit>(replay = 1)
    internal val navigateToRoutesFlow = navigateToRoutesMutableFlow.asSharedFlow()
    private val navigateToCaptureMutableFlow = MutableSharedFlow<Unit>(replay = 1)
    internal val navigateToCaptureFlow = navigateToCaptureMutableFlow.asSharedFlow()
    private val deepLinkFailedMutableFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    internal val deepLinkFailedFlow = deepLinkFailedMutableFlow.asSharedFlow()
    private val gpxOpenFailedMutableFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    internal val gpxOpenFailedFlow = gpxOpenFailedMutableFlow.asSharedFlow()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleContextWrapper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                0,
            )
        }

        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            LjTheme(darkTheme = themeMode == ThemeMode.DARK) {
                LjApp(
                    navigateToMapFlow = navigateToMapFlow,
                    navigateToRouteCreatorFlow = navigateToRouteCreatorFlow,
                    navigateToFavoritesFlow = navigateToFavoritesFlow,
                    navigateToRoutesFlow = navigateToRoutesFlow,
                    navigateToCaptureFlow = navigateToCaptureFlow,
                    deepLinkFailedFlow = deepLinkFailedFlow,
                    gpxOpenFailedFlow = gpxOpenFailedFlow,
                )
            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    internal fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(AppConstants.ServiceConstants.EXTRA_NAVIGATE_TO_MAP, false) == true) {
            navigateToMapMutableFlow.tryEmit(Unit)
        }
        if (intent?.getBooleanExtra(AppConstants.ServiceConstants.EXTRA_NAVIGATE_TO_ROUTE_CREATOR, false) == true) {
            navigateToRouteCreatorMutableFlow.tryEmit(Unit)
        }
        if (intent?.getBooleanExtra(AppConstants.ServiceConstants.EXTRA_NAVIGATE_TO_FAVORITES, false) == true) {
            navigateToFavoritesMutableFlow.tryEmit(Unit)
        }
        if (intent?.getBooleanExtra(AppConstants.ServiceConstants.EXTRA_NAVIGATE_TO_ROUTES, false) == true) {
            navigateToRoutesMutableFlow.tryEmit(Unit)
        }
        if (intent?.getBooleanExtra(AppConstants.ServiceConstants.EXTRA_NAVIGATE_TO_CAPTURE, false) == true) {
            navigateToCaptureMutableFlow.tryEmit(Unit)
        }
        if (intent?.action == ACTION_MOVE_TO_BACK) {
            moveTaskToBack(true)
        }
        if (intent != null && shouldTryOpenAsGpx(intent)) {
            handleGpxIntent(intent)
            return
        }
        if (intent?.action == Intent.ACTION_VIEW) {
            val groupInvite = parseGroupInvite(intent)
            if (groupInvite != null) {
                groupRepository.setPendingGroupInvite(groupInvite)
            } else {
                deliverCoords(parseDeepLinkCoords(intent))
            }
        }
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            val url = sharedText?.let(::extractUrlFromText)
            if (url != null) {
                handleSharedUrl(url)
            } else if (sharedText != null) {
                deepLinkFailedMutableFlow.tryEmit(Unit)
            }
        }
    }

    private fun handleGpxIntent(intent: Intent) {
        val uri = gpxUriFromIntent(intent)
        if (uri == null) {
            gpxOpenFailedMutableFlow.tryEmit(Unit)
            return
        }
        lifecycleScope.launch {
            try {
                val (content, displayName) =
                    withContext(Dispatchers.IO) {
                        readGpxContent(uri) to queryDisplayName(uri)
                    }
                val route = loadGpxForOpen(content, displayName)
                gpxOpenRepository.setPending(route.waypoints, route.name)
                navigateToMapMutableFlow.tryEmit(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "GPX open failed", e)
                gpxOpenFailedMutableFlow.tryEmit(Unit)
            }
        }
    }

    private fun queryDisplayName(uri: android.net.Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment
    }

    private fun readGpxContent(uri: android.net.Uri): String {
        val descriptor = contentResolver.openAssetFileDescriptor(uri, "r")
        val fileSize = descriptor?.use { it.length }
        if (fileSize != null && fileSize > AppConstants.ExportConstants.MAX_GPX_IMPORT_SIZE_BYTES) {
            throw IllegalArgumentException(
                "GPX file is too large (${fileSize / 1024 / 1024} MB). Maximum allowed is " +
                    "${AppConstants.ExportConstants.MAX_GPX_IMPORT_SIZE_BYTES / 1024 / 1024} MB.",
            )
        }
        return contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().readText()
        } ?: throw IllegalArgumentException("Cannot read GPX file")
    }

    private fun handleSharedUrl(url: String) {
        lifecycleScope.launch {
            val resolvedUrl = if (shortLinkResolver.isShortLink(url)) shortLinkResolver.resolve(url) ?: url else url
            deliverCoords(parseUrlCoords(resolvedUrl))
        }
    }

    private fun deliverCoords(coords: Pair<Double, Double>?) {
        if (coords != null) {
            deepLinkRepository.setPendingCoords(coords.first, coords.second)
            navigateToMapMutableFlow.tryEmit(Unit)
        } else {
            deepLinkFailedMutableFlow.tryEmit(Unit)
        }
    }
}
