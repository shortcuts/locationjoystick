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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    fun `discoverAllLeaders skips an export advert`() =
        runTest {
            assertEquals(emptyList<DiscoveredLeader>(), probeAll(listOf("ABC123" to mapOf("role" to "export".toByteArray()))))
        }

    @Test
    fun `discoverAllLeaders returns a leader without a role`() =
        runTest {
            assertEquals(listOf("ABC123"), probeAll(listOf("ABC123" to emptyMap())).map { it.code })
        }

    @Test
    fun `discoverAllLeaders resolves services one at a time and returns both`() =
        runTest {
            val discovery = slot<NsdManager.DiscoveryListener>()
            val resolvers = mutableListOf<NsdManager.ResolveListener>()
            every { nsdManager.discoverServices(any<String>(), any<Int>(), capture(discovery)) } returns Unit

            @Suppress("DEPRECATION")
            every { nsdManager.resolveService(any<NsdServiceInfo>(), capture(resolvers)) } returns Unit
            val job = async { manager.discoverAllLeaders() }
            runCurrent()
            discovery.captured.onServiceFound(found("ABC123"))
            discovery.captured.onServiceFound(found("DEF456"))
            assertEquals(1, resolvers.size)

            resolvers[0].onServiceResolved(resolved("192.168.1.5", 1234))
            assertEquals(2, resolvers.size)
            resolvers[1].onServiceResolved(resolved("192.168.1.6", 1235))
            advanceUntilIdle()

            assertEquals(
                listOf(DiscoveredLeader("ABC123", "192.168.1.5", 1234), DiscoveredLeader("DEF456", "192.168.1.6", 1235)),
                job.await(),
            )
        }

    @Test
    fun `discoverAllLeaders skips a failed resolve and keeps the other`() =
        runTest {
            val discovery = slot<NsdManager.DiscoveryListener>()
            val resolvers = mutableListOf<NsdManager.ResolveListener>()
            every { nsdManager.discoverServices(any<String>(), any<Int>(), capture(discovery)) } returns Unit

            @Suppress("DEPRECATION")
            every { nsdManager.resolveService(any<NsdServiceInfo>(), capture(resolvers)) } returns Unit
            val job = async { manager.discoverAllLeaders() }
            runCurrent()
            discovery.captured.onServiceFound(found("ABC123"))
            discovery.captured.onServiceFound(found("DEF456"))

            resolvers[0].onResolveFailed(found("ABC123"), 0)
            resolvers[1].onServiceResolved(resolved("192.168.1.6", 1235))
            advanceUntilIdle()

            assertEquals(listOf(DiscoveredLeader("DEF456", "192.168.1.6", 1235)), job.await())
        }

    @Test
    fun `discoverByCode returns an export advert`() =
        runTest {
            assertEquals("ABC123", probe(attributes = mapOf("role" to "export".toByteArray()))?.code)
        }

    private fun found(name: String) = mockk<NsdServiceInfo> { every { serviceName } returns name }

    private fun resolved(
        ip: String,
        servicePort: Int,
        attrs: Map<String, ByteArray> = emptyMap(),
    ) = mockk<NsdServiceInfo> {
        every { host } returns mockk<Inet4Address> { every { hostAddress } returns ip }
        every { port } returns servicePort
        every { this@mockk.attributes } returns attrs
    }

    private suspend fun kotlinx.coroutines.test.TestScope.probeAll(
        services: List<Pair<String, Map<String, ByteArray>>>,
    ): List<DiscoveredLeader> {
        val discovery = slot<NsdManager.DiscoveryListener>()
        val resolvers = mutableListOf<NsdManager.ResolveListener>()
        every { nsdManager.discoverServices(any<String>(), any<Int>(), capture(discovery)) } returns Unit

        @Suppress("DEPRECATION")
        every { nsdManager.resolveService(any<NsdServiceInfo>(), capture(resolvers)) } returns Unit
        val job = async { manager.discoverAllLeaders() }
        runCurrent()
        services.forEachIndexed { i, (name, attrs) ->
            discovery.captured.onServiceFound(found(name))
            resolvers[i].onServiceResolved(resolved("192.168.1.5", 1234, attrs))
        }
        advanceUntilIdle()
        return job.await()
    }

    private suspend fun kotlinx.coroutines.test.TestScope.probe(attributes: Map<String, ByteArray>): DiscoveredLeader? {
        val discovery = slot<NsdManager.DiscoveryListener>()
        val resolver = slot<NsdManager.ResolveListener>()
        every { nsdManager.discoverServices(any<String>(), any<Int>(), capture(discovery)) } returns Unit

        @Suppress("DEPRECATION")
        every { nsdManager.resolveService(any<NsdServiceInfo>(), capture(resolver)) } returns Unit
        val job = async { manager.discoverByCode("ABC123")?.let { DiscoveredLeader("ABC123", it.first, it.second) } }
        runCurrent()
        discovery.captured.onServiceFound(found("ABC123"))
        resolver.captured.onServiceResolved(resolved("192.168.1.5", 1234, attributes))
        runCurrent()
        return job.await()
    }
}
