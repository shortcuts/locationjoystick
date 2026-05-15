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
import com.locationjoystick.feature.map.api.MAP_ROUTE
import com.locationjoystick.feature.settings.api.SETTINGS_ROUTE
import com.locationjoystick.feature.setup.api.SETUP_ROUTE
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

@Composable
fun LjApp(navigateToMapFlow: Flow<Unit> = emptyFlow()) {
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

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    val current = navController.currentDestination?.route
                    // Skip redirect for screens that may launch sub-activities (file pickers, etc.)
                    val skipRedirect = current == IDLE_ROUTE || current == SETUP_ROUTE || current == SETTINGS_ROUTE
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
