package com.locationjoystick.core.common.util

import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface

private const val TAG = "NetworkUtils"

object NetworkUtils {
    /** Returns this device's local Wi-Fi/LAN IPv4 address, or null if none is found. */
    fun getLocalIpAddress(): String? =
        try {
            NetworkInterface
                .getNetworkInterfaces()
                ?.toList()
                ?.flatMap { it.inetAddresses.toList() }
                ?.firstOrNull { addr -> !addr.isLoopbackAddress && addr is Inet4Address }
                ?.hostAddress
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get local IP", e)
            null
        }
}
