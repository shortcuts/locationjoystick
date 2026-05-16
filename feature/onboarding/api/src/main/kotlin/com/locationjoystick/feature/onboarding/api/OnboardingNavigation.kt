package com.locationjoystick.feature.onboarding.api

import androidx.navigation.NavController
import androidx.navigation.NavOptions

const val ONBOARDING_ROUTE = "onboarding"

fun NavController.navigateToOnboarding(navOptions: NavOptions? = null) {
    navigate(ONBOARDING_ROUTE, navOptions)
}
