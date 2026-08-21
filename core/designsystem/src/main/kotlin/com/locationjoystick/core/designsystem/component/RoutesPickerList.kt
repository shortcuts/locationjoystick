package com.locationjoystick.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.model.Route

/**
 * Shared composable for the route-picker row list — name, waypoint count, and a Start button.
 * Used by both the map long-press Routes sheet and the floating widget's Routes panel.
 *
 * @param title Header text. Null skips the header — for embedding inside a container that
 *   already renders its own title.
 */
@Composable
fun RoutesPickerList(
    routes: List<Route>,
    onSelect: (Route) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    rowBackground: Color = MaterialTheme.colorScheme.surfaceVariant,
    textColor: Color = Color.Unspecified,
) {
    Column(modifier = modifier.fillMaxWidth().padding(contentPadding)) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = textColor)
        }
        if (routes.isEmpty()) {
            Text(
                "No routes saved",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                modifier = Modifier.padding(top = 16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(top = if (title != null) 12.dp else 0.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(routes, key = { it.id }) { route ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(rowBackground, MaterialTheme.shapes.small)
                                .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(route.name, style = MaterialTheme.typography.titleMedium, color = textColor)
                            Text(
                                "${route.waypoints.size} waypoints",
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor,
                            )
                        }
                        Button(onClick = { onSelect(route) }) {
                            Icon(LjIcons.PlayArrow, contentDescription = "Start route")
                        }
                    }
                }
            }
        }
    }
}
