package com.locationjoystick.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.locationjoystick.core.model.SavedItemSortMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SavedItemOrderPreferences {
    fun getRoutesSortNewestFirst(): Flow<Boolean>

    suspend fun setRoutesSortNewestFirst(newestFirst: Boolean)

    fun getFavoritesSortNewestFirst(): Flow<Boolean>

    suspend fun setFavoritesSortNewestFirst(newestFirst: Boolean)

    fun getRoutesSortMode(): Flow<SavedItemSortMode> =
        getRoutesSortNewestFirst().map { if (it) SavedItemSortMode.NEWEST_FIRST else SavedItemSortMode.OLDEST_FIRST }

    suspend fun setRoutesSortMode(mode: SavedItemSortMode) = setRoutesSortNewestFirst(mode != SavedItemSortMode.OLDEST_FIRST)

    fun getFavoritesSortMode(): Flow<SavedItemSortMode> =
        getFavoritesSortNewestFirst().map { if (it) SavedItemSortMode.NEWEST_FIRST else SavedItemSortMode.OLDEST_FIRST }

    suspend fun setFavoritesSortMode(mode: SavedItemSortMode) = setFavoritesSortNewestFirst(mode != SavedItemSortMode.OLDEST_FIRST)

    /**
     * Manual favorites order (ids, top first) set by drag. Empty = follow the sort mode. Choosing a
     * sort mode clears it, so the last action wins.
     */
    fun getFavoritesManualOrder(): Flow<List<String>>

    suspend fun setFavoritesManualOrder(ids: List<String>)

    /** Same as [getFavoritesManualOrder] for routes. */
    fun getRoutesManualOrder(): Flow<List<String>>

    suspend fun setRoutesManualOrder(ids: List<String>)
}

internal class SavedItemOrderStore(
    private val dataStore: DataStore<Preferences>,
) : SavedItemOrderPreferences {
    override fun getRoutesSortNewestFirst(): Flow<Boolean> = dataStore.pref(Keys.ROUTES_SORT_NEWEST_FIRST, true)

    override suspend fun setRoutesSortNewestFirst(newestFirst: Boolean) = dataStore.setPref(Keys.ROUTES_SORT_NEWEST_FIRST, newestFirst)

    override fun getFavoritesSortNewestFirst(): Flow<Boolean> = dataStore.pref(Keys.FAVORITES_SORT_NEWEST_FIRST, true)

    override suspend fun setFavoritesSortNewestFirst(newestFirst: Boolean) =
        dataStore.setPref(Keys.FAVORITES_SORT_NEWEST_FIRST, newestFirst)

    override fun getRoutesSortMode(): Flow<SavedItemSortMode> =
        dataStore.data.map { prefs ->
            prefs[Keys.ROUTES_SORT_MODE]?.let(::savedItemSortModeOrNull)
                ?: if (prefs[Keys.ROUTES_SORT_NEWEST_FIRST] != false) {
                    SavedItemSortMode.NEWEST_FIRST
                } else {
                    SavedItemSortMode.OLDEST_FIRST
                }
        }

    override suspend fun setRoutesSortMode(mode: SavedItemSortMode) {
        dataStore.edit { prefs ->
            prefs[Keys.ROUTES_SORT_MODE] = mode.name
            prefs.remove(Keys.ROUTES_MANUAL_ORDER)
            prefs[Keys.ROUTES_SORT_NEWEST_FIRST] = mode != SavedItemSortMode.OLDEST_FIRST
        }
    }

    override fun getFavoritesSortMode(): Flow<SavedItemSortMode> =
        dataStore.data.map { prefs ->
            prefs[Keys.FAVORITES_SORT_MODE]?.let(::savedItemSortModeOrNull)
                ?: if (prefs[Keys.FAVORITES_SORT_NEWEST_FIRST] != false) {
                    SavedItemSortMode.NEWEST_FIRST
                } else {
                    SavedItemSortMode.OLDEST_FIRST
                }
        }

    override suspend fun setFavoritesSortMode(mode: SavedItemSortMode) {
        dataStore.edit { prefs ->
            prefs[Keys.FAVORITES_SORT_MODE] = mode.name
            prefs.remove(Keys.FAVORITES_MANUAL_ORDER)
            prefs[Keys.FAVORITES_SORT_NEWEST_FIRST] = mode != SavedItemSortMode.OLDEST_FIRST
        }
    }

    override fun getFavoritesManualOrder(): Flow<List<String>> = manualOrder(Keys.FAVORITES_MANUAL_ORDER)

    override suspend fun setFavoritesManualOrder(ids: List<String>) = setManualOrder(Keys.FAVORITES_MANUAL_ORDER, ids)

    override fun getRoutesManualOrder(): Flow<List<String>> = manualOrder(Keys.ROUTES_MANUAL_ORDER)

    override suspend fun setRoutesManualOrder(ids: List<String>) = setManualOrder(Keys.ROUTES_MANUAL_ORDER, ids)

    private fun manualOrder(key: Preferences.Key<String>): Flow<List<String>> =
        dataStore.data.map { prefs -> deserializeManualOrder(prefs[key]) }

    private suspend fun setManualOrder(
        key: Preferences.Key<String>,
        ids: List<String>,
    ) {
        dataStore.edit { prefs ->
            if (ids.isEmpty()) prefs.remove(key) else prefs[key] = serializeManualOrder(ids)
        }
    }
}

private fun savedItemSortModeOrNull(raw: String): SavedItemSortMode? = runCatching { SavedItemSortMode.valueOf(raw) }.getOrNull()
