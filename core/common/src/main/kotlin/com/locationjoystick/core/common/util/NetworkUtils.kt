package com.locationjoystick.core.common.util

import android.util.Log
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface

private const val TAG = "NetworkUtils"

object NetworkUtils {
    private val CELLULAR_PREFIXES = listOf("rmnet", "ccmni")

    /** Returns this device's local Wi-Fi/LAN IPv4 address, or null if none is found. */
    fun getLocalIpAddress(): String? =
        try {
            pickLocalIpv4(
                NetworkInterface
                    .getNetworkInterfaces()
                    ?.toList()
                    ?.flatMap { iface -> iface.inetAddresses.toList().map { iface.name to it } }
                    .orEmpty(),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get local IP", e)
            null
        }

    /** Picks the LAN address from (interface name, address) pairs. Never returns a cellular address. */
    internal fun pickLocalIpv4(interfaces: List<Pair<String, InetAddress>>): String? {
        val candidates = interfaces.filter { (_, addr) -> !addr.isLoopbackAddress && addr is Inet4Address }
        // Cellular (rmnet) can enumerate before Wi-Fi and hand out an unreachable CGNAT
        // address — prefer Wi-Fi client (wlan) or hotspot-host (ap/swlan) interfaces.
        val preferred =
            candidates.firstOrNull { (name, _) ->
                name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("swlan")
            } ?: candidates.firstOrNull { (name, addr) ->
                addr.isSiteLocalAddress && CELLULAR_PREFIXES.none { name.startsWith(it) }
            }
        return preferred?.second?.hostAddress
    }

    /** True for loopback, IPv4 private/link-local and IPv6 link-local/ULA peers. Public peers are false. */
    fun isLanPeer(addr: InetAddress): Boolean =
        addr.isLoopbackAddress ||
            addr.isSiteLocalAddress ||
            addr.isLinkLocalAddress ||
            (addr is Inet6Address && (addr.address[0].toInt() and 0xFE) == 0xFC)
}
