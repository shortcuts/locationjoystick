package com.locationjoystick.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Settings
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
import com.locationjoystick.app.IDLE_ROUTE
import com.locationjoystick.feature.favorites.api.FAVORITES_ROUTE
import com.locationjoystick.feature.map.api.MAP_ROUTE
import com.locationjoystick.feature.roaming.api.ROAMING_ROUTE
import com.locationjoystick.feature.routes.api.ROUTES_ROUTE
import com.locationjoystick.feature.settings.api.SETTINGS_ROUTE
import kotlinx.coroutines.launch

@Composable
fun LjDrawerContent(
    navController: NavHostController,
    drawerState: DrawerState,
) {
    val scope = rememberCoroutineScope()

    ModalDrawerSheet(modifier = Modifier.fillMaxWidth(0.5f)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = { scope.launch { drawerState.close() } }) {
                Icon(Icons.Rounded.Close, contentDescription = "Close menu")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        NavigationDrawerItem(
            icon = { Icon(Icons.Rounded.LocationOn, "Map") },
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
            icon = { Icon(Icons.Rounded.Route, "Routes") },
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
            icon = { Icon(Icons.Rounded.Favorite, "Favorites") },
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
            icon = { Icon(Icons.Rounded.Explore, "Roaming") },
            label = { Text("Roaming") },
            selected = false,
            onClick = {
                navController.navigate(ROAMING_ROUTE) {
                    popUpTo(IDLE_ROUTE) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
                scope.launch { drawerState.close() }
            },
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Rounded.Settings, "Settings") },
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
    }
}
