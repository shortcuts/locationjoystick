package com.locationjoystick.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.component.DestinationCardSpec
import com.locationjoystick.core.designsystem.component.DestinationHub
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.location.rememberSpoofToggleState

internal const val IDLE_ROUTE = "idle"

@Composable
internal fun IdleScreen(
    onOpenDrawer: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToRoutes: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToGroup: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
) {
    val spoofToggle = rememberSpoofToggleState()

    LjScaffold(
        title = "Home",
        isSpoofing = spoofToggle.isSpoofing,
        onToggleSpoofing = spoofToggle.onToggle,
        locationLabel = spoofToggle.locationLabel,
        onNavigationClick = onOpenDrawer,
        bottomBar = bottomBar,
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        DestinationHub(
            paddingValues = paddingValues,
            cards =
                listOf(
                    DestinationCardSpec(
                        LjIcons.Map,
                        "Map",
                        "Spoof your GPS location and control movement on the map.",
                        onNavigateToMap,
                    ),
                    DestinationCardSpec(LjIcons.Route, "Routes", "Replay saved routes.", onNavigateToRoutes),
                    DestinationCardSpec(
                        LjIcons.Favorite,
                        "Favorites",
                        "Teleport or walk to saved locations.",
                        onNavigateToFavorites,
                    ),
                    DestinationCardSpec(
                        LjIcons.Share,
                        "Group Sync",
                        "Mirror your location to other devices on the same Wi-Fi.",
                        onNavigateToGroup,
                    ),
                    DestinationCardSpec(
                        LjIcons.Settings,
                        "Settings",
                        "Configure locationjoystick and spoof preferences.",
                        onNavigateToSettings,
                    ),
                ),
        )
    }
}
