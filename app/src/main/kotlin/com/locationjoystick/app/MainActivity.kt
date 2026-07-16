package com.locationjoystick.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import com.locationjoystick.core.data.DeepLinkRepository
import com.locationjoystick.core.data.GoogleMapsShortLinkResolver
import com.locationjoystick.core.data.GroupRepository
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.model.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var deepLinkRepository: DeepLinkRepository

    @Inject lateinit var groupRepository: GroupRepository

    @Inject lateinit var shortLinkResolver: GoogleMapsShortLinkResolver

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
    private val deepLinkFailedMutableFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    internal val deepLinkFailedFlow = deepLinkFailedMutableFlow.asSharedFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

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
                    deepLinkFailedFlow = deepLinkFailedFlow,
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
        if (intent?.action == ACTION_MOVE_TO_BACK) {
            moveTaskToBack(true)
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
