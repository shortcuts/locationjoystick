package com.locationjoystick.core.model

/**
 * App to open after a map link. [chosen] separates an explicit "None" from an unset choice,
 * so only an unset choice follows the guided path.
 */
fun effectiveLaunchPackage(
    stored: String?,
    chosen: Boolean,
    path: GuidedPath,
    isInstalled: (String) -> Boolean,
): String? = if (chosen) stored else path.gamePackage?.takeIf(isInstalled)
