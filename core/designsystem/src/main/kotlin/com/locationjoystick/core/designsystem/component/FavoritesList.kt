package com.locationjoystick.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import java.util.Locale

/**
 * Shared composable for displaying a list of favorite locations.
 *
 * @param title Header text (e.g. "Favorites", "Jump to Favorite"). Null skips the header entirely
 *   — for embedding inside a container that already renders its own title.
 * @param favorites List of [FavoriteLocation] items to display.
 * @param onSelect Called with the selected [FavoriteLocation].
 * @param onSaveCurrentLocation Optional: when non-null, an Add icon button is shown in the header.
 * @param cooldownBadgeText Optional: returns the full badge text for each favorite (e.g.
 *   "Suggested wait: 5m 30s · 2.3 km teleport" or "1.2 km away"). When non-null, a badge is shown.
 */
@Composable
fun FavoritesList(
    title: String?,
    favorites: List<FavoriteLocation>,
    onSelect: (FavoriteLocation) -> Unit,
    modifier: Modifier = Modifier,
    onSaveCurrentLocation: (() -> Unit)? = null,
    cooldownBadgeText: ((FavoriteLocation) -> String)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    rowBackground: Color = MaterialTheme.colorScheme.surfaceVariant,
    textColor: Color = Color.Unspecified,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(contentPadding),
    ) {
        if (title != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = textColor)
                if (onSaveCurrentLocation != null) {
                    IconButton(onClick = onSaveCurrentLocation) {
                        Icon(Icons.Default.Add, contentDescription = "Save current location")
                    }
                }
            }
        }

        if (favorites.isEmpty()) {
            Text(
                "No saved favorites yet",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                modifier = Modifier.padding(top = 16.dp),
            )
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = favorites, key = { it.id }) { favorite ->
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    rowBackground,
                                    MaterialTheme.shapes.small,
                                ).clickable { onSelect(favorite) }
                                .padding(12.dp),
                    ) {
                        Text(favorite.name, style = MaterialTheme.typography.titleMedium, color = textColor)
                        Text(
                            formatLatLng(favorite.position.latitude, favorite.position.longitude),
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor,
                        )
                        if (cooldownBadgeText != null) {
                            Spacer(Modifier.height(6.dp))
                            CooldownAdvisoryBadge(cooldownBadgeText(favorite))
                        }
                    }
                }
            }
        }
    }
}

/** Shared "lat, lon" formatting for coordinate rows across favorite detail/list composables. */
fun formatLatLng(
    latitude: Double,
    longitude: Double,
): String =
    "${String.format(Locale.US, "%.4f", latitude)}, " +
        String.format(Locale.US, "%.4f", longitude)

@Composable
fun CooldownAdvisoryBadge(
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FavoritesListEmptyPreview() {
    FavoritesList(
        title = "Favorites",
        favorites = emptyList(),
        onSelect = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun FavoritesListWithItemsPreview() {
    FavoritesList(
        title = "Favorites",
        favorites =
            listOf(
                FavoriteLocation(
                    id = "1",
                    name = "Home",
                    position = LatLng(48.8566, 2.3522),
                    createdAt = 0L,
                ),
                FavoriteLocation(
                    id = "2",
                    name = "Work",
                    position = LatLng(48.8606, 2.3376),
                    createdAt = 0L,
                ),
            ),
        onSelect = {},
        onSaveCurrentLocation = {},
    )
}
