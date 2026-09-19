package com.locationjoystick.feature.map.api

import androidx.navigation.NavController
import androidx.navigation.NavOptions

const val MAP_ROUTE = "map"
const val CAPTURE_ROUTE = "capture"

fun NavController.navigateToMap(navOptions: NavOptions? = null) {
    navigate(MAP_ROUTE, navOptions)
}
