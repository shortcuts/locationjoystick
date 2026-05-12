package com.locationjoystick.app

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.rememberNavController
import com.locationjoystick.app.navigation.LjDrawerContent
import com.locationjoystick.app.navigation.LjNavHost
import kotlinx.coroutines.launch

@Composable
fun LjApp() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

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
