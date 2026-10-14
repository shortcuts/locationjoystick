package com.locationjoystick.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.geocoding.GeocodeResult
import com.locationjoystick.core.common.geocoding.Geocoding
import com.locationjoystick.core.common.util.parseRawLatLng
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjSpacing
import com.locationjoystick.core.designsystem.R
import com.locationjoystick.core.model.RecentSearch
import kotlinx.coroutines.delay

@Composable
fun NominatimSearchBar(
    onLocationSelected: (lat: Double, lon: Double, displayName: String) -> Unit,
    modifier: Modifier = Modifier,
    recentSearches: List<RecentSearch> = emptyList(),
    onSearchCommitted: ((displayName: String, lat: Double, lon: Double) -> Unit)? = null,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GeocodeResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val rawCoords = parseRawLatLng(query)
        if (rawCoords != null) {
            results =
                listOf(
                    GeocodeResult(
                        lat = rawCoords.latitude,
                        lon = rawCoords.longitude,
                        displayName = "Go to ${rawCoords.latitude}, ${rawCoords.longitude}",
                    ),
                )
            isLoading = false
            return@LaunchedEffect
        }
        if (query.length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(AppConstants.NominatimConstants.SEARCH_DEBOUNCE_MS)
        isLoading = true
        results = Geocoding.provider.search(query)
        isLoading = false
    }

    val showRecent = query.isEmpty() && recentSearches.isNotEmpty()
    val showResults = results.isNotEmpty() || (query.length >= 2 && !isLoading) || showRecent

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
            placeholder = { Text(stringResource(R.string.search_bar_search_location)) },
            leadingIcon = { Icon(LjIcons.Search, contentDescription = null) },
            singleLine = true,
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                ),
            shape =
                if (showResults) {
                    MaterialTheme.shapes.medium.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)
                } else {
                    MaterialTheme.shapes.medium
                },
        )

        if (showResults) {
            HorizontalDivider()
            if (showRecent) {
                Text(
                    text = stringResource(R.string.search_bar_recent),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = LjSpacing.md, vertical = 6.dp),
                )
                recentSearches.forEach { recent ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onLocationSelected(recent.lat, recent.lon, recent.displayName)
                                    onSearchCommitted?.invoke(recent.displayName, recent.lat, recent.lon)
                                    query = ""
                                }.padding(horizontal = LjSpacing.md, vertical = 12.dp),
                    ) {
                        Icon(
                            imageVector = LjIcons.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = recent.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    HorizontalDivider()
                }
            }
            if (results.isEmpty() && query.length >= 2 && !isLoading) {
                Text(
                    text = stringResource(R.string.search_bar_no_results_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = LjSpacing.md, vertical = 12.dp),
                )
            }
            results.forEach { result ->
                Text(
                    text = result.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) {
                                onLocationSelected(result.lat, result.lon, result.displayName)
                                onSearchCommitted?.invoke(result.displayName, result.lat, result.lon)
                                query = ""
                                results = emptyList()
                            }.padding(horizontal = LjSpacing.md, vertical = 14.dp),
                )
                HorizontalDivider()
            }
        }
    }
}
