package com.locationjoystick.feature.widget.impl

import com.locationjoystick.feature.joystick.impl.JoystickOverlayService
import io.mockk.mockk
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JoystickBindingTest {
    private val service = mockk<JoystickOverlayService>()
    private val job = Job()

    @Test
    fun `unbound accepts a bind request`() {
        assertEquals(JoystickBinding.Binding, JoystickBinding.Unbound.bindRequested())
    }

    @Test
    fun `bind request is rejected while binding or bound`() {
        assertNull(JoystickBinding.Binding.bindRequested())
        assertNull(JoystickBinding.Bound(service, job).bindRequested())
    }

    @Test
    fun `binding becomes bound on connection`() {
        assertEquals(JoystickBinding.Bound(service, job), JoystickBinding.Binding.connected(service) { job })
    }

    @Test
    fun `bound reconnects with fresh collectors`() {
        val fresh = Job()
        assertEquals(JoystickBinding.Bound(service, fresh), JoystickBinding.Bound(service, job).connected(service) { fresh })
    }

    @Test
    fun `connection after unbind is rejected`() {
        assertNull(JoystickBinding.Unbound.connected(service) { error("no collectors expected") })
    }
}
