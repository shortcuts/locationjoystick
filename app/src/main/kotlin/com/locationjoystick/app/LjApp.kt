package com.locationjoystick.app

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.rememberNavController
import com.locationjoystick.app.navigation.LjDrawerContent
import com.locationjoystick.app.navigation.LjNavHost
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.feature.favorites.api.FAVORITES_ROUTE
import com.locationjoystick.feature.map.api.MAP_ROUTE
import com.locationjoystick.feature.onboarding.api.ONBOARDING_ROUTE
import com.locationjoystick.feature.routes.api.ROUTES_ROUTE
import com.locationjoystick.feature.routes.api.ROUTE_CREATOR_ROUTE
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
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

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
    }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    val current = navController.currentDestination?.route
                    // Skip redirect for screens that may launch sub-activities (file pickers, etc.)
                    val skipRedirect =
                        current == IDLE_ROUTE || current == ONBOARDING_ROUTE || current == SETTINGS_ROUTE || current == ABOUT_ROUTE
                    if (!skipRedirect) {
                        navController.navigate(IDLE_ROUTE) {
                            popUpTo(IDLE_ROUTE) { inclusive = false }
                        }
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
}
