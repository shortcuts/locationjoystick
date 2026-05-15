package com.locationjoystick.app

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.rememberNavController
import com.locationjoystick.app.navigation.LjDrawerContent
import com.locationjoystick.app.navigation.LjNavHost
import com.locationjoystick.feature.map.api.MAP_ROUTE
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

@Composable
fun LjApp(navigateToMapFlow: Flow<Unit> = emptyFlow()) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(navController) {
        navigateToMapFlow.collect {
            drawerState.close()
            navController.navigate(MAP_ROUTE) {
                popUpTo(MAP_ROUTE) { inclusive = false }
                launchSingleTop = true
            }
        }
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
