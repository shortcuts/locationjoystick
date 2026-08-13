package com.locationjoystick.core.designsystem.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.designsystem.LjError
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LjTopBar(
    title: String,
    isSpoofing: Boolean,
    onToggleSpoofing: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigationClick: (() -> Unit)? = null,
    navigationIcon: ImageVector = LjIcons.Menu,
    actions: @Composable () -> Unit = {},
    showSpoofToggle: Boolean = true,
    locationLabel: String? = null,
) {
    Box(modifier = modifier) {
        CenterAlignedTopAppBar(
            title = {},
            navigationIcon = {
                if (onNavigationClick != null) {
                    IconButton(onClick = onNavigationClick) {
                        Icon(
                            imageVector = navigationIcon,
                            contentDescription = "Open navigation menu",
                        )
                    }
                }
            },
            actions = { actions() },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
        )
        if (showSpoofToggle) {
            Box(
                modifier = Modifier.matchParentSize().windowInsetsPadding(TopAppBarDefaults.windowInsets),
                contentAlignment = Alignment.Center,
            ) {
                TextButton(
                    onClick = onToggleSpoofing,
                    shape = RoundedCornerShape(50),
                    colors =
                        ButtonDefaults.textButtonColors(
                            containerColor = if (isSpoofing) LjError.copy(alpha = 0.25f) else LjSuccess.copy(alpha = 0.25f),
                            contentColor = if (isSpoofing) LjError else LjSuccess,
                        ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier =
                        Modifier.semantics {
                            contentDescription = if (isSpoofing) "Stop location simulation" else "Start location simulation"
                        },
                ) {
                    Crossfade(
                        targetState = isSpoofing,
                        animationSpec = tween(150),
                        label = "spoofToggleIcon",
                    ) { spoofing ->
                        Icon(
                            imageVector = if (spoofing) LjIcons.Stop else LjIcons.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp).padding(end = 4.dp),
                        )
                    }
                    Text(
                        text =
                            if (isSpoofing) {
                                "Stop"
                            } else if (locationLabel != null) {
                                "Start · $locationLabel"
                            } else {
                                "Start"
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
