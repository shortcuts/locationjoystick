package com.locationjoystick.feature.widget.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjSuccess
import com.locationjoystick.core.designsystem.component.ListSearchField
import com.locationjoystick.core.designsystem.component.SavedItemSortMenu
import com.locationjoystick.core.model.SavedItemSortMode
import com.locationjoystick.feature.widget.impl.R

@Composable
internal fun FloatingPickerShell(
    title: String,
    onDismiss: () -> Unit,
    hasBack: Boolean,
    onBack: () -> Unit,
    searchEnabled: Boolean = false,
    searchVisible: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onToggleSearch: () -> Unit = {},
    showShareCurrentLocation: Boolean = false,
    onShareCurrentLocation: () -> Unit = {},
    sortMode: SavedItemSortMode? = null,
    onSortModeSelected: (SavedItemSortMode) -> Unit = {},
    searchLabel: String = stringResource(R.string.widget_search),
    compactOverlay: Boolean = false,
    minimized: Boolean = false,
    onToggleMinimize: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    if (compactOverlay) {
        CompactFloatingPickerShell(
            title = title,
            colors = colors,
            minimized = minimized,
            onToggleMinimize = onToggleMinimize,
            onDismiss = onDismiss,
            content = content,
        )
        return
    }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable { if (hasBack) onBack() else onDismiss() },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .shadow(elevation = 8.dp, shape = MaterialTheme.shapes.medium)
                    .background(colors.background, MaterialTheme.shapes.medium)
                    .clickable { /* consume touches inside panel */ },
        ) {
            CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                ) {
                    FloatingPickerTitleRow(
                        title = title,
                        colors = colors,
                        hasBack = hasBack,
                        onBack = onBack,
                        searchEnabled = searchEnabled,
                        searchVisible = searchVisible,
                        onToggleSearch = onToggleSearch,
                        showShareCurrentLocation = showShareCurrentLocation,
                        onShareCurrentLocation = onShareCurrentLocation,
                        sortMode = sortMode,
                        onSortModeSelected = onSortModeSelected,
                        onDismiss = onDismiss,
                        onToggleMinimize = null,
                        minimized = false,
                    )
                    if (!hasBack && searchEnabled && searchVisible) {
                        ListSearchField(
                            query = searchQuery,
                            onQueryChange = onSearchQueryChange,
                            label = searchLabel,
                            autoFocus = true,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun CompactFloatingPickerShell(
    title: String,
    colors: ColorScheme,
    minimized: Boolean,
    onToggleMinimize: (() -> Unit)?,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    colors.background,
                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                ).navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
            FloatingPickerTitleRow(
                title = title,
                colors = colors,
                hasBack = false,
                onBack = onDismiss,
                searchEnabled = false,
                searchVisible = false,
                onToggleSearch = {},
                sortMode = null,
                onSortModeSelected = {},
                onDismiss = onDismiss,
                onToggleMinimize = onToggleMinimize,
                minimized = minimized,
            )
            if (minimized) {
                Text(
                    text = stringResource(R.string.widget_panel_content_switch_apps_to_copy_then_expand_to_paste),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            } else {
                Spacer(Modifier.height(8.dp))
            }
            // Keep the form composed while shrunk so pasted text is not reset.
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (minimized) {
                                Modifier.size(0.dp).clipToBounds()
                            } else {
                                Modifier.heightIn(max = maxHeight)
                            },
                        ),
                content = content,
            )
        }
    }
}

@Composable
private fun FloatingPickerTitleRow(
    title: String,
    colors: ColorScheme,
    hasBack: Boolean,
    onBack: () -> Unit,
    searchEnabled: Boolean,
    searchVisible: Boolean,
    onToggleSearch: () -> Unit,
    showShareCurrentLocation: Boolean = false,
    onShareCurrentLocation: () -> Unit = {},
    sortMode: SavedItemSortMode?,
    onSortModeSelected: (SavedItemSortMode) -> Unit,
    onDismiss: () -> Unit,
    onToggleMinimize: (() -> Unit)?,
    minimized: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasBack) {
            IconButton(onClick = onBack) {
                Icon(LjIcons.ArrowBack, contentDescription = stringResource(R.string.widget_panel_content_back), tint = colors.onBackground)
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (!hasBack && showShareCurrentLocation) {
            IconButton(onClick = onShareCurrentLocation) {
                Icon(
                    imageVector = LjIcons.Share,
                    contentDescription = stringResource(R.string.widget_panel_content_share_current_location),
                    tint = colors.onBackground,
                )
            }
        }
        if (!hasBack && sortMode != null) {
            SavedItemSortMenu(selected = sortMode, onSelected = onSortModeSelected)
        }
        if (!hasBack && searchEnabled) {
            IconButton(onClick = onToggleSearch) {
                Icon(
                    imageVector = LjIcons.Search,
                    contentDescription =
                        if (searchVisible) {
                            stringResource(
                                R.string.widget_hide_search,
                            )
                        } else {
                            stringResource(R.string.widget_search)
                        },
                    tint = if (searchVisible) LjSuccess else colors.onBackground,
                )
            }
        }
        if (onToggleMinimize != null) {
            IconButton(onClick = onToggleMinimize) {
                Icon(
                    imageVector = if (minimized) LjIcons.ExpandLess else LjIcons.ExpandMore,
                    contentDescription =
                        if (minimized) {
                            stringResource(
                                R.string.widget_expand_paste_box,
                            )
                        } else {
                            stringResource(R.string.widget_shrink_paste_box)
                        },
                    tint = colors.onBackground,
                )
            }
        }
        if (!hasBack) {
            IconButton(onClick = onDismiss) {
                Icon(LjIcons.Close, contentDescription = stringResource(R.string.widget_panel_content_close), tint = colors.onBackground)
            }
        }
    }
}
