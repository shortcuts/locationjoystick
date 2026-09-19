package com.locationjoystick.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.locationjoystick.app.R
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
    onNavigateToCapture: () -> Unit = {},
    onNavigateToSettings: () -> Unit,
    onNavigateToGroup: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
) {
    val spoofToggle = rememberSpoofToggleState()

    LjScaffold(
        title = stringResource(R.string.drawer_home),
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
                        stringResource(R.string.drawer_map),
                        stringResource(R.string.idle_map_description),
                        onNavigateToMap,
                    ),
                    DestinationCardSpec(
                        LjIcons.Route,
                        stringResource(R.string.drawer_routes),
                        stringResource(R.string.idle_routes_description),
                        onNavigateToRoutes,
                    ),
                    DestinationCardSpec(
                        LjIcons.Favorite,
                        stringResource(R.string.drawer_favorites),
                        stringResource(R.string.idle_favorites_description),
                        onNavigateToFavorites,
                    ),
                    DestinationCardSpec(
                        LjIcons.AddLocationAlt,
                        stringResource(R.string.drawer_capture),
                        stringResource(R.string.idle_capture_description),
                        onNavigateToCapture,
                    ),
                    DestinationCardSpec(
                        LjIcons.Share,
                        stringResource(R.string.drawer_group_sync),
                        stringResource(R.string.idle_group_sync_description),
                        onNavigateToGroup,
                    ),
                    DestinationCardSpec(
                        LjIcons.Settings,
                        stringResource(R.string.drawer_settings),
                        stringResource(R.string.idle_settings_description),
                        onNavigateToSettings,
                    ),
                ),
        )
    }
}
