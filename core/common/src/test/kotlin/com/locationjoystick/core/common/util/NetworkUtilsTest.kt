package com.locationjoystick.core.common.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class NetworkUtilsTest {
    private fun ip(s: String) = InetAddress.getByName(s)

    @Test
    fun `rmnet-only interfaces return null`() {
        val result = NetworkUtils.pickLocalIpv4(listOf("rmnet_data0" to ip("100.64.1.2"), "ccmni1" to ip("10.1.2.3")))
        assertNull(result)
    }

    @Test
    fun `wlan wins over rmnet`() {
        val result = NetworkUtils.pickLocalIpv4(listOf("rmnet_data0" to ip("10.1.2.3"), "wlan0" to ip("192.168.1.5")))
        assertEquals("192.168.1.5", result)
    }

    @Test
    fun `site-local on unknown interface is used`() {
        assertEquals("192.168.43.1", NetworkUtils.pickLocalIpv4(listOf("eth0" to ip("192.168.43.1"))))
    }

    @Test
    fun `lan peers are accepted`() {
        listOf("127.0.0.1", "10.0.0.2", "192.168.43.7", "172.20.10.2", "169.254.1.1", "::1", "fe80::1", "fd12:3456::1")
            .forEach { assertTrue(it, NetworkUtils.isLanPeer(ip(it))) }
    }

    @Test
    fun `public peers are rejected`() {
        listOf("8.8.8.8", "100.64.1.2", "2001:4860:4860::8888")
            .forEach { assertFalse(it, NetworkUtils.isLanPeer(ip(it))) }
    }
}
