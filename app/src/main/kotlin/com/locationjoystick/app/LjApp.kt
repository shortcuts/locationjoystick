package com.locationjoystick.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.locationjoystick.app.R
import com.locationjoystick.app.navigation.LjDrawerContent
import com.locationjoystick.app.navigation.LjNavHost
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.feature.favorites.api.FAVORITES_ROUTE
import com.locationjoystick.feature.map.api.CAPTURE_ROUTE
import com.locationjoystick.feature.map.api.MAP_ROUTE
import com.locationjoystick.feature.onboarding.api.ONBOARDING_ROUTE
import com.locationjoystick.feature.routes.api.ROUTES_ROUTE
import com.locationjoystick.feature.routes.api.ROUTE_CREATOR_ROUTE
import com.locationjoystick.feature.routes.api.ROUTE_PASTE_CREATOR_ROUTE
import com.locationjoystick.feature.settings.api.SETTINGS_ROUTE
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

@Composable
fun LjApp(
    navigateToMapFlow: Flow<Unit> = emptyFlow(),
    navigateToRouteCreatorFlow: Flow<Unit> = emptyFlow(),
    navigateToFavoritesFlow: Flow<Unit> = emptyFlow(),
    navigateToRoutesFlow: Flow<Unit> = emptyFlow(),
    navigateToCaptureFlow: Flow<Unit> = emptyFlow(),
    deepLinkFailedFlow: Flow<Unit> = emptyFlow(),
    gpxOpenFailedFlow: Flow<Unit> = emptyFlow(),
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val couldNotOpenLinkMessage = stringResource(R.string.app_couldn_t_open_that_link)
    val couldNotOpenGpxMessage = stringResource(R.string.app_gpx_open_failed)

    LaunchedEffect(Unit) {
        deepLinkFailedFlow.collect {
            snackbarHostState.showSnackbar(couldNotOpenLinkMessage)
        }
    }

    LaunchedEffect(gpxOpenFailedFlow, couldNotOpenGpxMessage) {
        gpxOpenFailedFlow.collect {
            snackbarHostState.showSnackbar(couldNotOpenGpxMessage)
        }
    }

    LaunchedEffect(navController) {
        navigateToMapFlow.collect {
            drawerState.close()
            navController.navigate(MAP_ROUTE) {
                popUpTo(MAP_ROUTE) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(navController) {
        navigateToRouteCreatorFlow.collect {
            drawerState.close()
            navController.navigate("$ROUTE_CREATOR_ROUTE/${RouteType.STRAIGHT.name}") {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(navController) {
        launch {
            navigateToFavoritesFlow.collect {
                drawerState.close()
                navController.navigate(FAVORITES_ROUTE) {
                    launchSingleTop = true
                    popUpTo(IDLE_ROUTE) { saveState = true }
                    restoreState = true
                }
            }
        }
        launch {
            navigateToRoutesFlow.collect {
                drawerState.close()
                navController.navigate(ROUTES_ROUTE) {
                    launchSingleTop = true
                    popUpTo(IDLE_ROUTE) { saveState = true }
                    restoreState = true
                }
            }
        }
        launch {
            navigateToCaptureFlow.collect {
                drawerState.close()
                navController.navigate(CAPTURE_ROUTE) {
                    launchSingleTop = true
                    popUpTo(IDLE_ROUTE) { saveState = true }
                    restoreState = true
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    val current = navController.currentDestination?.route
                    if (!shouldSkipIdleRedirect(current)) {
                        navController.navigate(IDLE_ROUTE) {
                            popUpTo(IDLE_ROUTE) { inclusive = false }
                        }
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = false,
            drawerContent = {
                LjDrawerContent(
                    navController = navController,
                    drawerState = drawerState,
                )
            },
        ) {
            LjNavHost(
                navController = navController,
                onOpenDrawer = { scope.launch { drawerState.open() } },
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        val currentRoute =
            navController
                .currentBackStackEntryAsState()
                .value
                ?.destination
                ?.route
        if (currentRoute == IDLE_ROUTE) {
            WhatsNewPopup(
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            )
            UpdateAvailablePopup(
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

/**
 * Destinations that must stay on screen when the activity stops (app switch, recents).
 *
 * Idle/onboarding are already the hub. Settings launches SAF file pickers. Paste coordinates
 * (routes), Favorites (paste/from-coordinates sheets), and Capture (Android default-app
 * settings) are in-progress editors: users leave the app then return. Other MapLibre-heavy
 * screens still dump to Idle so the map is unloaded while backgrounded.
 */
internal fun shouldSkipIdleRedirect(route: String?): Boolean =
    route == IDLE_ROUTE ||
        route == ONBOARDING_ROUTE ||
        route == SETTINGS_ROUTE ||
        route == ROUTE_PASTE_CREATOR_ROUTE ||
        route == FAVORITES_ROUTE ||
        route == CAPTURE_ROUTE
