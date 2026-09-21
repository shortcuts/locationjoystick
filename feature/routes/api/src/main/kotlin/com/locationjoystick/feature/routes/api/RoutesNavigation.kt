package com.locationjoystick.feature.routes.api

import androidx.navigation.NavController
import androidx.navigation.NavOptions

const val ROUTES_ROUTE = "routes"
const val ROUTE_DETAIL_ROUTE = "route_detail"
const val ROUTE_CREATOR_ROUTE = "route_creator"
const val ROUTE_PASTE_CREATOR_ROUTE = "route_paste"

fun NavController.navigateToRoutes(navOptions: NavOptions? = null) {
    navigate(ROUTES_ROUTE, navOptions)
}
