package com.locationjoystick.feature.settings.impl

import android.util.Log
import com.locationjoystick.core.common.util.parseGpxRoutes
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
 * Traverses Realm's B-tree and Group table directory to extract:
 * - Favorites: `class_PlaceLocationData` (name, latitude, longitude)
 * - Routes: `class_RouteData` (name, coordinates LinkList) + `class_CoordinateData` (latitude, longitude)
 *
 * Supports both pre-Cluster (format <= 9) and modern Cluster (format >= 10) Realm formats.
 * Speed profiles are not present in GPS Joystick exports.
 */
internal object GpsJoystickMigrator {
    private const val TAG = "GpsJoystickMigrator"

    private val WIDTH_TABLE = intArrayOf(0, 1, 2, 4, 8, 16, 32, 64)

    fun parse(bytes: ByteArray): Result<MigrationResult> =
        runCatching {
            // Newer GPS Joystick versions export GPX instead of the Realm .db format.
            if (looksLikeGpx(bytes)) return@runCatching parseGpx(bytes)
            val isRealm =
                bytes.size >= 20 &&
                    bytes[16] == 'T'.code.toByte() &&
                    bytes[17] == '-'.code.toByte() &&
                    bytes[18] == 'D'.code.toByte() &&
                    bytes[19] == 'B'.code.toByte()
            if (!isRealm) {
                return Result.failure(
                    IllegalArgumentException("Not a valid Realm database file (missing T-DB header)"),
                )
            }
            parseRealm(bytes)
        }.onFailure { e ->
            Log.e(TAG, "Failed to parse GPS Joystick database", e)
        }

    // -------------------------------------------------------------------------
    // GPX format (newer GPS Joystick exports)
    // -------------------------------------------------------------------------

    private fun looksLikeGpx(bytes: ByteArray): Boolean =
        bytes.copyOfRange(0, minOf(bytes.size, 512)).toString(Charsets.UTF_8).contains("<gpx", ignoreCase = true)

