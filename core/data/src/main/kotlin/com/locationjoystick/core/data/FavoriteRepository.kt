package com.locationjoystick.core.data

import android.util.Log
import com.locationjoystick.core.database.dao.FavoriteDao
import com.locationjoystick.core.database.entities.FavoriteEntity
import com.locationjoystick.core.database.entities.toDomain
import com.locationjoystick.core.database.entities.toEntity
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FavoriteRepository"

@Singleton
class FavoriteRepository
    @Inject
    constructor(
        private val favoriteDao: FavoriteDao,
    ) {
        fun getFavorites(): Flow<List<FavoriteLocation>> = favoriteDao.getAll().map { list -> list.map { it.toDomain() } }

        suspend fun addFavorite(
            id: String,
            name: String,
            position: LatLng,
            createdAt: Long = System.currentTimeMillis(),
        ): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val favorite =
                        FavoriteLocation(
                            id = id,
                            name = name,
                            position = position,
                            createdAt = createdAt,
                        )
                    favoriteDao.insert(favorite.toEntity())
                }.onFailure { e ->
                    Log.e(TAG, "Failed to add favorite: $name", e)
                }
            }

        suspend fun updateFavorite(favorite: FavoriteLocation): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    favoriteDao.update(favorite.toEntity())
                }.onFailure { e ->
                    Log.e(TAG, "Failed to update favorite: ${favorite.id}", e)
                }
            }

        suspend fun deleteFavorite(id: String): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val entity = favoriteDao.getById(id)
                    if (entity != null) {
                        favoriteDao.delete(entity)
                    }
                }.onFailure { e ->
                    Log.e(TAG, "Failed to delete favorite: $id", e)
                }
            }

        suspend fun deleteAllFavorites(): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    favoriteDao.deleteAll()
                }.onFailure { e ->
                    Log.e(TAG, "Failed to delete all favorites", e)
                }
            }

        /**
         * Upserts all hot locations from [HOT_LOCATIONS] into the favorites table.
         *
         * ## ID Derivation Strategy
         *
         * Hot location IDs are deterministically derived from their names:
         * ```
         * id = HOT_ID_PREFIX + name.lowercase().replace(Regex("[^a-z0-9]"), "_")
         * ```
         * Examples:
         * - "Osaka Tokyo" → "hot_osaka_tokyo"
         * - "Sydney" → "hot_sydney"
         * - "Bryant Park NY" → "hot_bryant_park_ny"
         *
         * This enables idempotent upserts (update if exists by ID, insert if not).
         *
         * ## Stale Entry Risk: Name Changes Across Versions
         *
         * **Problem:** If a hot location name is corrected in a future app version
         * (e.g., "Osaka Tokyo" → "Osaka"), the old-derived ID persists:
         *
         * 1. User has app v1 with "Osaka Tokyo" → "hot_osaka_tokyo" inserted
         * 2. User updates to app v2 with corrected name "Osaka" → ID becomes "hot_osaka"
         * 3. `upsertHotLocations()` calls `favoriteDao.getById("hot_osaka")` → null (new name)
         * 4. Inserts new favorite with ID "hot_osaka"
         * 5. Old "hot_osaka_tokyo" entry is NOT deleted — it's orphaned
         *
         * The upsert uses [name] matching (via ID derivation), not ID lookup, so it
         * cannot detect the name change. Removal logic in [removeHotLocations] only
         * deletes entries with IDs starting with [HOT_ID_PREFIX] "hot_", so both entries
         * remain until the user manually deletes the stale entry (rare).
         *
         * **Risk Level:** LOW. The [HOT_LOCATIONS] list is stable and curated. Name
         * corrections are infrequent and unlikely.
         *
         * **Mitigation:** Avoid changing existing hot location names without manual
         * cleanup. If a name must change in the code, consider deleting the old entry
         * from [HOT_LOCATIONS] instead of renaming (forcing a fresh insertion).
         *
         * ## See Also
         * - [removeHotLocations] — deletes all hot location entries
         * - [HOT_LOCATIONS] — the canonical list of hot location definitions
         * - ISSUES.md (lines 75–87) — full technical debt entry
         */
        suspend fun upsertHotLocations(selectedIds: Set<String> = HOT_LOCATIONS.map { (name, _, _) -> idForName(name) }.toSet()): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    HOT_LOCATIONS.forEach { (name, lat, lon) ->
                        val id = idForName(name)
                        if (id in selectedIds) {
                            val existing = favoriteDao.getById(id)
                            if (existing != null) {
                                favoriteDao.update(existing.copy(latitude = lat, longitude = lon))
                            } else {
                                favoriteDao.insert(
                                    FavoriteEntity(
                                        id = id,
                                        name = name,
                                        latitude = lat,
                                        longitude = lon,
                                        createdAt = System.currentTimeMillis(),
                                    ),
                                )
                            }
                        } else {
                            val existing = favoriteDao.getById(id)
                            if (existing != null) favoriteDao.delete(existing)
                        }
                    }
                }.onFailure { e -> Log.e(TAG, "Failed to upsert hot locations", e) }
            }

        suspend fun removeHotLocations(): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    favoriteDao.deleteHotLocations()
                }.onFailure { e -> Log.e(TAG, "Failed to remove hot locations", e) }
            }

        companion object {
            private const val HOT_ID_PREFIX = "hot_"

            fun idForName(name: String): String = HOT_ID_PREFIX + name.lowercase().replace(Regex("[^a-z0-9]"), "_")

            /**
             * Curated list of hot favorite locations bundled with the app.
             *
             * Each location is upserted via [upsertHotLocations] when the user enables
             * the "Show hot locations" feature in Settings. Locations are matched by
             * their derived ID (see [upsertHotLocations] for ID derivation logic).
             *
             * ## Adding New Locations
             *
             * Append new `Triple("Name", latitude, longitude)` entries to the list.
             * The name will be converted to a deterministic ID:
             * ```
             * "Bryant Park NY" → "hot_bryant_park_ny"
             * ```
             *
             * ## Changing Existing Locations
             *
             * - **Updating coordinates:** Simply change the lat/lon. The name stays the same,
             *   so the ID remains unchanged. Existing entries are updated in-place.
             * - **Renaming a location:** Creates a new ID, so the old entry orphans (see
             *   [upsertHotLocations] "Stale Entry Risk" section). To avoid orphans:
             *   1. Delete the old entry from this list (removes it from the bundle)
             *   2. Add the new entry with the corrected name
             *   3. Users who enable hot locations will get a fresh insert of the corrected name
             *
             * ## See Also
             * - [upsertHotLocations] — full technical debt entry with risk analysis
             * - ISSUES.md (lines 75–87) — additional context on stale entry accumulation
             */
            val HOT_LOCATIONS =
                listOf(
                    Triple("Kiritimati", 1.98715, -157.47714),
                    Triple("Wellington Botanical Gardens", -41.28141, 174.76649),
                    Triple("Melbourne Australia", -37.81751, 144.96330),
                    Triple("Sydney", -33.868820, 151.209296),
                    Triple("Osaka Tokyo", 34.70246, 135.50024),
                    Triple("Taipei Taiwan", 25.05544, 121.52250),
                    Triple("Singapore", 1.288719, 103.848742),
                    Triple("Antalya Turkey", 36.87944, 30.70900),
                    Triple("Bornova Buyuk Park Turkey", 38.46314, 27.21649),
                    Triple("Budapest", 47.52876, 19.05138),
                    Triple("Canary Islands", 28.12890, -15.43390),
                    Triple("Dubai", 25.07675, 55.13294),
                    Triple("Thessaloniki Greece", 40.62773, 22.95471),
                    Triple("UK", 53.190085, -2.89158),
                    Triple("Zaragoza Spain", 41.661342, -0.892832),
                    Triple("Santa Cruz Spain", 28.48952, -16.31807),
                    Triple("Indaial Brazil", -26.89304, -49.22998),
                    Triple("Sao Paulo Brazil", -23.58659, -46.65702),
                    Triple("Bryant Park NY", 40.7537, -73.9835),
                    Triple("Times Square NYC", 40.7589, -73.9851),
                    Triple("Millennium Park Chicago", 41.886473, -87.626356),
                    Triple("Niantic HQ", 37.789464, -122.401621),
                    Triple("SF Union Square", 37.787993, -122.407437),
                    Triple("Pier 39 California", 37.808673, -122.409821),
                    Triple("Honolulu Hawaii", 21.29836, -157.86011),
                    Triple("Pago Pago", -14.27859, -170.68886),
                    Triple("Chicago 2026 Fest Park", 41.875549, -87.619066),
                    Triple("Chicago City Experience", 41.891797, -87.611083),
                    Triple("Chicago Friendship District", 41.828436, -87.633616),
                    Triple("Chicago Scouting District", 41.861967, -87.663436),
                    Triple("Chicago Investigation District", 41.916267, -87.63231),
                    Triple("Chicago Collection District", 41.877598, -87.62369),
                )
        }
    }
