package com.locationjoystick.feature.settings.impl

import android.util.Log
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.core.model.Waypoint
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Parses a GPS Joystick Realm/TightDB binary export (.db) without a Realm SDK dependency.
 *
 * The binary format is scanned for known structural markers to extract:
 * - Favorites (class_PlaceLocationData): names + lat/lon double columns
 * - Routes (class_RouteData + class_CoordinateData): route names + waypoint columns
 *
 * Speed profiles are not present in GPS Joystick exports.
 */
internal object GpsJoystickMigrator {
    private const val TAG = "GpsJoystickMigrator"

    // Realm/TightDB header magic bytes
    private val REALM_HEADER = "T-DB".toByteArray(Charsets.US_ASCII)

    // Marker that precedes each columnar double array: "AAAA\x0c\x00\x00\x04"
    private val DOUBLE_ARRAY_MARKER =
        byteArrayOf(
            0x41,
            0x41,
            0x41,
            0x41,
            0x0c,
            0x00,
            0x00,
            0x04,
        )

    fun parse(bytes: ByteArray): Result<MigrationResult> =
        runCatching {
            if (!startsWithHeader(bytes)) {
                return Result.failure(IllegalArgumentException("Not a valid Realm database file (missing T-DB header)"))
            }

            val favorites = parseFavorites(bytes)
            val routes = parseRoutes(bytes)

            MigrationResult(
                favorites = favorites,
                routes = routes,
                walkSpeed = null,
                runSpeed = null,
                bikeSpeed = null,
            )
        }.onFailure { e ->
            Log.e(TAG, "Failed to parse GPS Joystick database", e)
        }

    private fun startsWithHeader(bytes: ByteArray): Boolean {
        if (bytes.size < REALM_HEADER.size) return false
        return REALM_HEADER.indices.all { i -> bytes[i] == REALM_HEADER[i] }
    }

    // -------------------------------------------------------------------------
    // Favorites parsing
    // -------------------------------------------------------------------------

    private fun parseFavorites(bytes: ByteArray): List<FavoriteLocation> {
        val names = parseFavoriteNames(bytes)
        if (names.isEmpty()) return emptyList()

        val doubleBlocks = findAllDoubleArrayBlocks(bytes, names.size)
        if (doubleBlocks.size < 2) {
            Log.e(TAG, "Could not find lat/lon columns for favorites (found ${doubleBlocks.size} blocks)")
            return emptyList()
        }

        val lats = doubleBlocks[0]
        val lons = doubleBlocks[1]
        val count = minOf(names.size, lats.size, lons.size)

        return (0 until count).map { i ->
            FavoriteLocation(
                id = UUID.randomUUID().toString(),
                name = names[i],
                position = LatLng(latitude = lats[i], longitude = lons[i]),
                createdAt = System.currentTimeMillis(),
            )
        }
    }

    /**
     * Favorite names are stored as a null-separated string list in the binary.
     * We scan for a block of 4 UUIDs (each UUID is 36 chars + null terminator)
     * and then read the null-separated names that follow.
     */
    private fun parseFavoriteNames(bytes: ByteArray): List<String> {
        val uuidPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        val text = bytes.toString(Charsets.ISO_8859_1)

        // Find consecutive UUIDs as null-terminated strings
        val uuidMatches = uuidPattern.findAll(text).toList()
        if (uuidMatches.size < 4) return emptyList()

        // After the last UUID block, scan for null-separated non-empty strings
        val lastUuidEnd = uuidMatches.last().range.last + 1
        return extractNullSeparatedStrings(text, lastUuidEnd, maxCount = uuidMatches.size)
    }

    // -------------------------------------------------------------------------
    // Routes parsing
    // -------------------------------------------------------------------------

    private fun parseRoutes(bytes: ByteArray): List<Route> {
        val names = parseRouteNames(bytes)
        if (names.isEmpty()) return emptyList()

        // Find all double-array blocks. The first two belong to favorites (lat, lon, altitude).
        // Route coordinate blocks appear after the favorite blocks.
        val allBlocks = findAllDoubleArrayBlocks(bytes, minSize = 2)

        // Skip first 3 blocks (favorites lat, lon, altitude). Then pair remaining as lat/lon per route.
        // Route waypoint columns are interleaved: lat0, lon0, lat1, lon1, ...
        val routeBlocks = if (allBlocks.size > 3) allBlocks.drop(3) else emptyList()

        val routes = mutableListOf<Route>()
        var blockIndex = 0
        for (name in names) {
            if (blockIndex + 1 >= routeBlocks.size) break
            val latCol = routeBlocks[blockIndex]
            val lonCol = routeBlocks[blockIndex + 1]
            blockIndex += 2

            val waypointCount = minOf(latCol.size, lonCol.size)
            if (waypointCount < 2) continue

            val waypoints =
                (0 until waypointCount).map { wi ->
                    Waypoint(
                        id = UUID.randomUUID().toString(),
                        position = LatLng(latitude = latCol[wi], longitude = lonCol[wi]),
                        orderIndex = wi,
                    )
                }

            routes.add(
                Route(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    waypoints = waypoints,
                    isLooping = false,
                    routeType = RouteType.STRAIGHT,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                ),
            )

            if (routes.size >= names.size) break
        }

        return routes
    }

