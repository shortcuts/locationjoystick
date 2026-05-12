package com.locationjoystick.core.ui.component

import android.util.Log
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val TAG = "NominatimSearchBar"
private const val NOMINATIM_SEARCH_URL = "https://nominatim.openstreetmap.org/search"
private const val SEARCH_DEBOUNCE_MS = 300L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NominatimSearchBar(
    onLocationSelected: (lat: Double, lon: Double, displayName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<NominatimResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(SEARCH_DEBOUNCE_MS)
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = URL("$NOMINATIM_SEARCH_URL?q=$encoded&format=json&limit=5")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "locationjoystick/1.0")
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
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

    SearchBar(
        query = query,
        onQueryChange = { query = it },
        onSearch = { /* no-op */ },
        active = active,
        onActiveChange = { active = it },
        modifier = modifier,
        placeholder = { Text("Search location...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
    ) {
        if (results.isEmpty() && query.length >= 2 && !isLoading) {
            ListItem(
                headlineContent = { Text("No results found", maxLines = 1) },
            )
        }
        LazyColumn {
            items(results) { result ->
                DropdownMenuItem(
                    text = {
                        Text(
                            result.displayName,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        onLocationSelected(result.lat, result.lon, result.displayName)
                        query = ""
                        active = false
                        results = emptyList()
                    },
                )
            }
        }
    }
}

private data class NominatimResult(
    val lat: Double,
    val lon: Double,
    val displayName: String,
)
