package com.locationjoystick.core.designsystem.component

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

@Composable
fun NominatimSearchBar(
    onLocationSelected: (lat: Double, lon: Double, displayName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
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

    val showResults = results.isNotEmpty() || (query.length >= 2 && !isLoading)

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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onLocationSelected(result.lat, result.lon, result.displayName)
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