    /**
     * Route names follow the same UUID-then-names pattern as favorites, but for 8 routes.
     * We look for the second large UUID block in the file (after the favorites block).
     */
    private fun parseRouteNames(bytes: ByteArray): List<String> {
        val uuidPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        val text = bytes.toString(Charsets.ISO_8859_1)

        val uuidMatches = uuidPattern.findAll(text).toList()
        // First N UUIDs belong to favorites. Routes use the next block (8 UUIDs).
        // We skip the first 4 (favorites) and look for the next cluster of 8.
        if (uuidMatches.size < 5) return emptyList()

        // Find clusters: group UUIDs that are close together in position
        val clusters = mutableListOf<List<MatchResult>>()
        var currentCluster = mutableListOf(uuidMatches[0])
        for (i in 1 until uuidMatches.size) {
            val prev = uuidMatches[i - 1]
            val curr = uuidMatches[i]
            if (curr.range.first - prev.range.last < 50) {
                currentCluster.add(curr)
            } else {
                if (currentCluster.size >= 2) clusters.add(currentCluster.toList())
                currentCluster = mutableListOf(curr)
            }
        }
        if (currentCluster.size >= 2) clusters.add(currentCluster.toList())

        // Routes cluster should have 8 UUIDs (second cluster)
        val routeCluster = clusters.getOrNull(1) ?: return emptyList()
        val lastUuidEnd = routeCluster.last().range.last + 1
        return extractNullSeparatedStrings(text, lastUuidEnd, maxCount = routeCluster.size)
    }

    // -------------------------------------------------------------------------
    // Shared utilities
    // -------------------------------------------------------------------------

    /**
     * Scans [bytes] for all DOUBLE_ARRAY_MARKER occurrences and reads the [minSize] doubles that follow each one.
     */
    private fun findAllDoubleArrayBlocks(
        bytes: ByteArray,
        minSize: Int,
    ): List<List<Double>> {
        val result = mutableListOf<List<Double>>()
        var pos = 0
        while (pos <= bytes.size - DOUBLE_ARRAY_MARKER.size) {
            if (markerAt(bytes, pos)) {
                val dataStart = pos + DOUBLE_ARRAY_MARKER.size
                val doubles = readDoubleArray(bytes, dataStart, minSize)
                if (doubles.isNotEmpty()) {
                    result.add(doubles)
                }
                pos += DOUBLE_ARRAY_MARKER.size
            } else {
                pos++
            }
        }
        return result
    }

    private fun markerAt(
        bytes: ByteArray,
        offset: Int,
    ): Boolean {
        if (offset + DOUBLE_ARRAY_MARKER.size > bytes.size) return false
        return DOUBLE_ARRAY_MARKER.indices.all { i -> bytes[offset + i] == DOUBLE_ARRAY_MARKER[i] }
    }

    /**
     * Reads little-endian doubles from [bytes] starting at [offset].
     * Reads up to as many doubles as possible that pass the validity check,
     * requiring at least [minCount] valid doubles.
     */
    private fun readDoubleArray(
        bytes: ByteArray,
        offset: Int,
        minCount: Int,
    ): List<Double> {
        val doubles = mutableListOf<Double>()
        var pos = offset
        while (pos + 8 <= bytes.size) {
            val buf = ByteBuffer.wrap(bytes, pos, 8).order(ByteOrder.LITTLE_ENDIAN)
            val d = buf.double
            if (!d.isFinite() || d == 0.0) break
            doubles.add(d)
            pos += 8
        }
        return if (doubles.size >= minCount) doubles else emptyList()
    }

    /**
     * Extracts null-separated non-empty printable strings starting at [startIdx] in [text],
     * up to [maxCount] strings.
     */
    private fun extractNullSeparatedStrings(
        text: String,
        startIdx: Int,
        maxCount: Int,
    ): List<String> {
        val names = mutableListOf<String>()
        val sb = StringBuilder()
        var i = startIdx
        while (i < text.length && names.size < maxCount) {
            val c = text[i]
            when {
                c == '\u0000' -> {
                    val s = sb.toString().trim()
                    if (s.isNotEmpty() && s.all { it.code in 32..126 }) {
                        names.add(s)
                    }
                    sb.clear()
                    // Stop if we have enough names and have passed the block
                    if (names.size >= maxCount) break
                }

                c.code in 32..126 -> {
                    sb.append(c)
                }

                else -> {
                    // Non-printable, non-null byte: if we haven't found names yet, skip. Otherwise stop.
                    if (names.isNotEmpty()) break
                    sb.clear()
                }
            }
            i++
        }
        // Flush remaining
        val s = sb.toString().trim()
        if (s.isNotEmpty() && s.all { it.code in 32..126 } && names.size < maxCount) {
            names.add(s)
        }
        return names
    }
}
