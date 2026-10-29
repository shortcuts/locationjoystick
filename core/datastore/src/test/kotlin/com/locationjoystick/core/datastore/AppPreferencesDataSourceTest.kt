package com.locationjoystick.core.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.AppFeature
import com.locationjoystick.core.model.GroundCalibration
import com.locationjoystick.core.model.GuidedPath
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RealPositionSnapshot
import com.locationjoystick.core.model.SavedItemSortMode
import com.locationjoystick.core.model.StoredGroundCalibration
import com.locationjoystick.core.testing.FakePreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppPreferencesDataSourceTest {
    private lateinit var fakeDataStore: FakePreferencesDataStore
    private lateinit var dataSource: AppPreferencesDataSource

    @Before
    fun setUp() {
        fakeDataStore = FakePreferencesDataStore()
        dataSource = AppPreferencesDataSource(fakeDataStore)
    }

    @Test
    fun `hideTeleportFeatures defaults to false and round-trips true`() =
        runTest {
            assertFalse(dataSource.getHideTeleportFeatures().first())
            dataSource.setHideTeleportFeatures(true)
            assertTrue(dataSource.getHideTeleportFeatures().first())
        }

    private val storedCalibration =
        StoredGroundCalibration(
            GroundCalibration(-12.5, 7.25, 6.5, -0.0024, 540.0, 1490.5),
            screenWidth = 1080,
            screenHeight = 2400,
            calibratedAtMs = 1_700_000_000_000L,
            flowCount = 211,
            medianAbsPx = 0.8,
        )

    @Test
    fun `ground calibration round-trips and null removes it`() =
        runTest {
            assertNull(dataSource.getGroundCalibration().first())
            dataSource.setGroundCalibration(storedCalibration)
            assertEquals(storedCalibration, dataSource.getGroundCalibration().first())
            dataSource.setGroundCalibration(null)
            assertNull(dataSource.getGroundCalibration().first())
        }

    @Test
    fun `a version 1 record from the old motion fit reads null`() =
        runTest {
            val key = stringPreferencesKey("tap_to_walk_ground_calibration")
            fakeDataStore.edit { it[key] = serializeGroundCalibration(storedCalibration).replace("\"v\":2", "\"v\":1") }
            assertNull(dataSource.getGroundCalibration().first())
        }

    @Test
    fun `corrupt or unknown version ground calibration reads null`() =
        runTest {
            val key = stringPreferencesKey("tap_to_walk_ground_calibration")
            for (raw in listOf("not json", "{}", serializeGroundCalibration(storedCalibration).replace("\"v\":2", "\"v\":3"))) {
                fakeDataStore.edit { it[key] = raw }
                assertNull(raw, dataSource.getGroundCalibration().first())
            }
        }

    @Test
    fun `ground calibration is outside the settings snapshot and cleared by reset`() =
        runTest {
            dataSource.setGroundCalibration(storedCalibration)
            val snapshot = dataSource.getSettingsSnapshot().first()
            dataSource.applySnapshot(snapshot)
            assertEquals(storedCalibration, dataSource.getGroundCalibration().first())
            dataSource.clearAllExceptOnboarding()
            assertNull(dataSource.getGroundCalibration().first())
        }

    @Test
    fun `applySnapshot keeps tap to walk when the snapshot predates the immediate setter`() =
        runTest {
            val staleSnapshot = dataSource.getSettingsSnapshot().first()
            dataSource.setTapToWalkOverlayEnabled(true)
            dataSource.applySnapshot(staleSnapshot)
            assertTrue(dataSource.getTapToWalkOverlayEnabled().first())
        }

    @Test
    fun `plantingRadius defaults to 35 and clamps to 5-200`() =
        runTest {
            assertEquals(35.0, dataSource.getPlantingRadius().first(), 0.0)
            dataSource.setPlantingRadius(60.0)
            assertEquals(60.0, dataSource.getPlantingRadius().first(), 0.0)
            dataSource.setPlantingRadius(1.0)
            assertEquals(5.0, dataSource.getPlantingRadius().first(), 0.0)
            dataSource.setPlantingRadius(999.0)
            assertEquals(200.0, dataSource.getPlantingRadius().first(), 0.0)
        }

    @Test
    fun `sort modes default and round-trip`() =
        runTest {
            assertEquals(SavedItemSortMode.NEWEST_FIRST, dataSource.getRoutesSortMode().first())
            assertEquals(SavedItemSortMode.NEWEST_FIRST, dataSource.getFavoritesSortMode().first())

            dataSource.setRoutesSortMode(SavedItemSortMode.NAME_ASCENDING)
            dataSource.setFavoritesSortMode(SavedItemSortMode.NAME_DESCENDING)

            assertEquals(SavedItemSortMode.NAME_ASCENDING, dataSource.getRoutesSortMode().first())
            assertEquals(SavedItemSortMode.NAME_DESCENDING, dataSource.getFavoritesSortMode().first())
        }

    @Test
    fun `manual order round-trips and a sort mode clears it`() =
        runTest {
            assertEquals(emptyList<String>(), dataSource.getFavoritesManualOrder().first())

            dataSource.setFavoritesManualOrder(listOf("b", "a"))
            dataSource.setRoutesManualOrder(listOf("r2", "r1"))
            assertEquals(listOf("b", "a"), dataSource.getFavoritesManualOrder().first())

            dataSource.setFavoritesSortMode(SavedItemSortMode.NAME_ASCENDING)

            assertEquals(emptyList<String>(), dataSource.getFavoritesManualOrder().first())
            assertEquals(listOf("r2", "r1"), dataSource.getRoutesManualOrder().first())
        }

    @Test
    fun `legacy sort booleans migrate`() =
        runTest {
            fakeDataStore.updateData { prefs ->
                prefs.toMutablePreferences().apply {
                    this[booleanPreferencesKey("routes_sort_newest_first")] = false
                    this[booleanPreferencesKey("favorites_sort_newest_first")] = true
                }
            }

            assertEquals(SavedItemSortMode.OLDEST_FIRST, dataSource.getRoutesSortMode().first())
            assertEquals(SavedItemSortMode.NEWEST_FIRST, dataSource.getFavoritesSortMode().first())
        }

    @Test
    fun `hideWidgetOverlay defaults to false and round-trips true`() =
        runTest {
            assertFalse(dataSource.getHideWidgetOverlay().first())
            dataSource.setHideWidgetOverlay(true)
            assertTrue(dataSource.getHideWidgetOverlay().first())
        }

    @Test
    fun `keepWidgetOnIdle defaults to false and round-trips true`() =
        runTest {
            assertFalse(dataSource.getKeepWidgetOnIdle().first())
            dataSource.setKeepWidgetOnIdle(true)
            assertTrue(dataSource.getKeepWidgetOnIdle().first())
        }

    @Test
    fun `hideForegroundNotification defaults to false and round-trips true`() =
        runTest {
            assertFalse(dataSource.getHideForegroundNotification().first())
            dataSource.setHideForegroundNotification(true)
            assertTrue(dataSource.getHideForegroundNotification().first())
        }

    @Test
    fun `showRouteJumpButtons defaults to false and round-trips true`() =
        runTest {
            assertFalse(dataSource.getShowRouteJumpButtons().first())
            dataSource.setShowRouteJumpButtons(true)
            assertTrue(dataSource.getShowRouteJumpButtons().first())
        }

    @Test
    fun `lastKnownPositionEnabled defaults to false and round-trips true`() =
        runTest {
            assertFalse(dataSource.getLastKnownPositionEnabled().first())
            dataSource.setLastKnownPositionEnabled(true)
            assertTrue(dataSource.getLastKnownPositionEnabled().first())
        }

    @Test
    fun `real position snapshot is null by default, round-trips and is replaced`() =
        runTest {
            assertNull(dataSource.getRealPositionSnapshot().first())
            dataSource.setRealPositionSnapshot(RealPositionSnapshot(LatLng(1.5, 2.5), 10L))
            assertEquals(RealPositionSnapshot(LatLng(1.5, 2.5), 10L), dataSource.getRealPositionSnapshot().first())
            dataSource.setRealPositionSnapshot(RealPositionSnapshot(LatLng(3.5, 4.5), 20L))
            assertEquals(RealPositionSnapshot(LatLng(3.5, 4.5), 20L), dataSource.getRealPositionSnapshot().first())
        }

    @Test
    fun `guided path, accessibility disclosure and reconnect tap default and round-trip`() =
        runTest {
            assertEquals(GuidedPath.DEFAULT.name, dataSource.getGuidedPath().first())
            dataSource.setGuidedPath(GuidedPath.POKEMON_GO.name)
            assertEquals(GuidedPath.POKEMON_GO.name, dataSource.getGuidedPath().first())

            assertEquals(AppConstants.AccessibilityConstants.DISCLOSURE_UNANSWERED, dataSource.getAccessibilityDisclosureChoice().first())
            dataSource.setAccessibilityDisclosureChoice(AppConstants.AccessibilityConstants.DISCLOSURE_ACCEPTED)
            assertEquals(AppConstants.AccessibilityConstants.DISCLOSURE_ACCEPTED, dataSource.getAccessibilityDisclosureChoice().first())

            assertFalse(dataSource.getReconnectTapEnabled().first())
            dataSource.setReconnectTapEnabled(true)
            assertTrue(dataSource.getReconnectTapEnabled().first())
        }

    @Test
    fun `bypassMockLocationCheck defaults to false and round-trips true`() =
        runTest {
            assertFalse(dataSource.getBypassMockLocationCheck().first())
            dataSource.setBypassMockLocationCheck(true)
            assertTrue(dataSource.getBypassMockLocationCheck().first())
        }

    @Test
    fun `mergeNewDefaultMapFeatures does not add paste on upgrade from legacy defaults`() {
        val stored = LEGACY_MAP_FAB_SEEN_DEFAULTS
        val merged = mergeNewDefaultMapFeatures(stored, seenDefaults = null)
        assertFalse(merged.contains("paste_coordinates"))
        assertTrue(merged.containsAll(stored))
    }

    @Test
    fun `mergeNewDefaultMapFeatures does not re-enable after user disables paste`() {
        val stored = setOf("favorites", "routes", "roaming", "search")
        val seen = AppFeature.DEFAULT_MAP_ENABLED.map { it.name.lowercase() }.toSet()
        val merged = mergeNewDefaultMapFeatures(stored, seen)
        assertFalse(merged.contains("paste_coordinates"))
        assertEquals(stored, merged)
    }

    @Test
    fun `mergeNewDefaultMapFeatures adds capture on upgrade from paste-era defaults`() {
        val stored = setOf("favorites", "routes", "roaming", "search", "paste_coordinates")
        val merged = mergeNewDefaultMapFeatures(stored, seenDefaults = stored)
        assertTrue(merged.contains("capture_coordinates"))
        assertTrue(merged.containsAll(stored))
    }

    @Test
    fun `mergeNewDefaultMapFeatures does not re-enable after user disables capture`() {
        val stored = setOf("favorites", "routes", "roaming", "search", "paste_coordinates")
        val seen = AppFeature.DEFAULT_MAP_ENABLED.map { it.name.lowercase() }.toSet()
        val merged = mergeNewDefaultMapFeatures(stored, seen)
        assertFalse(merged.contains("capture_coordinates"))
        assertEquals(stored, merged)
    }

    @Test
    fun `map FAB items default excludes paste coordinates`() =
        runTest {
            val items = dataSource.getMapItems().first()
            assertFalse(items.contains("paste_coordinates"))
            assertTrue(items.contains("capture_coordinates"))
            assertTrue(items.containsAll(LEGACY_MAP_FAB_SEEN_DEFAULTS))
        }

    @Test
    fun `legacy map FAB store does not gain paste coordinates on upgrade`() =
        runTest {
            fakeDataStore.updateData { prefs ->
                val mutable = prefs.toMutablePreferences()
                mutable[stringSetPreferencesKey("map_fab_items")] = LEGACY_MAP_FAB_SEEN_DEFAULTS
                mutable
            }
            val items = dataSource.getMapItems().first()
            assertFalse(items.contains("paste_coordinates"))
            assertTrue(items.contains("capture_coordinates"))
            assertTrue(items.contains("search"))
        }

    @Test
    fun `disabling paste coordinates on map FABs sticks`() =
        runTest {
            dataSource.setMapItems(LEGACY_MAP_FAB_SEEN_DEFAULTS)
            val items = dataSource.getMapItems().first()
            assertFalse(items.contains("paste_coordinates"))
            assertEquals(LEGACY_MAP_FAB_SEEN_DEFAULTS, items)
        }

    @Test
    fun `legacy widget store does not gain paste coordinates on upgrade`() =
        runTest {
            fakeDataStore.updateData { prefs ->
                val mutable = prefs.toMutablePreferences()
                mutable[stringSetPreferencesKey("widget_items")] = LEGACY_WIDGET_SEEN_DEFAULTS
                mutable
            }
            val items = dataSource.getWidgetItems().first()
            assertFalse(items.contains("paste_coordinates"))
            assertTrue(items.contains("favorites"))
            assertTrue(items.contains("roaming"))
        }

    @Test
    fun `legacy widget store gains roaming on upgrade`() =
        runTest {
            fakeDataStore.updateData { prefs ->
                val mutable = prefs.toMutablePreferences()
                mutable[stringSetPreferencesKey("widget_items")] = LEGACY_WIDGET_SEEN_DEFAULTS
                mutable
            }
            val items = dataSource.getWidgetItems().first()
            assertTrue(items.contains("roaming"))
            assertFalse(items.contains("paste_coordinates"))
        }

    @Test
    fun `paste-era widget seen defaults gain roaming and keep paste off`() {
        val stored = LEGACY_WIDGET_SEEN_DEFAULTS
        val merged = mergeNewDefaultWidgetFeatures(stored, seenDefaults = WIDGET_PRE_ROAMING_SEEN_DEFAULTS)
        assertTrue(merged.contains("roaming"))
        assertFalse(merged.contains("paste_coordinates"))
        assertTrue(merged.containsAll(stored))
    }

    @Test
    fun `paste-era widget store with saved seen defaults gains roaming`() =
        runTest {
            fakeDataStore.updateData { prefs ->
                val mutable = prefs.toMutablePreferences()
                mutable[stringSetPreferencesKey("widget_items")] =
                    WIDGET_PRE_ROAMING_SEEN_DEFAULTS
                mutable[stringSetPreferencesKey("widget_seen_defaults")] =
                    WIDGET_PRE_ROAMING_SEEN_DEFAULTS
                mutable
            }
            val items = dataSource.getWidgetItems().first()
            assertTrue(items.contains("roaming"))
            assertTrue(items.contains("paste_coordinates"))
        }

    @Test
    fun `disabling roaming on widget after it was a default sticks`() =
        runTest {
            val withoutRoaming =
                AppFeature.DEFAULT_WIDGET_ENABLED
                    .map { it.name.lowercase() }
                    .toSet() - "roaming"
            dataSource.setWidgetItems(withoutRoaming)
            val items = dataSource.getWidgetItems().first()
            assertFalse(items.contains("roaming"))
            assertEquals(withoutRoaming, items)
        }

    @Test
    fun `widget items default includes roaming`() =
        runTest {
            val items = dataSource.getWidgetItems().first()
            assertTrue(items.contains("roaming"))
        }

    @Test
    fun `disabling paste coordinates on widget sticks`() =
        runTest {
            dataSource.setWidgetItems(LEGACY_WIDGET_SEEN_DEFAULTS)
            val items = dataSource.getWidgetItems().first()
            assertFalse(items.contains("paste_coordinates"))
            assertEquals(LEGACY_WIDGET_SEEN_DEFAULTS, items)
        }

    @Test
    fun `fresh store has no paste coordinates on widget or map`() =
        runTest {
            assertFalse(dataSource.getWidgetItems().first().contains("paste_coordinates"))
            assertFalse(dataSource.getMapItems().first().contains("paste_coordinates"))
        }

    @Test
    fun `persisted paste coordinates stays enabled on widget`() =
        runTest {
            dataSource.setWidgetItems(AppFeature.DEFAULT_WIDGET_ENABLED.map { it.name.lowercase() }.toSet() + "paste_coordinates")
            assertTrue(dataSource.getWidgetItems().first().contains("paste_coordinates"))
        }

    @Test
    fun `persisted paste coordinates stays enabled on map`() =
        runTest {
            dataSource.setMapItems(AppFeature.DEFAULT_MAP_ENABLED.map { it.name.lowercase() }.toSet() + "paste_coordinates")
            assertTrue(dataSource.getMapItems().first().contains("paste_coordinates"))
        }
}
