package com.locationjoystick.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.component.AppIcon
import com.locationjoystick.core.designsystem.component.LjScaffold

internal const val IDLE_ROUTE = "idle"

@Composable
internal fun IdleScreen(
    onOpenDrawer: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToRoutes: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToInfo: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    val isWide = LocalConfiguration.current.screenWidthDp >= 600

    LjScaffold(
        title = "Lj",
        onNavigationClick = onOpenDrawer,
        bottomBar = bottomBar,
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        if (isWide) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 32.dp),
                    ) {
                        AppIcon()
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "locationjoystick",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                item {
                    IdleDestinationCard(
                        LjIcons.Map,
                        "Map",
                        "Spoof your GPS location and control movement on the map.",
                        onNavigateToMap,
                    )
                }
                item { IdleDestinationCard(LjIcons.Route, "Routes", "Replay saved routes.", onNavigateToRoutes) }
                item { IdleDestinationCard(LjIcons.Favorite, "Favorites", "Teleport or walk to saved locations.", onNavigateToFavorites) }
                item {
                    IdleDestinationCard(
                        LjIcons.Settings,
                        "Settings",
                        "Configure locationjoystick and spoof preferences.",
                        onNavigateToSettings,
                    )
                }
                item { IdleDestinationCard(LjIcons.Info, "About", "App info, credits, and license.", onNavigateToInfo) }
            }
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(40.dp))

                AppIcon()

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "locationjoystick",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(32.dp))

                IdleDestinationCard(
                    icon = LjIcons.Map,
                    title = "Map",
                    description = "Spoof your GPS location and control movement on the map.",
                    onClick = onNavigateToMap,
                )
                Spacer(modifier = Modifier.height(12.dp))
                IdleDestinationCard(
                    icon = LjIcons.Route,
                    title = "Routes",
                    description = "Replay saved routes.",
                    onClick = onNavigateToRoutes,
                )
                Spacer(modifier = Modifier.height(12.dp))
                IdleDestinationCard(
                    icon = LjIcons.Favorite,
                    title = "Favorites",
                    description = "Teleport or walk to saved locations.",
                    onClick = onNavigateToFavorites,
                )
                Spacer(modifier = Modifier.height(12.dp))
                IdleDestinationCard(
                    icon = LjIcons.Settings,
                    title = "Settings",
                    description = "Configure locationjoystick and spoof preferences.",
                    onClick = onNavigateToSettings,
                )
                Spacer(modifier = Modifier.height(12.dp))
                IdleDestinationCard(
                    icon = LjIcons.Info,
                    title = "About",
                    description = "App info, credits, and license.",
                    onClick = onNavigateToInfo,
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun IdleDestinationCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
