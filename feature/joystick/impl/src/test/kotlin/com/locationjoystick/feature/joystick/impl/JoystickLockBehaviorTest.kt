package com.locationjoystick.feature.joystick.impl

import android.os.SystemClock
import android.view.MotionEvent
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.location.MapController
import com.locationjoystick.core.location.MockLocationService
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.isPaused
import io.mockk.clearMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class JoystickLockBehaviorTest {
    private lateinit var service: JoystickOverlayService
    private lateinit var view: JoystickView
    private lateinit var locationRepository: LocationRepository
    private lateinit var mapController: MapController
    private lateinit var mockLocationService: MockLocationService

    @Before
    fun setUp() {
        locationRepository = LocationRepository()
        mapController = mockk(relaxed = true)
        mockLocationService = mockk(relaxed = true)
        // Attach a context without onCreate: these tests exercise touch/service callbacks, not
        // Hilt injection, GPS injection or a real WindowManager overlay.
        service = Robolectric.buildService(JoystickOverlayService::class.java).get()
        service.locationRepository = locationRepository
        service.mapController = mapController
        ReflectionHelpers.setField(service, "mockLocationService", mockLocationService)
        view = service.createOverlayView() as JoystickView
        ReflectionHelpers.setField(service, "overlayView", view)
        view.layout(0, 0, 300, 300)
    }

    @After
    fun tearDown() {
        service.onDestroy()
    }

    @Test
    fun `centering a locked stick stops steps and pulling outward keeps lock enabled`() {
        service.setIsLocked(true)
        touch(MotionEvent.ACTION_DOWN, 270f, 150f)
        val movementJob = service.inputState.job!!
        assertTrue(movementJob.isActive)

        // Within the dead zone, but not exactly at the center.
        touch(MotionEvent.ACTION_MOVE, 155f, 150f)

        assertTrue(service.isLocked.value)
        assertTrue(view.isLocked)
        assertTrue(movementJob.isActive)
        assertEquals(0f, service.inputState.input!!.force)

        touch(MotionEvent.ACTION_MOVE, 150f, 150f)
        touch(MotionEvent.ACTION_UP, 150f, 150f)
        assertTrue(service.isLocked.value)
        assertEquals(0f, service.inputState.input!!.force)

        touch(MotionEvent.ACTION_DOWN, 270f, 150f)
        touch(MotionEvent.ACTION_UP, 270f, 150f)
        assertTrue(service.isLocked.value)
        assertTrue(service.inputState.input!!.force > 0f)
        verify(exactly = 0) { mockLocationService.clearMotionVector() }
    }

    @Test
    fun `tapping the center of an idle locked stick keeps it locked`() {
        service.setIsLocked(true)

        touch(MotionEvent.ACTION_DOWN, 150f, 150f)
        touch(MotionEvent.ACTION_UP, 150f, 150f)

        assertTrue(service.isLocked.value)
        assertTrue(view.isLocked)
        assertNull(service.inputState.input)
    }

    @Test
    fun `releasing a locked stick outside the dead zone retains its direction`() {
        service.setIsLocked(true)
        touch(MotionEvent.ACTION_DOWN, 270f, 150f)
        val input = service.inputState.input!!

        touch(MotionEvent.ACTION_UP, 270f, 150f)

        assertTrue(service.isLocked.value)
        assertTrue(view.isLocked)
        assertTrue(input.force > 0f)
        assertEquals(input, service.inputState.input!!)
        assertTrue(service.inputState.job!!.isActive)
        verify(exactly = 0) { mockLocationService.clearMotionVector() }
    }

    @Test
    fun `dragging the overlay handle across the center does not unlock`() {
        service.setIsLocked(true)

        touch(MotionEvent.ACTION_DOWN, 10f, 10f)
        touch(MotionEvent.ACTION_MOVE, 150f, 150f)
        touch(MotionEvent.ACTION_UP, 150f, 150f)

        assertTrue(service.isLocked.value)
        assertTrue(view.isLocked)
        verify(exactly = 0) { mockLocationService.clearMotionVector() }
    }

    @Test
    fun `explicit unlock clears locked movement`() {
        service.setIsLocked(true)
        touch(MotionEvent.ACTION_DOWN, 270f, 150f)

        service.setIsLocked(false)
        touch(MotionEvent.ACTION_UP, 270f, 150f)

        assertFalse(service.isLocked.value)
        assertNull(service.inputState.input)
    }

    @Test
    fun `release after explicit unlock preserves a newly started walk`() {
        releaseAfterUnlockAndRestart(MockMode.WALK_TO)
    }

    @Test
    fun `release after explicit unlock preserves a newly started replay`() {
        releaseAfterUnlockAndRestart(MockMode.ROUTE_REPLAY)
    }

    @Test
    fun `center touch while an engine owns movement preserves lock mode and vector`() {
        service.setIsLocked(true)
        locationRepository.setWalkTarget(LatLng(1.0, 2.0))

        touch(MotionEvent.ACTION_DOWN, 150f, 150f)

        assertTrue(service.isLocked.value)
        assertEquals(MockMode.WALK_TO, locationRepository.currentMode.value)
        assertEquals(LatLng(1.0, 2.0), locationRepository.walkTarget.value)
        verify(exactly = 0) { mockLocationService.clearMotionVector() }
    }

    @Test
    fun `new stick input requests walk pause and release keeps the target paused`() {
        val target = LatLng(1.0, 2.0)
        locationRepository.setWalkTarget(target)
        every { mapController.pauseAutomatedMovement() } answers { locationRepository.pauseMovement() }

        touch(MotionEvent.ACTION_DOWN, 270f, 150f)
        touch(MotionEvent.ACTION_UP, 270f, 150f)

        assertEquals(target, locationRepository.walkTarget.value)
        assertEquals(MockMode.WALK_TO, locationRepository.currentMode.value)
        assertTrue(locationRepository.currentMovement().isPaused(MockMode.WALK_TO))
        verify(exactly = 1) { mapController.pauseAutomatedMovement() }
        confirmVerified(mapController)
        verify(exactly = 1) { mockLocationService.clearMotionVector() }
    }

    @Test
    fun `new stick input requests replay pause and release preserves the route`() {
        val waypoints = listOf(LatLng(1.0, 2.0), LatLng(1.1, 2.1))
        locationRepository.setMockMode(MockMode.ROUTE_REPLAY)
        locationRepository.setActiveRouteId("saved-route")
        locationRepository.setRouteWaypoints(waypoints)
        locationRepository.startSpoofing()
        every { mapController.pauseAutomatedMovement() } answers { locationRepository.pauseSpoofing() }

        touch(MotionEvent.ACTION_DOWN, 270f, 150f)
        touch(MotionEvent.ACTION_UP, 270f, 150f)

        assertEquals("saved-route", locationRepository.activeRouteId.value)
        assertEquals(waypoints, locationRepository.routeWaypoints.value)
        assertEquals(MockMode.ROUTE_REPLAY, locationRepository.currentMode.value)
        assertEquals(MockLocationState.PAUSED, locationRepository.mockLocationState.value)
        verify(exactly = 1) { mapController.pauseAutomatedMovement() }
        confirmVerified(mapController)
        verify(exactly = 1) { mockLocationService.clearMotionVector() }
    }

    @Test
    fun `new stick input requests roam pause and release keeps it paused`() {
        locationRepository.setMockMode(MockMode.ROAMING)
        every { mapController.pauseAutomatedMovement() } answers { locationRepository.pauseMovement() }

        touch(MotionEvent.ACTION_DOWN, 270f, 150f)
        touch(MotionEvent.ACTION_UP, 270f, 150f)

        assertEquals(MockMode.ROAMING, locationRepository.currentMode.value)
        assertTrue(locationRepository.currentMovement().isPaused(MockMode.ROAMING))
        verify(exactly = 1) { mapController.pauseAutomatedMovement() }
        confirmVerified(mapController)
        verify(exactly = 1) { mockLocationService.clearMotionVector() }
    }

    private fun releaseAfterUnlockAndRestart(mode: MockMode) {
        service.setIsLocked(true)
        touch(MotionEvent.ACTION_DOWN, 270f, 150f)
        touch(MotionEvent.ACTION_MOVE, 150f, 150f)
        service.setIsLocked(false)
        assertFalse(service.isLocked.value)
        clearMocks(mockLocationService)
        locationRepository.setMockMode(mode)

        // A map action starts another engine before the old finger-up is delivered.
        touch(MotionEvent.ACTION_UP, 150f, 150f)

        assertEquals(mode, locationRepository.currentMode.value)
        verify(exactly = 0) { mockLocationService.clearMotionVector() }
    }

    private fun touch(
        action: Int,
        x: Float,
        y: Float,
    ) {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, x, y, 0)
        try {
            assertTrue(view.dispatchTouchEvent(event))
        } finally {
            event.recycle()
        }
    }
}
