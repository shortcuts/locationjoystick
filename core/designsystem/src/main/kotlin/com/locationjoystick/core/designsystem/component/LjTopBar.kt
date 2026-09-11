package com.locationjoystick.core.designsystem.component

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.designsystem.LjError
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjSpacing
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(TopAppBarDefaults.windowInsets)
                    .padding(horizontal = LjSpacing.xs, vertical = LjSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Start column: hamburger + title, left-aligned.
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onNavigationClick != null) {
                    IconButton(onClick = onNavigationClick) {
                        Icon(
                            imageVector = navigationIcon,
                            contentDescription = "Open navigation menu",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Middle column: state indicator, always centered.
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (showSpoofToggle) {
                    val context = LocalContext.current
                    val interactionSource = remember { MutableInteractionSource() }
                    val tint = if (isSpoofing) LjError else LjSuccess
                    Row(
                        modifier =
                            Modifier
                                .defaultMinSize(minHeight = 44.dp)
                                .semantics {
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
                                ).padding(horizontal = LjSpacing.sm, vertical = 3.dp),
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
                                tint = tint,
                                modifier = Modifier.size(12.dp).padding(end = 3.dp),
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
                            style = MaterialTheme.typography.labelSmall,
                            color = tint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // End column: overflow menu (or nothing, for screens with no actions).
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions()
            }
        }
    }
}

@Composable
fun LjOverflowMenu(content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(LjIcons.MoreVert, contentDescription = "More actions")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            content { expanded = false }
        }
    }
}

/**
 * Section label + divider for grouping items inside [LjOverflowMenu]'s dropdown content.
 * Renders a divider above the label except for the first group in a menu — pass
 * [showDivider] = false for that one.
 */
@Composable
fun LjOverflowMenuSectionLabel(
    text: String,
    showDivider: Boolean = true,
) {
    if (showDivider) {
        HorizontalDivider(
            modifier = Modifier.padding(vertical = LjSpacing.xs),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = LjSpacing.md, vertical = LjSpacing.sm),
    )
}

@Preview
@Composable
private fun LjTopBarPreview() {
    LjTopBar(
        title = "Map",
        isSpoofing = false,
        onToggleSpoofing = {},
        onNavigationClick = {},
    )
}

@Preview
@Composable
private fun LjOverflowMenuSectionLabelPreview() {
    Surface {
        Column {
            LjOverflowMenuSectionLabel("Export", showDivider = false)
            LjOverflowMenuSectionLabel("Import")
            LjOverflowMenuSectionLabel("Danger")
        }
    }
}
