package com.locationjoystick.feature.settings.impl

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.AppSettings
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.Route
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.GZIPOutputStream

internal object QrChunker {
    private val CHUNK_SIZE_LIMIT = AppConstants.ExportConstants.QR_CHUNK_SIZE_LIMIT

    data class ChunkResult(
        val chunks: List<ChunkEnvelope>,
        val skippedRoutes: List<String>, // names of routes too large for a single QR chunk
    )

    fun chunk(exportData: com.locationjoystick.core.model.ExportData): ChunkResult {
        val sessionId = UUID.randomUUID().toString()
        val skipped = mutableListOf<String>()

        // Build item list: settings first, then favorites, then routes
        val items = mutableListOf<ChunkContent>()
        items.add(ChunkContent.Settings(exportData.settings))
        items.addAll(exportData.favoriteLocations.map { ChunkContent.Favorites(listOf(it)) })
        items.addAll(exportData.routes.map { ChunkContent.Routes(listOf(it)) })

        // Greedy bin-packing: try to fit each item into the last open bin
        val bins = mutableListOf<MutableList<ChunkContent>>()
        for (item in items) {
            val itemJson = contentToJsonObject(item)
            val itemSize = gzipSize(itemJson.toString())

            if (itemSize > CHUNK_SIZE_LIMIT) {
                // Single item too large — skip routes, swallow favorites/settings (should not happen)
                when (item) {
                    is ChunkContent.Routes -> {
                        item.payload.forEach { skipped.add(it.name) }
                    }

                    is ChunkContent.Favorites -> {}

                    // individual favorite should never exceed limit
                    is ChunkContent.Settings -> {} // settings should never exceed limit
                }
                continue
            }

            // Try to add to last bin
            val lastBin = bins.lastOrNull()
            if (lastBin != null) {
                val candidateJson = binToJsonArray(lastBin + item)
                if (gzipSize(candidateJson.toString()) <= CHUNK_SIZE_LIMIT) {
                    lastBin.add(item)
                    continue
                }
            }

            // Open a new bin
            bins.add(mutableListOf(item))
        }

        // Build envelopes
        val totalChunks = bins.size
        val envelopes =
            bins.mapIndexed { idx, bin ->
                val jsonString = binToJsonArray(bin).toString()
                val gzipped = gzip(jsonString.toByteArray(Charsets.UTF_8))
                val encoded =
                    java.util.Base64
                        .getEncoder()
                        .encodeToString(gzipped)
                ChunkEnvelope(
                    session = sessionId,
                    chunk = idx + 1,
                    total = totalChunks,
                    d = encoded,
                )
            }

        return ChunkResult(envelopes, skipped)
    }

    // Serialize a bin (list of ChunkContent) as a JSONArray of content objects
    private fun binToJsonArray(contents: List<ChunkContent>): JSONArray {
        val array = JSONArray()
        contents.forEach { array.put(contentToJsonObject(it)) }
        return array
    }

    // Serialize a single ChunkContent as a JSONObject with "type" + "payload"
    private fun contentToJsonObject(content: ChunkContent): JSONObject =
        when (content) {
            is ChunkContent.Settings -> {
                JSONObject().apply {
                    put("type", "settings")
                    put("payload", settingsToJson(content.payload))
                }
            }

            is ChunkContent.Routes -> {
                JSONObject().apply {
                    put("type", "routes")
                    put("payload", JSONArray().apply { content.payload.forEach { put(routeToJson(it)) } })
                }
            }

            is ChunkContent.Favorites -> {
                JSONObject().apply {
                    put("type", "favorites")
                    put("payload", JSONArray().apply { content.payload.forEach { put(favoriteToJson(it)) } })
                }
            }
        }

    private fun settingsToJson(settings: AppSettings): JSONObject =
        JSONObject().apply {
            put("speedUnit", settings.speedUnit.name)
            put("enabledWidgetFeatures", JSONArray(settings.enabledWidgetFeatures.map { it.name }))
            put("activeSpeedProfileId", settings.activeSpeedProfileId)
            put("joystickStyle", settings.joystickStyle.name)
            put("mapFollowsLocation", settings.mapFollowsLocation)
            put("useRoadSnappingByDefault", settings.useRoadSnappingByDefault)
        }

    private fun routeToJson(route: Route): JSONObject =
        JSONObject().apply {
            put("id", route.id)
            put("name", route.name)
            put("isLooping", route.isLooping)
            put("routeType", route.routeType.name)
            put("createdAt", route.createdAt)
            val wpArray = JSONArray()
            route.waypoints.forEach { wp ->
                wpArray.put(
                    JSONObject().apply {
                        put("id", wp.id)
                        put("lat", wp.position.latitude)
                        put("lon", wp.position.longitude)
                        put("orderIndex", wp.orderIndex)
                    },
                )
            }
            put("waypoints", wpArray)
        }

    private fun favoriteToJson(fav: FavoriteLocation): JSONObject =
        JSONObject().apply {
            put("id", fav.id)
            put("name", fav.name)
            put("lat", fav.position.latitude)
            put("lon", fav.position.longitude)
            put("createdAt", fav.createdAt)
        }

    private fun gzipSize(json: String): Int = gzip(json.toByteArray(Charsets.UTF_8)).size

    private fun gzip(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(data) }
        return output.toByteArray()
    }
}
