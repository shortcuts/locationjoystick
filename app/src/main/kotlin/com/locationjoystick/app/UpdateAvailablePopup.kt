package com.locationjoystick.app

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.locationjoystick.app.R
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.designsystem.LjIcons

/** Home-screen "a newer version is available on GitHub" badge (see docs/features/update-check.md). */
@Composable
fun UpdateAvailablePopup(modifier: Modifier = Modifier) {
    val viewModel: UpdateAvailableViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    AnimatedVisibility(
        visible = uiState.visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 4 }),
        modifier = modifier,
    ) {
        DismissiblePillBadge(
            icon = LjIcons.FileDownload,
            label = stringResource(R.string.update_available_label, uiState.latestVersion),
            contentDescription = stringResource(R.string.update_available_cd, uiState.latestVersion),
            dismissDescription = stringResource(R.string.update_available_dismiss_cd),
            onClick = {
                viewModel.dismiss(uiState.latestVersion)
                val url = AppConstants.UpdateCheckConstants.releaseUrl(uiState.latestVersion)
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            },
            onDismiss = { viewModel.dismiss(uiState.latestVersion) },
        )
    }
}
