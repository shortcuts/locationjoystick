package com.locationjoystick.feature.favorites.api

import androidx.navigation.NavController
import androidx.navigation.NavOptions

const val FAVORITES_ROUTE = "favorites"
const val MAP_PICKER_ROUTE = "map_picker"

fun NavController.navigateToFavorites(navOptions: NavOptions? = null) {
    navigate(FAVORITES_ROUTE, navOptions)
}

fun NavController.navigateToMapPicker(navOptions: NavOptions? = null) {
    navigate(MAP_PICKER_ROUTE, navOptions)
}
