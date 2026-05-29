package com.locationjoystick.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.locationjoystick.app.ABOUT_ROUTE
import com.locationjoystick.app.IDLE_ROUTE
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.feature.favorites.api.FAVORITES_ROUTE
import com.locationjoystick.feature.map.api.MAP_ROUTE
import com.locationjoystick.feature.routes.api.ROUTES_ROUTE
import com.locationjoystick.feature.settings.api.SETTINGS_ROUTE
import kotlinx.coroutines.launch

@Composable
fun LjDrawerContent(
    navController: NavHostController,
    drawerState: DrawerState,
) {
    val scope = rememberCoroutineScope()

    ModalDrawerSheet(modifier = Modifier.widthIn(max = 320.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = { scope.launch { drawerState.close() } }) {
                Icon(LjIcons.Close, contentDescription = "Close menu")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        NavigationDrawerItem(
            icon = { Icon(LjIcons.LocationOn, "Map") },
            label = { Text("Map") },
            selected = false,
            onClick = {
                navController.navigate(MAP_ROUTE) {
                    popUpTo(IDLE_ROUTE) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
                scope.launch { drawerState.close() }
            },
        )
        NavigationDrawerItem(
            icon = { Icon(LjIcons.Route, "Routes") },
            label = { Text("Routes") },
            selected = false,
            onClick = {
                navController.navigate(ROUTES_ROUTE) {
                    popUpTo(IDLE_ROUTE) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
                scope.launch { drawerState.close() }
            },
        )
        NavigationDrawerItem(
            icon = { Icon(LjIcons.Favorite, "Favorites") },
            label = { Text("Favorites") },
            selected = false,
            onClick = {
                navController.navigate(FAVORITES_ROUTE) {
                    popUpTo(IDLE_ROUTE) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
                scope.launch { drawerState.close() }
            },
        )
        NavigationDrawerItem(
            icon = { Icon(LjIcons.Settings, "Settings") },
            label = { Text("Settings") },
            selected = false,
            onClick = {
                navController.navigate(SETTINGS_ROUTE) {
                    popUpTo(IDLE_ROUTE) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
                scope.launch { drawerState.close() }
            },
        )
        NavigationDrawerItem(
            icon = { Icon(LjIcons.Info, "About") },
            label = { Text("About") },
            selected = false,
            onClick = {
                navController.navigate(ABOUT_ROUTE) {
                    popUpTo(IDLE_ROUTE) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
                scope.launch { drawerState.close() }
            },
        )
    }
}
