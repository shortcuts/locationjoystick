package com.locationjoystick.app

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.designsystem.LjIcons

/**
 * App-level "what's new in this version" badge. Non-mandatory: the badge sits quietly until
 * tapped, dismissing itself for this version either way once tapped or explicitly closed.
 */
@Composable
fun WhatsNewPopup(modifier: Modifier = Modifier) {
    val viewModel: WhatsNewViewModel = hiltViewModel()
    val hasUnseenUpdate by viewModel.hasUnseenUpdate.collectAsState()
    var showModal by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    AnimatedVisibility(
        visible = hasUnseenUpdate && !showModal,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 4 }),
        modifier = modifier,
    ) {
        WhatsNewBadge(
            onClick = {
                viewModel.markSeen()
                showModal = true
            },
            onDismiss = viewModel::markSeen,
        )
    }

    if (showModal) {
        val loadState by viewModel.loadState.collectAsState()
        LaunchedEffect(Unit) { viewModel.loadHighlights() }
        WhatsNewDialog(
            loadState = loadState,
            onDismiss = { showModal = false },
            onViewFullChangelog = {
                showModal = false
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppConstants.AppInfo.CHANGELOG_URL)))
            },
        )
    }
}

@Composable
private fun WhatsNewBadge(
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f, label = "whatsNewBadgeScale")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            Modifier
                .scale(scale)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .semantics { contentDescription = "What's new in this version" }
                .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 10.dp),
    ) {
        Icon(
            imageVector = LjIcons.WhatsNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = "What's new",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
        )
        Icon(
            imageVector = LjIcons.Close,
            contentDescription = "Dismiss",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier =
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDismiss),
        )
    }
}

@Composable
private fun WhatsNewDialog(
    loadState: WhatsNewLoadState,
    onDismiss: () -> Unit,
    onViewFullChangelog: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What's new in v${AppConstants.AppInfo.VERSION_NAME}") },
        text = {
            when (loadState) {
                is WhatsNewLoadState.Loading ->
                    Box(modifier = Modifier.height(80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                is WhatsNewLoadState.Failed ->
                    Text(
                        "Couldn't load what's new. Check your connection, or view the full changelog online.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                is WhatsNewLoadState.Loaded ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        loadState.highlights.forEach { highlight ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("•", style = MaterialTheme.typography.bodyMedium)
                                Text(highlight, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it")
            }
        },
        dismissButton = {
            TextButton(onClick = onViewFullChangelog) {
                Text("View full changelog")
            }
        },
    )
}
