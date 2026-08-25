package com.locationjoystick.core.datastore

import com.locationjoystick.core.testing.FakePreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
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

    @Test
    fun `hideWidgetOverlay defaults to false and round-trips true`() =
        runTest {
            assertFalse(dataSource.getHideWidgetOverlay().first())
            dataSource.setHideWidgetOverlay(true)
            assertTrue(dataSource.getHideWidgetOverlay().first())
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
    fun `bypassMockLocationCheck defaults to false and round-trips true`() =
        runTest {
            assertFalse(dataSource.getBypassMockLocationCheck().first())
            dataSource.setBypassMockLocationCheck(true)
            assertTrue(dataSource.getBypassMockLocationCheck().first())
        }
}
