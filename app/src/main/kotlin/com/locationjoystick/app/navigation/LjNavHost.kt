package com.locationjoystick.app.navigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.locationjoystick.app.IDLE_ROUTE
import com.locationjoystick.app.IdleScreen
import com.locationjoystick.core.common.util.isMockLocationEnabled
import com.locationjoystick.core.common.util.isOverlayPermissionGranted
import com.locationjoystick.feature.favorites.api.FAVORITES_ROUTE
import com.locationjoystick.feature.favorites.api.MAP_PICKER_ROUTE
import com.locationjoystick.feature.favorites.impl.FavoritesRoute
import com.locationjoystick.feature.favorites.impl.FavoritesViewModel
import com.locationjoystick.feature.favorites.impl.MapPickerRoute
import com.locationjoystick.feature.map.api.MAP_ROUTE
import com.locationjoystick.feature.map.impl.mapScreen
import com.locationjoystick.feature.onboarding.api.ONBOARDING_ROUTE
import com.locationjoystick.feature.onboarding.impl.OnboardingRoute
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

private const val ROUTES_GRAPH = "routes_graph"
private const val FAVORITES_GRAPH = "favorites_graph"

private fun fadeInScale(): EnterTransition =
    fadeIn(animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)) +
        scaleIn(
            initialScale = 0.95f,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
        )

private fun fadeOutScale(): ExitTransition =
    fadeOut(animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)) +
        scaleOut(
            targetScale = 0.95f,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
        )

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
            if (allPermissionsGranted(context)) IDLE_ROUTE else ONBOARDING_ROUTE
        }

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(
            route = ONBOARDING_ROUTE,
            enterTransition = { fadeInScale() },
            exitTransition = { fadeOutScale() },
            popEnterTransition = { fadeInScale() },
            popExitTransition = { fadeOutScale() },
        ) {
            OnboardingRoute(
                onSetupComplete = {
                    navController.navigate(IDLE_ROUTE) {
                        popUpTo(ONBOARDING_ROUTE) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = IDLE_ROUTE,
            enterTransition = { fadeInScale() },
            exitTransition = { fadeOutScale() },
            popEnterTransition = { fadeInScale() },
            popExitTransition = { fadeOutScale() },
        ) {
            IdleScreen(
                onOpenDrawer = onOpenDrawer,
                onNavigateToMap = {
                    navController.navigate(MAP_ROUTE) { launchSingleTop = true }
                },
                onNavigateToRoutes = {
                    navController.navigate(ROUTES_ROUTE) { launchSingleTop = true }
                },
                onNavigateToFavorites = {
                    navController.navigate(FAVORITES_ROUTE) { launchSingleTop = true }
                },
                onNavigateToRoaming = {
                    navController.navigate(ROAMING_ROUTE) { launchSingleTop = true }
                },
                onNavigateToSettings = {
                    navController.navigate(SETTINGS_ROUTE) { launchSingleTop = true }
                },
            )
        }

        mapScreen(onOpenDrawer = onOpenDrawer)

        navigation(startDestination = ROUTES_ROUTE, route = ROUTES_GRAPH) {
            composable(
                route = ROUTES_ROUTE,
                enterTransition = { fadeInScale() },
                exitTransition = { fadeOutScale() },
                popEnterTransition = { fadeInScale() },
                popExitTransition = { fadeOutScale() },
            ) {
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

            composable(
                route = "$ROUTE_CREATOR_ROUTE/{routeType}",
                enterTransition = { fadeInScale() },
                exitTransition = { fadeOutScale() },
                popEnterTransition = { fadeInScale() },
                popExitTransition = { fadeOutScale() },
            ) {
                RouteCreatorRoute(
                    onRouteSaved = { navController.navigateUp() },
                    onBack = { navController.navigateUp() },
                )
            }

            composable(
                route = "$ROUTE_DETAIL_ROUTE/{routeId}",
                enterTransition = { fadeInScale() },
                exitTransition = { fadeOutScale() },
                popEnterTransition = { fadeInScale() },
                popExitTransition = { fadeOutScale() },
            ) { backStackEntry ->
                val routeId = backStackEntry.arguments?.getString("routeId") ?: return@composable
                RouteDetailScreen(
                    routeId = routeId,
                    onNavigateBack = { navController.navigateUp() },
                    onOpenDrawer = onOpenDrawer,
                )
            }
        }

        navigation(startDestination = FAVORITES_ROUTE, route = FAVORITES_GRAPH) {
            composable(
                route = FAVORITES_ROUTE,
                enterTransition = { fadeInScale() },
                exitTransition = { fadeOutScale() },
                popEnterTransition = { fadeInScale() },
                popExitTransition = { fadeOutScale() },
            ) { backStackEntry ->
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

            composable(
                route = MAP_PICKER_ROUTE,
                enterTransition = { fadeInScale() },
                exitTransition = { fadeOutScale() },
                popEnterTransition = { fadeInScale() },
                popExitTransition = { fadeOutScale() },
            ) { backStackEntry ->
                val parentEntry =
                    remember(backStackEntry) {
                        navController.getBackStackEntry(FAVORITES_GRAPH)
                    }
                val favoritesViewModel: FavoritesViewModel = hiltViewModel(parentEntry)
                MapPickerRoute(
                    initialPosition = favoritesViewModel.currentPosition,
                    onLocationPicked = { name, lat, lon ->
                        favoritesViewModel.addFavorite(name, lat, lon)
                        navController.navigateUp()
                    },
                    onBack = { navController.navigateUp() },
                )
            }
        }

        composable(
            route = ROAMING_ROUTE,
            enterTransition = { fadeInScale() },
            exitTransition = { fadeOutScale() },
            popEnterTransition = { fadeInScale() },
            popExitTransition = { fadeOutScale() },
        ) {
            RoamingRoute(
                onOpenDrawer = onOpenDrawer,
                viewModel = hiltViewModel(),
            )
        }

        composable(
            route = SETTINGS_ROUTE,
            enterTransition = { fadeInScale() },
            exitTransition = { fadeOutScale() },
            popEnterTransition = { fadeInScale() },
            popExitTransition = { fadeOutScale() },
        ) {
            SettingsRoute(
                onOpenDrawer = onOpenDrawer,
                viewModel = hiltViewModel(),
            )
        }
    }
}
