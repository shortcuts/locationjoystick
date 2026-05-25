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
 * Extracts favorites (class_PlaceLocationData) and routes (class_RouteData +
 * class_CoordinateData). Speed profiles are not present in GPS Joystick exports.
 *
 * ## Binary format notes
 *
 * - `T-DB` magic at byte offset 16.
 * - Double arrays: 8-byte header `41 41 41 41 0C xx xx <count>` followed by `count` little-endian
 *   doubles.  Count is stored in byte 7 (the 8th byte) of the header.
 * - String arrays: 8-byte header `41 41 41 41 0D xx xx <count>` followed by `count`
 *   null-terminated UTF-8 strings, each entry padded to a 16-byte boundary.
 *
 * ## Parsing strategy
 *
 * 1. Scan for string arrays (0x0D type).  Filter out schema arrays (column-name tables).
 * 2. Scan for double arrays (0x0C type).  Find consecutive same-count pairs whose values are
 *    all plausible geographic coordinates (abs ≤ 180, non-zero).
 * 3. Assign coord pairs: if two pairs exist the larger is routes, the smaller is favorites.
 *    If only one pair exists and a matching-count string array is present → favorites only;
 *    otherwise → routes only.
 * 4. Match string arrays to favorites/routes by count.
 * 5. Fall back to `Favorite N` / `Route N` default names when named arrays are absent.
 */
internal object GpsJoystickMigrator {
    private const val TAG = "GpsJoystickMigrator"

    private val REALM_HEADER = "T-DB".toByteArray(Charsets.US_ASCII)
    private const val REALM_HEADER_OFFSET = 16
    private const val ARRAY_HEADER_SIZE = 8

    // 5-byte prefixes that identify Realm array headers.
    private val DOUBLE_ARRAY_PREFIX = byteArrayOf(0x41, 0x41, 0x41, 0x41, 0x0c.toByte())
    private val STRING_ARRAY_PREFIX = byteArrayOf(0x41, 0x41, 0x41, 0x41, 0x0d.toByte())

    /** Column names present in Realm schema tables — used to skip metadata arrays. */
    private val SCHEMA_NAMES =
        setOf(
            "id",
            "name",
            "latitude",
            "longitude",
            "altitude",
            "coordinates",
            "typeId",
            "address",
            "sortOrder",
            "pk_table",
            "pk_property",
            "parentFolderId",
            "type",
            "folderId",
        )