    /** Reuses the same GPX parsing the Routes screen's "Import GPX" uses (see issue #63). */
    private fun parseGpx(bytes: ByteArray): MigrationResult {
        val routes =
            parseGpxRoutes(bytes.toString(Charsets.UTF_8)).map { gpxRoute ->
                Route(
                    id = UUID.randomUUID().toString(),
                    name = gpxRoute.name,
                    waypoints =
                        gpxRoute.waypoints.mapIndexed { index, position ->
                            Waypoint(id = UUID.randomUUID().toString(), position = position, orderIndex = index)
                        },
                    isLooping = false,
                    routeType = RouteType.STRAIGHT,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            }
        return MigrationResult(favorites = emptyList(), routes = routes, walkSpeed = null, runSpeed = null, bikeSpeed = null)
    }

    // -------------------------------------------------------------------------
    // Structural Realm parser
    // -------------------------------------------------------------------------

    private fun parseRealm(bytes: ByteArray): MigrationResult {
        val topRef0 = readLongLE(bytes, 0)
        val topRef1 = readLongLE(bytes, 8)
        val flags = bytes[23].toInt() and 0xff
        val active = flags and 1
        var topRef = if (active == 1) topRef1 else topRef0
        if (topRef == -1L && bytes.size >= 40) {
            // Streaming form footer (last 16 bytes: 8 bytes top_ref + 8 bytes magic)
            topRef = readLongLE(bytes, bytes.size - 16)
        }
        if (topRef <= 0 || topRef + 8 > bytes.size) {
            return MigrationResult(emptyList(), emptyList(), null, null, null)
        }

        val rootHdr =
            parseArrayHeader(bytes, topRef.toInt())
                ?: return MigrationResult(emptyList(), emptyList(), null, null, null)
        if (!rootHdr.hasRefs || rootHdr.size < 2) {
            return MigrationResult(emptyList(), emptyList(), null, null, null)
        }
        val eb = rootHdr.elemBytes
        val schemaRef = readRef(bytes, topRef.toInt() + 8, 0, eb).toInt()
        val tablesRef = readRef(bytes, topRef.toInt() + 8, 1, eb).toInt()

        val schema = readStrings(bytes, schemaRef)
        val trHdr =
            parseArrayHeader(bytes, tablesRef)
                ?: return MigrationResult(emptyList(), emptyList(), null, null, null)
        if (!trHdr.hasRefs) {
            return MigrationResult(emptyList(), emptyList(), null, null, null)
        }
        val trEb = trHdr.elemBytes

        val tableRefs = mutableMapOf<String, Int>()
        for (i in 0 until trHdr.size) {
            val name = schema.getOrNull(i) ?: "table_$i"
            tableRefs[name] = readRef(bytes, tablesRef + 8, i, trEb).toInt()
        }

        // 1. Favorites (class_PlaceLocationData)
        val favorites = mutableListOf<FavoriteLocation>()
        val favRef = tableRefs["class_PlaceLocationData"] ?: 0
        if (favRef > 0) {
            val info = getTableInfo(bytes, favRef)
            if (info != null) {
                val namesIdx = info.columnNames.indexOf("name")
                val latIdx = info.columnNames.indexOf("latitude")
                val lonIdx = info.columnNames.indexOf("longitude")
                val sortIdx = info.columnNames.indexOf("sortOrder")
                val slotOffset = if (info.isCluster) 1 else 0

                val names = mutableListOf<String>()
                val lats = mutableListOf<Double>()
                val lons = mutableListOf<Double>()
                val sortOrders = mutableListOf<Int>()

                for (leaf in info.leaves) {
                    val lHdr = parseArrayHeader(bytes, leaf) ?: continue
                    val lEb = lHdr.elemBytes
                    if (namesIdx != -1) {
                        val ref = readRef(bytes, leaf + 8, namesIdx + slotOffset, lEb).toInt()
                        names.addAll(readStrings(bytes, ref))
                    }
                    if (latIdx != -1) {
                        val ref = readRef(bytes, leaf + 8, latIdx + slotOffset, lEb).toInt()
                        lats.addAll(readDoubles(bytes, ref))
                    }
                    if (lonIdx != -1) {
                        val ref = readRef(bytes, leaf + 8, lonIdx + slotOffset, lEb).toInt()
                        lons.addAll(readDoubles(bytes, ref))
                    }
                    if (sortIdx != -1) {
                        val ref = readRef(bytes, leaf + 8, sortIdx + slotOffset, lEb).toInt()
                        sortOrders.addAll(readIntArray(bytes, ref))
                    }
                }

                val baseTime = System.currentTimeMillis()
                val count = minOf(lats.size, lons.size)
                for (i in 0 until count) {
                    val name = names.getOrNull(i)?.takeIf { it.isNotBlank() } ?: "Favorite ${i + 1}"
                    val sortOrder = sortOrders.getOrNull(i)
                    val order = if (sortOrder != null && sortOrder >= 0) sortOrder else i
                    favorites.add(
                        FavoriteLocation(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            position = LatLng(latitude = lats[i], longitude = lons[i]),
                            createdAt = baseTime + order * 1000L,
                        ),
                    )
                }
            }
        }

        // 2. Routes (class_RouteData + class_CoordinateData)
        val routes = mutableListOf<Route>()
        val routeRef = tableRefs["class_RouteData"] ?: 0
        val coordRef = tableRefs["class_CoordinateData"] ?: 0
        if (routeRef > 0 && coordRef > 0) {
            val cInfo = getTableInfo(bytes, coordRef)
            val allLats = mutableListOf<Double>()
            val allLons = mutableListOf<Double>()
            if (cInfo != null) {
                val latIdx = cInfo.columnNames.indexOf("latitude")
                val lonIdx = cInfo.columnNames.indexOf("longitude")
                val slotOffset = if (cInfo.isCluster) 1 else 0
                for (leaf in cInfo.leaves) {
                    val lHdr = parseArrayHeader(bytes, leaf) ?: continue
                    val lEb = lHdr.elemBytes
                    if (latIdx != -1) {
                        val ref = readRef(bytes, leaf + 8, latIdx + slotOffset, lEb).toInt()
                        allLats.addAll(readDoubles(bytes, ref))
                    }
                    if (lonIdx != -1) {
                        val ref = readRef(bytes, leaf + 8, lonIdx + slotOffset, lEb).toInt()
                        allLons.addAll(readDoubles(bytes, ref))
                    }
                }
            }

            val rInfo = getTableInfo(bytes, routeRef)
            if (rInfo != null) {
                val nameIdx = rInfo.columnNames.indexOf("name")
                val coordsIdx = rInfo.columnNames.indexOf("coordinates")
                val sortIdx = rInfo.columnNames.indexOf("sortOrder")
                val slotOffset = if (rInfo.isCluster) 1 else 0
                val baseTime = System.currentTimeMillis()

                for (leaf in rInfo.leaves) {
                    val lHdr = parseArrayHeader(bytes, leaf) ?: continue
                    val lEb = lHdr.elemBytes
                    val rNames =
                        if (nameIdx != -1) {
                            val ref = readRef(bytes, leaf + 8, nameIdx + slotOffset, lEb).toInt()
                            readStrings(bytes, ref)
                        } else {
                            emptyList()
                        }
                    val rSortOrders =
                        if (sortIdx != -1) {
                            val ref = readRef(bytes, leaf + 8, sortIdx + slotOffset, lEb).toInt()
                            readIntArray(bytes, ref)
                        } else {
                            emptyList()
                        }

                    if (coordsIdx != -1) {
                        val coordsRef = readRef(bytes, leaf + 8, coordsIdx + slotOffset, lEb).toInt()
                        val crHdr = parseArrayHeader(bytes, coordsRef)
                        if (crHdr != null && crHdr.hasRefs) {
                            val crEb = crHdr.elemBytes
                            for (ri in 0 until crHdr.size) {
                                val rName = rNames.getOrNull(ri)?.takeIf { it.isNotBlank() } ?: "Route ${ri + 1}"
                                val sortOrder = rSortOrders.getOrNull(ri)
                                val order = if (sortOrder != null && sortOrder >= 0) sortOrder else routes.size
                                val linkRef = readRef(bytes, coordsRef + 8, ri, crEb).toInt()
                                val indices = if (linkRef > 0) readUintArray(bytes, linkRef) else emptyList()
                                val waypoints =
                                    indices.mapIndexedNotNull { wi, idx ->
                                        if (idx in allLats.indices && idx in allLons.indices) {
                                            Waypoint(
                                                id = UUID.randomUUID().toString(),
                                                position = LatLng(latitude = allLats[idx], longitude = allLons[idx]),
                                                orderIndex = wi,
                                            )
                                        } else {
                                            null
                                        }
                                    }
                                val routeTime = baseTime + order * 1000L
                                routes.add(
                                    Route(
                                        id = UUID.randomUUID().toString(),
                                        name = rName,
                                        waypoints = waypoints,
                                        isLooping = false,
                                        routeType = RouteType.STRAIGHT,
                                        createdAt = routeTime,
                                        updatedAt = routeTime,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }

        return MigrationResult(
            favorites = favorites,
            routes = routes,
            walkSpeed = null,
            runSpeed = null,
            bikeSpeed = null,
        )
    }

    private data class TableInfo(
        val columnNames: List<String>,
        val isCluster: Boolean,
        val leaves: List<Int>,
    )

    private fun getTableInfo(
        bytes: ByteArray,
        tableRef: Int,
    ): TableInfo? {
        if (tableRef <= 0 || tableRef + 8 > bytes.size) return null
        val tHdr = parseArrayHeader(bytes, tableRef) ?: return null
        if (!tHdr.hasRefs || tHdr.size < 2) return null
        val tEb = tHdr.elemBytes
        val specRef = readRef(bytes, tableRef + 8, 0, tEb).toInt()
        val sHdr = parseArrayHeader(bytes, specRef) ?: return null
        if (!sHdr.hasRefs || sHdr.size < 2) return null
        val sEb = sHdr.elemBytes
        val namesRef = readRef(bytes, specRef + 8, 1, sEb).toInt()
        val colNames = readStrings(bytes, namesRef)

        val isCluster = tHdr.size >= 3
        val leaves =
            if (isCluster) {
                val clusterRoot = readRef(bytes, tableRef + 8, 2, tEb).toInt()
                walkClusterLeaves(bytes, clusterRoot)
            } else {
                val colsRef = readRef(bytes, tableRef + 8, 1, tEb).toInt()
                listOf(colsRef)
            }

        return TableInfo(columnNames = colNames, isCluster = isCluster, leaves = leaves)
    }

    private fun walkClusterLeaves(
        bytes: ByteArray,
        rootRef: Int,
    ): List<Int> {
        if (rootRef <= 0 || rootRef + 8 > bytes.size) return emptyList()
        val hdr = parseArrayHeader(bytes, rootRef) ?: return emptyList()
        if (!hdr.hasRefs) return emptyList()
        val isInner = (hdr.flags and 0x80) != 0
        if (!isInner) return listOf(rootRef)
        val eb = hdr.elemBytes
        if (eb < 1) return emptyList()
        val leaves = mutableListOf<Int>()
        for (i in 3 until hdr.size) {
            val childRef = readRef(bytes, rootRef + 8, i, eb).toInt()
            if (childRef > 0) {
                leaves.addAll(walkClusterLeaves(bytes, childRef))
            }
        }
        return leaves
    }

    // -------------------------------------------------------------------------
    // Low-level Realm binary decoders
    // -------------------------------------------------------------------------

    private data class ArrayHeader(
        val flags: Int,
        val size: Int,
        val width: Int,
        val widthScheme: Int,
        val elemBytes: Int,
        val hasRefs: Boolean,
    )

    private fun parseArrayHeader(
        bytes: ByteArray,
        offset: Int,
    ): ArrayHeader? {
        if (offset < 0 || offset + 8 > bytes.size) return null
        if (bytes[offset] != 0x41.toByte() ||
            bytes[offset + 1] != 0x41.toByte() ||
            bytes[offset + 2] != 0x41.toByte() ||
            bytes[offset + 3] != 0x41.toByte()
        ) {
            return null
        }
        val flags = bytes[offset + 4].toInt() and 0xff
        val size = readSize(bytes, offset + 5)
        val widthScheme = (flags ushr 3) and 3
        val widthNdx = flags and 7
        val width = if (widthNdx in WIDTH_TABLE.indices) WIDTH_TABLE[widthNdx] else 0
        val elemBytes =
            when (widthScheme) {
                0 -> if (width >= 8) width / 8 else 0
                1 -> width
                else -> 0
            }
        val hasRefs = (flags and 0x40) != 0
        return ArrayHeader(flags, size, width, widthScheme, elemBytes, hasRefs)
    }

    private fun readRef(
        bytes: ByteArray,
        payloadStart: Int,
        index: Int,
        elemBytes: Int,
    ): Long {
        val off = payloadStart + index * elemBytes
        if (elemBytes < 1 || off < 0 || off + elemBytes > bytes.size) return -1L
        var value = 0L
        for (b in 0 until elemBytes) {
            value = value or ((bytes[off + b].toLong() and 0xffL) shl (b * 8))
        }
        return value
    }

    private fun readUintArray(
        bytes: ByteArray,
        offset: Int,
    ): List<Int> = readIntArray(bytes, offset, signed = false)

    private fun readIntArray(
        bytes: ByteArray,
        offset: Int,
        signed: Boolean = true,
    ): List<Int> {
        val hdr = parseArrayHeader(bytes, offset) ?: return emptyList()
        val count = hdr.size
        val width = hdr.width
        if (count <= 0) return emptyList()
        if (width <= 0) return List(count) { 0 }
        val payloadStart = offset + 8
        val result = ArrayList<Int>(count)
        val signBit = if (signed && width in 1..64) 1L shl (width - 1) else 0L
        if (hdr.widthScheme == 0) {
            val mask = if (width >= 64) -1L else (1L shl width) - 1L
            for (i in 0 until count) {
                val bitOffset = i * width
                val byteOffset = payloadStart + (bitOffset / 8)
                val eb = (bitOffset % 8 + width + 7) / 8
                if (byteOffset + eb > bytes.size) break
                var v = 0L
                for (b in 0 until eb) {
                    v = v or ((bytes[byteOffset + b].toLong() and 0xffL) shl (b * 8))
                }
                var value = (v ushr (bitOffset % 8)) and mask
                if (width >= 8 && signBit != 0L && value >= signBit) {
                    value -= (1L shl width)
                }
                result.add(value.toInt())
            }
        } else if (hdr.widthScheme == 1) {
            val bitWidth = width * 8
            val mask = if (bitWidth >= 64) -1L else (1L shl bitWidth) - 1L
            val sb = if (signed && bitWidth in 8..64) 1L shl (bitWidth - 1) else 0L
            for (i in 0 until count) {
                val byteOffset = payloadStart + i * width
                if (byteOffset + width > bytes.size) break
                var v = 0L
                for (b in 0 until width) {
                    v = v or ((bytes[byteOffset + b].toLong() and 0xffL) shl (b * 8))
                }
                var value = v and mask
                if (sb != 0L && value >= sb) {
                    value -= (1L shl bitWidth)
                }
                result.add(value.toInt())
            }
        }
        return result
    }

    private fun readStrings(
        bytes: ByteArray,
        offset: Int,
    ): List<String> {
        val hdr = parseArrayHeader(bytes, offset) ?: return emptyList()
        // 1. ArrayStringShort (0x0D inline string array: !hasRefs)
        if (!hdr.hasRefs) {
            val width = hdr.width
            val count = hdr.size
            if (width <= 0) return List(count) { "" }
            val payloadStart = offset + 8
            val result = ArrayList<String>(count)
            for (i in 0 until count) {
                val entryStart = payloadStart + i * width
                if (entryStart + width > bytes.size) break
                val pad = bytes[entryStart + width - 1].toInt() and 0xff
                val len = maxOf(0, width - 1 - pad)
                result.add(String(bytes, entryStart, len, Charsets.UTF_8))
            }
            return result
        }
        // 2. ArraySmallBlobs / ArrayStringLong (hasRefs, size 2 or 3)
        if (hdr.hasRefs && hdr.size in 2..3) {
            val eb = hdr.elemBytes
            val offsetsRef = readRef(bytes, offset + 8, 0, eb).toInt()
            val blobRef = readRef(bytes, offset + 8, 1, eb).toInt()
            if (offsetsRef <= 0 || blobRef <= 0) return emptyList()
            val offsets = readUintArray(bytes, offsetsRef)
            val bHdr = parseArrayHeader(bytes, blobRef) ?: return emptyList()
            val blobSize = bHdr.size
            val blobStart = blobRef + 8
            if (blobStart + blobSize > bytes.size) return emptyList()
            val result = ArrayList<String>(offsets.size)
            var prev = 0
            for (end in offsets) {
                if (end < prev || prev >= blobSize) {
                    result.add("")
                    continue
                }
                val sliceEnd = minOf(end, blobSize)
                var len = sliceEnd - prev
                if (len > 0 && bytes[blobStart + prev + len - 1] == 0.toByte()) {
                    len--
                }
                result.add(if (len > 0) String(bytes, blobStart + prev, len, Charsets.UTF_8) else "")
                prev = end
            }
            return result
        }
        return emptyList()
    }

    private fun readDoubles(
        bytes: ByteArray,
        offset: Int,
    ): List<Double> {
        val hdr = parseArrayHeader(bytes, offset) ?: return emptyList()
        val count = hdr.size
        val payloadStart = offset + 8
        if (payloadStart + count * 8 > bytes.size) return emptyList()
        val buf = ByteBuffer.wrap(bytes, payloadStart, count * 8).order(ByteOrder.LITTLE_ENDIAN)
        return (0 until count).map { buf.double }
    }

    private fun readSize(
        bytes: ByteArray,
        offset: Int,
    ): Int {
        if (offset < 0 || offset + 3 > bytes.size) return 0
        return ((bytes[offset].toInt() and 0xff) shl 16) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            (bytes[offset + 2].toInt() and 0xff)
    }

    private fun readLongLE(
        bytes: ByteArray,
        offset: Int,
    ): Long {
        if (offset < 0 || offset + 8 > bytes.size) return 0L
        return ByteBuffer.wrap(bytes, offset, 8).order(ByteOrder.LITTLE_ENDIAN).long
    }
}
