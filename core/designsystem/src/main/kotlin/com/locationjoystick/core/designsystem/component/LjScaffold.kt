package com.locationjoystick.core.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.locationjoystick.core.designsystem.LjIcons

@Composable
fun LjScaffold(
    title: String,
    isSpoofing: Boolean,
    onToggleSpoofing: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigationClick: (() -> Unit)? = null,
    navigationIcon: ImageVector = LjIcons.Menu,
    actions: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = { SnackbarHost(remember { SnackbarHostState() }) },
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentWindowInsets: WindowInsets = WindowInsets(0),
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            LjTopBar(
                title = title,
                isSpoofing = isSpoofing,
                onToggleSpoofing = onToggleSpoofing,
                onNavigationClick = onNavigationClick,
                navigationIcon = navigationIcon,
                actions = actions,
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
        snackbarHost = snackbarHost,
        containerColor = containerColor,
        contentWindowInsets = contentWindowInsets,
        content = content,
    )
}
