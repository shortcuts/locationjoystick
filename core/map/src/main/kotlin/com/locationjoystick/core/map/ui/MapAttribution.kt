package com.locationjoystick.core.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.map.R
import com.locationjoystick.core.model.MapTileSource

/** Human-readable credit line for a raster provider. */
@Composable
fun mapAttributionText(tileSource: MapTileSource): String =
    stringResource(
        when (tileSource) {
            MapTileSource.OSM -> R.string.map_attribution_osm
            MapTileSource.AMAP -> R.string.map_attribution_amap
        },
    )

/**
 * Small translucent provider credit, meant to sit in a corner of every map surface. We disable
 * MapLibre's built-in attribution button, so this is what keeps us compliant with each provider's
 * "credit the data source" requirement and also makes the GCJ-02 datum visible to the user.
 */
@Composable
fun MapAttribution(
    tileSource: MapTileSource,
    modifier: Modifier = Modifier,
) {
    Text(
        text = mapAttributionText(tileSource),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier =
            modifier
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    MaterialTheme.shapes.extraSmall,
                ).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
