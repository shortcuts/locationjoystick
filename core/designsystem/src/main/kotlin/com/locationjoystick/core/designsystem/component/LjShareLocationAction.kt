package com.locationjoystick.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.locationjoystick.core.designsystem.ActivityState
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.R
import com.locationjoystick.core.designsystem.UiConstants
import com.locationjoystick.core.designsystem.containerColor
import com.locationjoystick.core.designsystem.contentColor

/**
 * Share icon that expands inline (no popup, so it works inside overlay windows) into two options:
 * copy coordinates or share the location link. Picking an option runs its callback and collapses.
 */
@Composable
fun LjShareLocationAction(
    onCopyCoordinates: () -> Unit,
    onShareLink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val container = ActivityState.IDLE.containerColor(MaterialTheme.colorScheme)
    val content = ActivityState.IDLE.contentColor(MaterialTheme.colorScheme)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UiConstants.FAB_CONTAINER_SIZE / 4),
    ) {
        AnimatedVisibility(visible = expanded) {
            Row(horizontalArrangement = Arrangement.spacedBy(UiConstants.FAB_CONTAINER_SIZE / 4)) {
                LjMapIconButton(
                    icon = LjIcons.ContentCopy,
                    contentDescription = stringResource(R.string.share_location_copy_coordinates_cd),
                    containerColor = container,
                    contentColor = content,
                    onClick = {
                        expanded = false
                        onCopyCoordinates()
                    },
                )
                LjMapIconButton(
                    icon = LjIcons.Link,
                    contentDescription = stringResource(R.string.share_location_link_cd),
                    containerColor = container,
                    contentColor = content,
                    onClick = {
                        expanded = false
                        onShareLink()
                    },
                )
            }
        }
        LjMapIconButton(
            icon = LjIcons.Share,
            contentDescription = stringResource(R.string.share_location_cd),
            containerColor = container,
            contentColor = if (expanded) MaterialTheme.colorScheme.primary else content,
            onClick = { expanded = !expanded },
        )
    }
}
