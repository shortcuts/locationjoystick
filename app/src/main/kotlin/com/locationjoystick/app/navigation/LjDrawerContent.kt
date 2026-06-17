package com.locationjoystick.app.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.locationjoystick.app.IDLE_ROUTE
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.feature.favorites.api.FAVORITES_ROUTE
import com.locationjoystick.feature.favorites.api.MAP_PICKER_ROUTE
import com.locationjoystick.feature.group.api.GROUP_ROUTE
import com.locationjoystick.feature.map.api.MAP_ROUTE
import com.locationjoystick.feature.routes.api.ROUTES_ROUTE
import com.locationjoystick.feature.settings.api.SETTINGS_ROUTE
import kotlinx.coroutines.launch

@Composable
fun LjDrawerContent(
    navController: NavHostController,
    drawerState: DrawerState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    ModalDrawerSheet(modifier = Modifier.widthIn(max = 320.dp).semantics { testTag = "nav_drawer" }) {
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
            icon = { Icon(LjIcons.Home, "Home") },
            label = { Text("Home") },
            selected = currentRoute == IDLE_ROUTE,
            onClick = {
                navController.navigate(IDLE_ROUTE) {
                    popUpTo(IDLE_ROUTE) { inclusive = true }
                    launchSingleTop = true
                }
                scope.launch { drawerState.close() }
            },
        )
        NavigationDrawerItem(
            icon = { Icon(LjIcons.LocationOn, "Map") },
            label = { Text("Map") },
            selected = currentRoute == MAP_ROUTE,
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
            selected = currentRoute != null && (currentRoute == ROUTES_ROUTE || currentRoute.startsWith("route_")),
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
            selected = currentRoute == FAVORITES_ROUTE || currentRoute == MAP_PICKER_ROUTE,
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
            icon = { Icon(LjIcons.Share, "Group Sync") },
            label = { Text("Group Sync") },
            selected = currentRoute == GROUP_ROUTE,
            onClick = {
                navController.navigate(GROUP_ROUTE) {
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
            selected = currentRoute == SETTINGS_ROUTE,
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
            icon = { Icon(LjIcons.Explore, "Website") },
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Website")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = LjIcons.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                }
            },
            selected = false,
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://locationjoystick.shrtcts.fr/")))
                scope.launch { drawerState.close() }
            },
        )
    }
}
