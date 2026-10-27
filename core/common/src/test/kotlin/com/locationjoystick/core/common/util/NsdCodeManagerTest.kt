package com.locationjoystick.core.common.util

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.unmockkConstructor
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.Inet4Address

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

    @Test
    fun `stopping the export advert keeps the leader advert registered`() {
        val listeners = mutableListOf<NsdManager.RegistrationListener>()
        every { nsdManager.registerService(any(), any(), capture(listeners)) } returns Unit

        manager.startAdvertising("LEADER", 1000)
        manager.startAdvertising("EXPORT", 2000, export = true)
        manager.stopAdvertising("EXPORT")

        verify(exactly = 0) { nsdManager.unregisterService(listeners[0]) }
        verify(exactly = 1) { nsdManager.unregisterService(listeners[1]) }
    }

    @Test
    fun `stopAdvertising with an unknown code unregisters nothing`() {
        manager.startAdvertising("LEADER", 1000)
        manager.stopAdvertising("OTHER1")

        verify(exactly = 0) { nsdManager.unregisterService(any()) }
    }

    @Test
    fun `only the export advert carries the role attribute`() {
        mockkConstructor(NsdServiceInfo::class)
        try {
            every { anyConstructed<NsdServiceInfo>().setAttribute(any(), any()) } returns Unit
            manager.startAdvertising("LEADER", 1000)
            verify(exactly = 0) { anyConstructed<NsdServiceInfo>().setAttribute(any(), any()) }

            manager.startAdvertising("EXPORT", 2000, export = true)
            verify(exactly = 1) { anyConstructed<NsdServiceInfo>().setAttribute("role", "export") }
        } finally {
            unmockkConstructor(NsdServiceInfo::class)
        }
    }

    @Test
    fun `discoverAnyLeader skips an export advert`() =
        runTest {
            assertNull(probe(attributes = mapOf("role" to "export".toByteArray()), probeAny = true))
        }

    @Test
    fun `discoverAnyLeader returns a leader without a role`() =
        runTest {
            assertEquals("ABC123", probe(attributes = emptyMap(), probeAny = true)?.code)
        }

    @Test
    fun `discoverByCode returns an export advert`() =
        runTest {
            assertEquals("ABC123", probe(attributes = mapOf("role" to "export".toByteArray()), probeAny = false)?.code)
        }

    private suspend fun kotlinx.coroutines.test.TestScope.probe(
        attributes: Map<String, ByteArray>,
        probeAny: Boolean,
    ): DiscoveredLeader? {
        val discovery = slot<NsdManager.DiscoveryListener>()
        val resolver = slot<NsdManager.ResolveListener>()
        every { nsdManager.discoverServices(any<String>(), any<Int>(), capture(discovery)) } returns Unit

        @Suppress("DEPRECATION")
        every { nsdManager.resolveService(any<NsdServiceInfo>(), capture(resolver)) } returns Unit
        val job =
            async {
                if (probeAny) {
                    manager.discoverAnyLeader()
                } else {
                    manager
                        .discoverByCode(
                            "ABC123",
                        )?.let { DiscoveredLeader("ABC123", it.first, it.second) }
                }
            }
        runCurrent()
        val found = mockk<NsdServiceInfo> { every { serviceName } returns "ABC123" }
        discovery.captured.onServiceFound(found)
        val address = mockk<Inet4Address> { every { hostAddress } returns "192.168.1.5" }
        resolver.captured.onServiceResolved(
            mockk<NsdServiceInfo> {
                every { host } returns address
                every { port } returns 1234
                every { this@mockk.attributes } returns attributes
            },
        )
        runCurrent()
        return job.await()
    }
}
