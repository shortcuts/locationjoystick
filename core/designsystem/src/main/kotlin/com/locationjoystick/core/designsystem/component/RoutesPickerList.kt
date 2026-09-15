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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjSpacing
import com.locationjoystick.core.designsystem.R
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.matchesSearch

/**
 * Shared composable for the route-picker row list — name, waypoint count, and a Start button.
 * Used by both the map long-press Routes sheet and the floating widget's Routes panel.
 *
 * @param title Header text. Null skips the header — for embedding inside a container that
 *   already renders its own title.
 * @param enableSearch When true, shows a search field and filters locally. Query is `remember`d
 *   (not saveable) so dismissing the host panel starts from the full list.
 * @param filterQuery Used only when [enableSearch] is false — parent-owned filter (widget picker).
 */
@Composable
fun RoutesPickerList(
    routes: List<Route>,
    onSelect: (Route) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    rowBackground: Color = MaterialTheme.colorScheme.surfaceVariant,
    textColor: Color = Color.Unspecified,
    enableSearch: Boolean = true,
    filterQuery: String = "",
) {
    var internalQuery by remember { mutableStateOf("") }
    val query = if (enableSearch) internalQuery else filterQuery
    val filtered = remember(routes, query) { routes.filter { it.matchesSearch(query) } }

    Column(modifier = modifier.fillMaxWidth().padding(contentPadding)) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = textColor)
        }
        if (enableSearch && routes.isNotEmpty()) {
            ListSearchField(
                query = internalQuery,
                onQueryChange = { internalQuery = it },
                label = stringResource(R.string.routes_picker_list_search_routes),
                textColor = textColor,
                modifier = Modifier.padding(top = if (title != null) 8.dp else 0.dp),
            )
        }
        if (routes.isEmpty()) {
            Text(
                stringResource(R.string.routes_picker_no_routes_saved),
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                modifier = Modifier.padding(top = LjSpacing.md),
            )
        } else if (filtered.isEmpty()) {
            Text(
                stringResource(R.string.routes_picker_list_no_routes_match_your_search),
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                modifier = Modifier.padding(top = 16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(top = if (title != null || enableSearch) 8.dp else 0.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered, key = { it.id }) { route ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(rowBackground, MaterialTheme.shapes.small)
                                .padding(8.dp),
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
                            Icon(LjIcons.PlayArrow, contentDescription = stringResource(R.string.routes_picker_start_route_cd))
                        }
                    }
                }
            }
        }
    }
}
