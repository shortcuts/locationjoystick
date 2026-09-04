package com.locationjoystick.core.designsystem.component

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
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
        actions = {
            if (showSpoofToggle) {
                val context = LocalContext.current
                val interactionSource = remember { MutableInteractionSource() }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isSpoofing) LjError.copy(alpha = 0.25f) else LjSuccess.copy(alpha = 0.25f),
                    contentColor = if (isSpoofing) LjError else LjSuccess,
                    modifier =
                        Modifier.defaultMinSize(minHeight = 48.dp).semantics {
                            contentDescription = if (isSpoofing) "Stop location simulation" else "Start location simulation"
                        }.combinedClickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            onClick = onToggleSpoofing,
                            onLongClick = {
                                if (!isSpoofing && locationLabel != null) {
                                    Toast.makeText(context, locationLabel, Toast.LENGTH_SHORT).show()
                                }
                            },
                        ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
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
            actions()
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurface,
            ),
    )
}
