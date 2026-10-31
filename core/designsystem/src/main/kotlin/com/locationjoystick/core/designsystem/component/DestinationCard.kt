package com.locationjoystick.core.designsystem.component

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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.designsystem.LjSpacing
import com.locationjoystick.core.designsystem.R

/** One card in a [DestinationHub] list — icon, title, description, and its own tap target. */
data class DestinationCardSpec(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val onClick: () -> Unit,
)

/**
 * A tappable card: 40dp icon circle + title/description. Shared by IdleScreen and
 * SettingsHubScreen so they can't drift apart the way IdleDestinationCard/SettingsDestinationCard did.
 */
@Composable
fun DestinationCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = LjSpacing.md, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LjSpacing.md),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
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
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * App header (icon + name + version) followed by a list of [DestinationCard]s. Switches to a
 * 2-column [LazyVerticalGrid] on screens >= 600dp wide, single-column scroll otherwise — the
 * layout branch IdleScreen and SettingsHubScreen used to hand-duplicate.
 */
@Composable
fun DestinationHub(
    paddingValues: PaddingValues,
    cards: List<DestinationCardSpec>,
    modifier: Modifier = Modifier,
) {
    val isWide =
        with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.width
                .toDp()
        } >= 600.dp
    if (isWide) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = LjSpacing.lg),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp, bottom = LjSpacing.xl),
                ) {
                    DestinationHubHeaderText()
                }
            }
            items(cards) { card ->
                DestinationCard(card.icon, card.title, card.description, card.onClick)
            }
        }
    } else {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(LjSpacing.sm))
            DestinationHubHeaderText()
            Spacer(modifier = Modifier.height(LjSpacing.md))
            cards.forEachIndexed { index, card ->
                DestinationCard(card.icon, card.title, card.description, card.onClick)
                if (index != cards.lastIndex) {
                    Spacer(modifier = Modifier.height(LjSpacing.sm))
                }
            }
            Spacer(modifier = Modifier.height(LjSpacing.lg))
        }
    }
}

@Composable
private fun DestinationHubHeaderText() {
    AppIcon(size = 56.dp)
    Spacer(modifier = Modifier.height(LjSpacing.sm))
    Text(
        text = stringResource(R.string.destination_card_locationjoystick),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(LjSpacing.xs))
    Text(
        text = stringResource(R.string.destination_card_version, AppConstants.AppInfo.VERSION_NAME),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
