package com.locationjoystick.core.common.util

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NsdCodeManagerTest {
    private val nsdManager = mockk<NsdManager>(relaxed = true)
    private val context =
        mockk<Context>(relaxed = true) {
            every { getSystemService(Context.NSD_SERVICE) } returns nsdManager
            every { applicationContext } returns this
            every { getSystemService(Context.WIFI_SERVICE) } returns
                mockk<WifiManager>(relaxed = true)
        }
    private val manager = NsdCodeManager(context)

    @Test
    fun `startAdvertising twice unregisters the first listener`() {
        val listeners = mutableListOf<NsdManager.RegistrationListener>()
        every { nsdManager.registerService(any(), any(), capture(listeners)) } returns Unit

        manager.startAdvertising("ABC123", 1000)
        manager.startAdvertising("ABC123", 2000)

        verify(exactly = 1) { nsdManager.unregisterService(listeners[0]) }
    }

    @Test
    fun `two onServiceFound resolve once`() =
        runTest {
            val listener = slot<NsdManager.DiscoveryListener>()
            every { nsdManager.discoverServices(any<String>(), any<Int>(), capture(listener)) } returns Unit
            val job = async { manager.discoverByCode("ABC123") }
            runCurrent()
            val info = mockk<NsdServiceInfo> { every { serviceName } returns "ABC123" }

            listener.captured.onServiceFound(info)
            listener.captured.onServiceFound(info)

            @Suppress("DEPRECATION")
            verify(exactly = 1) { nsdManager.resolveService(any<NsdServiceInfo>(), any<NsdManager.ResolveListener>()) }
            job.cancel()
        }
}
