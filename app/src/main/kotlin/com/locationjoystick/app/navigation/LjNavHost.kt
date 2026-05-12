package com.locationjoystick.app.navigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.locationjoystick.core.common.util.isMockLocationEnabled
import com.locationjoystick.core.common.util.isOverlayPermissionGranted
import com.locationjoystick.feature.favorites.api.FAVORITES_ROUTE
import com.locationjoystick.feature.favorites.api.MAP_PICKER_ROUTE
import com.locationjoystick.feature.favorites.impl.FavoritesRoute
import com.locationjoystick.feature.favorites.impl.FavoritesViewModel
import com.locationjoystick.feature.favorites.impl.MapPickerRoute
import com.locationjoystick.feature.map.api.MAP_ROUTE
import com.locationjoystick.feature.map.impl.mapScreen
import com.locationjoystick.feature.roaming.api.ROAMING_ROUTE
import com.locationjoystick.feature.roaming.impl.RoamingRoute
import com.locationjoystick.feature.routes.api.ROUTES_ROUTE
import com.locationjoystick.feature.routes.api.ROUTE_CREATOR_ROUTE
import com.locationjoystick.feature.routes.api.ROUTE_DETAIL_ROUTE
import com.locationjoystick.feature.routes.impl.RouteCreatorRoute
import com.locationjoystick.feature.routes.impl.RouteDetailScreen
import com.locationjoystick.feature.routes.impl.RoutesRoute
import com.locationjoystick.feature.settings.api.SETTINGS_ROUTE
import com.locationjoystick.feature.settings.impl.SettingsRoute
import com.locationjoystick.feature.setup.api.SETUP_ROUTE
import com.locationjoystick.feature.setup.impl.SetupRoute

private const val ROUTES_GRAPH = "routes_graph"
private const val FAVORITES_GRAPH = "favorites_graph"

private fun allPermissionsGranted(context: Context): Boolean {
    val locationGranted =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    return locationGranted && isOverlayPermissionGranted(context) && isMockLocationEnabled(context)
}

@Composable
fun LjNavHost(
    navController: NavHostController,
    onOpenDrawer: () -> Unit,
) {
    val context = LocalContext.current
    val startDestination =
        remember {
            if (allPermissionsGranted(context)) MAP_ROUTE else SETUP_ROUTE
        }

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(SETUP_ROUTE) {
            SetupRoute(
                onSetupComplete = {
                    navController.navigate(MAP_ROUTE) {
                        popUpTo(SETUP_ROUTE) { inclusive = true }
                    }
                },
            )
        }

        mapScreen(onOpenDrawer = onOpenDrawer)

        navigation(startDestination = ROUTES_ROUTE, route = ROUTES_GRAPH) {
            composable(ROUTES_ROUTE) {
                RoutesRoute(
                    onNavigateToDetail = { routeId ->
                        navController.navigate("$ROUTE_DETAIL_ROUTE/$routeId")
                    },
                    onNavigateToCreate = { routeType ->
                        navController.navigate("$ROUTE_CREATOR_ROUTE/${routeType.name}")
                    },
                    onImportGpx = {},
                    onOpenDrawer = onOpenDrawer,
                    viewModel = hiltViewModel(),
                )
            }

            composable("$ROUTE_CREATOR_ROUTE/{routeType}") {
                RouteCreatorRoute(
                    onRouteSaved = { navController.navigateUp() },
                    onBack = { navController.navigateUp() },
                )
            }

            composable("$ROUTE_DETAIL_ROUTE/{routeId}") { backStackEntry ->
                val routeId = backStackEntry.arguments?.getString("routeId") ?: return@composable
                RouteDetailScreen(
                    routeId = routeId,
                    onNavigateBack = { navController.navigateUp() },
                )
            }
        }

        navigation(startDestination = FAVORITES_ROUTE, route = FAVORITES_GRAPH) {
            composable(FAVORITES_ROUTE) { backStackEntry ->
                val parentEntry =
                    remember(backStackEntry) {
                        navController.getBackStackEntry(FAVORITES_GRAPH)
                    }
                val favoritesViewModel: FavoritesViewModel = hiltViewModel(parentEntry)
                FavoritesRoute(
                    viewModel = favoritesViewModel,
                    onNavigateToMapPicker = { navController.navigate(MAP_PICKER_ROUTE) },
                    onOpenDrawer = onOpenDrawer,
                )
            }

            composable(MAP_PICKER_ROUTE) { backStackEntry ->
                val parentEntry =
                    remember(backStackEntry) {
                        navController.getBackStackEntry(FAVORITES_GRAPH)
                    }
                val favoritesViewModel: FavoritesViewModel = hiltViewModel(parentEntry)
                MapPickerRoute(
                    onLocationPicked = { name, lat, lon ->
                        favoritesViewModel.addFavorite(name, lat, lon)
                        navController.navigateUp()
                    },
                    onBack = { navController.navigateUp() },
                )
            }
        }

        composable(ROAMING_ROUTE) {
            RoamingRoute(
                onOpenDrawer = onOpenDrawer,
                viewModel = hiltViewModel(),
            )
        }

        composable(SETTINGS_ROUTE) {
            SettingsRoute(
                onOpenDrawer = onOpenDrawer,
                viewModel = hiltViewModel(),
            )
        }
    }
}
