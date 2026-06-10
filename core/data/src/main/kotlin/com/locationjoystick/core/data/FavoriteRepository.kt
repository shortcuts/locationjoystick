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

/**
 * A curated hot location entry. The [name] is the stable identity key — do not rename
 * existing entries (the ID is derived from the name). [country] and [city] are used for
 * UI grouping only and do not affect storage identity.
 */
data class HotLocation(
    val name: String,
    val lat: Double,
    val lon: Double,
    val country: String,
    val city: String,
)

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
        suspend fun upsertHotLocations(
            selectedIds: Set<String> = HOT_LOCATIONS.map { idForLocation(it.name, it.city) }.toSet(),
        ): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    HOT_LOCATIONS.forEach { location ->
                        val id = idForLocation(location.name, location.city)
                        if (id in selectedIds) {
                            val existing = favoriteDao.getById(id)
                            if (existing != null) {
                                favoriteDao.update(existing.copy(latitude = location.lat, longitude = location.lon))
                            } else {
                                favoriteDao.insert(
                                    FavoriteEntity(
                                        id = id,
                                        name = location.name,
                                        latitude = location.lat,
                                        longitude = location.lon,
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

            fun idForLocation(
                name: String,
                city: String,
            ): String = HOT_ID_PREFIX + "$name $city".lowercase().replace(Regex("[^a-z0-9]"), "_")

            val HOT_LOCATIONS =
                listOf(
                    HotLocation("Pago Pago", -14.27859, -170.68886, "American Samoa", "Pago Pago"),
                    HotLocation("Circular Quay, Sydney", -33.861756, 151.210883, "Australia", "Sydney"),
                    HotLocation("Melbourne", -37.81751, 144.96330, "Australia", "Melbourne"),
                    HotLocation("Sydney", -33.868820, 151.209296, "Australia", "Sydney"),
                    HotLocation("Cemetery in Brazil", -23.549616, -46.655418, "Brazil", "São Paulo"),
                    HotLocation("Indaial", -26.89304, -49.22998, "Brazil", "Indaial"),
                    HotLocation("Porto Alegre", -30.034647, -51.217658, "Brazil", "Porto Alegre"),
                    HotLocation("São Paulo", -23.58659, -46.65702, "Brazil", "São Paulo"),
                    HotLocation("Go Fest City Experience", 55.684220, 12.580021, "Denmark", "Copenhagen"),
                    HotLocation("Go Fest Park", 55.701744, 12.569932, "Denmark", "Copenhagen"),
                    HotLocation("Jardin des Tuileries", 48.862796, 2.329297, "France", "Paris"),
                    HotLocation("Jardin du Luxembourg", 48.846574, 2.337258, "France", "Paris"),
                    HotLocation("Thessaloniki", 40.62773, 22.95471, "Greece", "Thessaloniki"),
                    HotLocation("Budapest", 47.52876, 19.05138, "Hungary", "Budapest"),
                    HotLocation("Marine Drive, Mumbai", 18.943000, 72.823800, "India", "Mumbai"),
                    HotLocation("Sector 62, Noida", 28.682562, 77.350067, "India", "Noida"),
                    HotLocation("Expo '70 Commemorative Park, Osaka", 34.6664, 135.5006, "Japan", "Osaka"),
                    HotLocation("Osaka", 34.70246, 135.50024, "Japan", "Osaka"),
                    HotLocation("9 Stops Japan", 35.6961, 139.8144, "Japan", "Tokyo"),
                    HotLocation("Shibuya, Tokyo", 35.661777, 139.704051, "Japan", "Tokyo"),
                    HotLocation("Tokyo Disneyland", 35.6312, 139.8809, "Japan", "Tokyo"),
                    HotLocation("Tottori Sand Dunes", 35.5391, 134.2351, "Japan", "Tottori"),
                    HotLocation("Kiritimati", 1.98715, -157.47714, "Kiribati", "Kiritimati"),
                    HotLocation("Aotea Square, Auckland", -36.852095, 174.763180, "New Zealand", "Auckland"),
                    HotLocation("Wellington Botanical Gardens", -41.28141, 174.76649, "New Zealand", "Wellington"),
                    HotLocation("Singapore", 1.288719, 103.848742, "Singapore", "Singapore"),
                    HotLocation("Dongdaemun Design Plaza", 37.566800, 127.009370, "South Korea", "Seoul"),
                    HotLocation("Parc de les Cordelles, Barcelona", 41.4925, 2.1353, "Spain", "Barcelona"),
                    HotLocation("Jabalquinto", 38.096122, -3.626411, "Spain", "Jabalquinto"),
                    HotLocation("Canary Islands", 28.12890, -15.43390, "Spain", "Las Palmas"),
                    HotLocation("Santa Cruz", 28.48952, -16.31807, "Spain", "Santa Cruz de Tenerife"),
                    HotLocation("Zaragoza", 41.661342, -0.892832, "Spain", "Zaragoza"),
                    HotLocation("Taipei", 25.05544, 121.52250, "Taiwan", "Taipei"),
                    HotLocation("Ximending / Taipei Gym Cluster", 25.0466, 121.5169, "Taiwan", "Taipei"),
                    HotLocation("Antalya", 36.87944, 30.70900, "Turkey", "Antalya"),
                    HotLocation("Bornova Buyuk Park", 38.46314, 27.21649, "Turkey", "Izmir"),
                    HotLocation("Dubai", 25.07675, 55.13294, "United Arab Emirates", "Dubai"),
                    HotLocation("UK", 53.190085, -2.89158, "United Kingdom", "Cheshire"),
                    HotLocation("Big Ben, London", 51.510357, -0.116773, "United Kingdom", "London"),
                    HotLocation("Go Fest City Experience", 41.891797, -87.611083, "United States", "Chicago"),
                    HotLocation("Go Fest Collection District", 41.877598, -87.62369, "United States", "Chicago"),
                    HotLocation("Go Fest Friendship District", 41.828436, -87.633616, "United States", "Chicago"),
                    HotLocation("Go Fest Investigation District", 41.916267, -87.63231, "United States", "Chicago"),
                    HotLocation("Go Fest Park", 41.875549, -87.619066, "United States", "Chicago"),
                    HotLocation("Go Fest Scouting District", 41.861967, -87.663436, "United States", "Chicago"),
                    HotLocation("Millennium Park", 41.886473, -87.626356, "United States", "Chicago"),
                    HotLocation("Ala Moana Center", 21.291, -157.844, "United States", "Honolulu"),
                    HotLocation("Honolulu", 21.29836, -157.86011, "United States", "Honolulu"),
                    HotLocation("Bryant Park", 40.7537, -73.9835, "United States", "New York City"),
                    HotLocation("Central Park", 40.785091, -73.968285, "United States", "New York City"),
                    HotLocation("Times Square", 40.7589, -73.9851, "United States", "New York City"),
                    HotLocation("Niantic HQ", 37.789464, -122.401621, "United States", "San Francisco"),
                    HotLocation("Pier 39", 37.808673, -122.409821, "United States", "San Francisco"),
                    HotLocation("Union Square", 37.787993, -122.407437, "United States", "San Francisco"),
                )
        }
    }
