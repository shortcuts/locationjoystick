package com.locationjoystick.core.designsystem.component

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.parseRawLatLng
import com.locationjoystick.core.model.RecentSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val TAG = "NominatimSearchBar"

@Composable
fun NominatimSearchBar(
    onLocationSelected: (lat: Double, lon: Double, displayName: String) -> Unit,
    modifier: Modifier = Modifier,
    recentSearches: List<RecentSearch> = emptyList(),
    onSearchCommitted: ((displayName: String, lat: Double, lon: Double) -> Unit)? = null,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<NominatimResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val rawCoords = parseRawLatLng(query)
        if (rawCoords != null) {
            results =
                listOf(
                    NominatimResult(
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
        withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = URL("${AppConstants.NominatimConstants.SEARCH_URL}?q=$encoded&format=json&limit=5")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "locationjoystick/1.0")
                conn.connectTimeout = AppConstants.NominatimConstants.CONNECT_TIMEOUT_MS
                conn.readTimeout = AppConstants.NominatimConstants.READ_TIMEOUT_MS
                try {
                    val responseText = conn.inputStream.bufferedReader().readText()
                    val array = JSONArray(responseText)
                    val parsed =
                        (0 until minOf(array.length(), 5)).mapNotNull { i ->
                            try {
                                val obj = array.getJSONObject(i)
                                NominatimResult(
                                    lat = obj.getDouble("lat"),
                                    lon = obj.getDouble("lon"),
                                    displayName = obj.getString("display_name"),
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }
                    results = parsed
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Search failed", e)
                results = emptyList()
            }
            isLoading = false
        }
    }

    val showRecent = query.isEmpty() && recentSearches.isNotEmpty()
    val showResults = results.isNotEmpty() || (query.length >= 2 && !isLoading) || showRecent

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
            placeholder = { Text("Search location...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape =
                if (showResults) {
                    RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                } else {
                    RoundedCornerShape(12.dp)
                },
        )

        if (showResults) {
            HorizontalDivider()
            if (showRecent) {
                Text(
                    text = "Recent",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
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
                                }.padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
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
                    text = "No results found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                            .clickable {
                                onLocationSelected(result.lat, result.lon, result.displayName)
                                onSearchCommitted?.invoke(result.displayName, result.lat, result.lon)
                                query = ""
                                results = emptyList()
                            }.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                HorizontalDivider()
            }
        }
    }
}

private data class NominatimResult(
    val lat: Double,
    val lon: Double,
    val displayName: String,
)
