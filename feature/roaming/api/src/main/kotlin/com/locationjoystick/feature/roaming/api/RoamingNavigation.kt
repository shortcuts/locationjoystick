package com.locationjoystick.feature.roaming.api

import androidx.navigation.NavController
import androidx.navigation.NavOptions

const val ROAMING_ROUTE = "roaming"

fun NavController.navigateToRoaming(navOptions: NavOptions? = null) {
    navigate(ROAMING_ROUTE, navOptions)
}