    fun parse(bytes: ByteArray): Result<MigrationResult> =
        runCatching {
            if (!hasRealmHeader(bytes)) {
                return Result.failure(
                    IllegalArgumentException("Not a valid Realm database file (missing T-DB header)"),
                )
            }
            val dataStringArrays = findDataStringArrays(bytes)
            val coordPairs = findCoordPairs(bytes)
            val (favorites, routes) = extractFavoritesAndRoutes(bytes, dataStringArrays, coordPairs)
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

    // -------------------------------------------------------------------------
    // Top-level extraction
    // -------------------------------------------------------------------------

    private data class CoordPair(
        val lats: List<Double>,
        val lons: List<Double>,
    ) {
        val count: Int get() = lats.size
    }

    private fun extractFavoritesAndRoutes(
        bytes: ByteArray,
        dataArrays: List<List<String>>,
        coordPairs: List<CoordPair>,
    ): Pair<List<FavoriteLocation>, List<Route>> {
        if (coordPairs.isEmpty()) return emptyList<FavoriteLocation>() to emptyList()

        val favPair: CoordPair?
        val routePair: CoordPair?

        if (coordPairs.size >= 2) {
            val sorted = coordPairs.sortedBy { it.count }
            favPair = sorted.first()
            routePair = sorted.last()
        } else {
            val pair = coordPairs.first()
            val hasMatchingNameArray = dataArrays.any { it.size == pair.count }
            if (hasMatchingNameArray) {
                favPair = pair
                routePair = null
            } else {
                favPair = null
                routePair = pair
            }
        }

        val favCount = favPair?.count ?: 0
        val routeTotal = routePair?.count ?: 0

        // Find the name array that matches fav count
        val favNameArray = dataArrays.firstOrNull { it.size == favCount && favCount > 0 }

        // Find route name array: a data array whose count ≠ favCount
        val routeNameArray =
            if (routePair != null) {
                val candidates = dataArrays.filter { it.size != favCount }
                // Prefer a candidate whose count evenly divides route total
                candidates.firstOrNull { routeTotal % it.size == 0 } ?: candidates.lastOrNull()
            } else {
                null
            }

        val favorites = buildFavorites(favPair, favNameArray)
        val routes = buildRoutes(routePair, routeNameArray)
        return favorites to routes
    }

    // -------------------------------------------------------------------------
    // Favorites
    // -------------------------------------------------------------------------

    private fun buildFavorites(
        pair: CoordPair?,
        nameArray: List<String>?,
    ): List<FavoriteLocation> {
        if (pair == null || pair.count == 0) return emptyList()
        return (0 until pair.count).map { i ->
            FavoriteLocation(
                id = UUID.randomUUID().toString(),
                name = nameArray?.getOrNull(i) ?: "Favorite ${i + 1}",
                position = LatLng(latitude = pair.lats[i], longitude = pair.lons[i]),
                createdAt = System.currentTimeMillis(),
            )
        }
    }

    // -------------------------------------------------------------------------
    // Routes
    // -------------------------------------------------------------------------

    private fun buildRoutes(
        pair: CoordPair?,
        nameArray: List<String>?,
    ): List<Route> {
        if (pair == null || pair.count < 1) return emptyList()
        val totalWaypoints = pair.count
        // Fall back to a single unnamed route when the waypoint count is less than the
        // number of named routes (can happen with partial/corrupt exports).
        val routeCount = if (nameArray != null && totalWaypoints >= nameArray.size) nameArray.size else 1
        val base = totalWaypoints / routeCount

        return (0 until routeCount).map { ri ->
            val start = ri * base
            val end = if (ri == routeCount - 1) totalWaypoints else start + base
            val waypoints =
                (start until end).mapIndexed { wi, idx ->
                    Waypoint(
                        id = UUID.randomUUID().toString(),
                        position = LatLng(latitude = pair.lats[idx], longitude = pair.lons[idx]),
                        orderIndex = wi,
                    )
                }
            Route(
                id = UUID.randomUUID().toString(),
                name = nameArray?.getOrNull(ri) ?: "Route ${ri + 1}",
                waypoints = waypoints,
                isLooping = false,
                routeType = RouteType.STRAIGHT,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    // -------------------------------------------------------------------------
    // String array scanning
    // -------------------------------------------------------------------------

    /**
     * Scans the file for Realm string arrays (0x0D type) and returns only those that
     * are not schema/column-name tables.
     */
    private fun findDataStringArrays(bytes: ByteArray): List<List<String>> {
        val result = mutableListOf<List<String>>()
        var pos = 0
        while (pos <= bytes.size - ARRAY_HEADER_SIZE) {
            if (prefixAt(bytes, pos, STRING_ARRAY_PREFIX)) {
                val count = bytes[pos + 7].toInt() and 0xff
                if (count > 0) {
                    val strings = readStringArray(bytes, pos + ARRAY_HEADER_SIZE, count)
                    if (strings.size == count && strings.none { it in SCHEMA_NAMES }) {
                        result.add(strings)
                    }
                }
                pos += ARRAY_HEADER_SIZE
            } else {
                pos++
            }
        }
        return result
    }

    /**
     * Reads [count] null-terminated UTF-8 strings starting at [dataStart].
     * Each string occupies a 16-byte-aligned slot within the array body.
     */
    private fun readStringArray(
        bytes: ByteArray,
        dataStart: Int,
        count: Int,
    ): List<String> {
        val strings = mutableListOf<String>()
        var pos = dataStart
        repeat(count) {
            val nullPos = bytes.indexOf(0.toByte(), pos)
            if (nullPos == -1 || nullPos >= bytes.size) return strings
            val s = bytes.copyOfRange(pos, nullPos).toString(Charsets.UTF_8)
            strings.add(s)
            val consumed = nullPos - dataStart + 1 // bytes consumed since array start
            val rem = consumed % 16
            pos = dataStart + consumed + if (rem == 0) 0 else 16 - rem
        }
        return strings
    }

    private fun ByteArray.indexOf(
        value: Byte,
        fromIndex: Int,
    ): Int {
        for (i in fromIndex until size) if (this[i] == value) return i
        return -1
    }

    // -------------------------------------------------------------------------
    // Double array scanning
    // -------------------------------------------------------------------------

    /**
     * Scans for consecutive same-count double array pairs whose values are all plausible
     * geographic coordinates (|v| ≤ 180, non-zero, finite).
     */
    private fun findCoordPairs(bytes: ByteArray): List<CoordPair> {
        val coordBlocks =
            findDoubleArrayBlocks(bytes)
                .filter { block -> block.isNotEmpty() && block.all { v -> v.isFinite() && v != 0.0 && Math.abs(v) <= 180.0 } }

        val pairs = mutableListOf<CoordPair>()
        var i = 0
        while (i < coordBlocks.size - 1) {
            val a = coordBlocks[i]
            val b = coordBlocks[i + 1]
            if (a.size == b.size) {
                pairs.add(CoordPair(lats = a, lons = b))
                i += 2
            } else {
                i++
            }
        }
        return pairs
    }

    private fun findDoubleArrayBlocks(bytes: ByteArray): List<List<Double>> {
        val result = mutableListOf<List<Double>>()
        var pos = 0
        while (pos <= bytes.size - ARRAY_HEADER_SIZE) {
            if (prefixAt(bytes, pos, DOUBLE_ARRAY_PREFIX)) {
                val count = bytes[pos + 7].toInt() and 0xff
                if (count > 0) {
                    val doubles = readDoubles(bytes, pos + ARRAY_HEADER_SIZE, count)
                    if (doubles.size == count) result.add(doubles)
                }
                pos += ARRAY_HEADER_SIZE
            } else {
                pos++
            }
        }
        return result
    }

    private fun readDoubles(
        bytes: ByteArray,
        dataStart: Int,
        count: Int,
    ): List<Double> {
        val end = dataStart + count * 8
        if (end > bytes.size) return emptyList()
        val buf = ByteBuffer.wrap(bytes, dataStart, count * 8).order(ByteOrder.LITTLE_ENDIAN)
        return (0 until count).map { buf.double }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun hasRealmHeader(bytes: ByteArray): Boolean {
        if (bytes.size < REALM_HEADER_OFFSET + REALM_HEADER.size) return false
        return REALM_HEADER.indices.all { i -> bytes[REALM_HEADER_OFFSET + i] == REALM_HEADER[i] }
    }

    private fun prefixAt(
        bytes: ByteArray,
        offset: Int,
        prefix: ByteArray,
    ): Boolean {
        if (offset + prefix.size > bytes.size) return false
        return prefix.indices.all { i -> bytes[offset + i] == prefix[i] }
    }
}
