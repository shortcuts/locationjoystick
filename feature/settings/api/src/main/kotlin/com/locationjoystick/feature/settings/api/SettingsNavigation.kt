package com.locationjoystick.feature.settings.api

import androidx.navigation.NavController
import androidx.navigation.NavOptions

const val SETTINGS_ROUTE = "settings"

fun NavController.navigateToSettings(navOptions: NavOptions? = null) {
    navigate(SETTINGS_ROUTE, navOptions)
}
