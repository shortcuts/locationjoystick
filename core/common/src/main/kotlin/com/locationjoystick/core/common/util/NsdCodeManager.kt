package com.locationjoystick.core.common.util

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val TAG = "NsdCodeManager"

/** A leader found on the local network. [code] is the advertised service name. */
data class DiscoveredLeader(
    val code: String,
    val host: String,
    val port: Int,
)

/**
 * Advertises and discovers a host:port pair on the local network, keyed by a short code.
 * Used by both Group Sync (leader/follower) and QR export/import.
 */
@Singleton
class NsdCodeManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        private var registrationListener: NsdManager.RegistrationListener? = null

        // Some OEM Wi-Fi drivers filter incoming multicast unless a lock is held, silently
        // breaking mDNS advertise/discovery. Reference-counted: advertising and discovery
        // can overlap.
        private val multicastLock =
            (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createMulticastLock(TAG)
                .apply { setReferenceCounted(true) }

        private fun acquireMulticastLock() {
            try {
                multicastLock.acquire()
            } catch (e: Exception) {
                Log.w(TAG, "Multicast lock acquire failed", e)
            }
        }

        private fun releaseMulticastLock() {
            try {
                if (multicastLock.isHeld) multicastLock.release()
            } catch (e: Exception) {
                Log.w(TAG, "Multicast lock release failed", e)
            }
        }

        fun startAdvertising(
            code: String,
            port: Int,
        ) {
            val info =
                NsdServiceInfo().apply {
                    serviceName = code
                    serviceType = AppConstants.SyncConstants.NSD_SERVICE_TYPE
                    this.port = port
                }
            val listener =
                object : NsdManager.RegistrationListener {
                    override fun onServiceRegistered(info: NsdServiceInfo) {
                        Log.i(TAG, "NSD registered: ${info.serviceName}")
                    }

                    override fun onRegistrationFailed(
                        info: NsdServiceInfo,
                        errorCode: Int,
                    ) {
                        Log.e(TAG, "NSD registration failed: $errorCode")
                    }

                    override fun onServiceUnregistered(info: NsdServiceInfo) {
                        Log.i(TAG, "NSD unregistered: ${info.serviceName}")
                    }

                    override fun onUnregistrationFailed(
                        info: NsdServiceInfo,
                        errorCode: Int,
                    ) {
                        Log.e(TAG, "NSD unregistration failed: $errorCode")
                    }
                }
            registrationListener = listener
            acquireMulticastLock()
            try {
                nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (e: Exception) {
                Log.e(TAG, "NSD register error", e)
            }
        }

        fun stopAdvertising() {
            registrationListener?.let { listener ->
                try {
                    nsdManager.unregisterService(listener)
                } catch (e: Exception) {
                    Log.w(TAG, "NSD unregister error", e)
                }
                registrationListener = null
                releaseMulticastLock()
            }
        }

        /**
         * Discovers the NSD service with the given [code] and resolves it to a host:port pair.
         * Returns null if not found within [AppConstants.SyncConstants.NSD_DISCOVERY_TIMEOUT_MS].
         */
        suspend fun discoverByCode(code: String): Pair<String, Int>? =
            discover(AppConstants.SyncConstants.NSD_DISCOVERY_TIMEOUT_MS) { it == code }?.let { it.host to it.port }

        /**
         * Finds any leader on the network, for the idle-view hint. Leaders advertise a
         * [AppConstants.SyncConstants.GROUP_CODE_LENGTH]-char code; other services of the type are skipped.
         */
        suspend fun discoverAnyLeader(): DiscoveredLeader? =
            discover(AppConstants.SyncConstants.NSD_LEADER_PROBE_TIMEOUT_MS) {
                it.length == AppConstants.SyncConstants.GROUP_CODE_LENGTH
            }

        private suspend fun discover(
            timeoutMs: Long,
            matches: (String) -> Boolean,
        ): DiscoveredLeader? {
            acquireMulticastLock()
            try {
                return discoverLocked(timeoutMs, matches)
            } finally {
                releaseMulticastLock()
            }
        }

        private suspend fun discoverLocked(
            timeoutMs: Long,
            matches: (String) -> Boolean,
        ): DiscoveredLeader? =
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    var discoveryListener: NsdManager.DiscoveryListener? = null
                    var foundName = ""

                    @Suppress("DEPRECATION")
                    val resolveListener =
                        object : NsdManager.ResolveListener {
                            override fun onResolveFailed(
                                info: NsdServiceInfo,
                                errorCode: Int,
                            ) {
                                Log.e(TAG, "NSD resolve failed: $errorCode")
                                if (cont.isActive) cont.resume(null)
                            }

                            override fun onServiceResolved(info: NsdServiceInfo) {
                                val inetAddr = info.host
                                val host = (inetAddr as? Inet4Address)?.hostAddress
                                val port = info.port
                                Log.i(TAG, "NSD resolved: $host:$port (raw: ${inetAddr?.hostAddress})")
                                if (host != null && port > 0) {
                                    cont.resume(DiscoveredLeader(foundName, host, port))
                                } else {
                                    Log.w(TAG, "NSD resolved non-IPv4 or no host — cannot connect")
                                    cont.resume(null)
                                }
                            }
                        }

                    discoveryListener =
                        object : NsdManager.DiscoveryListener {
                            override fun onStartDiscoveryFailed(
                                serviceType: String,
                                errorCode: Int,
                            ) {
                                Log.e(TAG, "NSD discovery start failed: $errorCode")
                                if (cont.isActive) cont.resume(null)
                            }

                            override fun onStopDiscoveryFailed(
                                serviceType: String,
                                errorCode: Int,
                            ) {
                                Log.w(TAG, "NSD discovery stop failed: $errorCode")
                            }

                            override fun onDiscoveryStarted(serviceType: String) {
                                Log.d(TAG, "NSD discovery started")
                            }

                            override fun onDiscoveryStopped(serviceType: String) {}

                            override fun onServiceFound(info: NsdServiceInfo) {
                                if (matches(info.serviceName)) {
                                    foundName = info.serviceName
                                    try {
                                        nsdManager.stopServiceDiscovery(this)
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Stop discovery error", e)
                                    }
                                    @Suppress("DEPRECATION")
                                    nsdManager.resolveService(info, resolveListener)
                                }
                            }

                            override fun onServiceLost(info: NsdServiceInfo) {}
                        }

                    cont.invokeOnCancellation {
                        try {
                            discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
                        } catch (e: Exception) {
                            Log.w(TAG, "Cancellation stop discovery error", e)
                        }
                    }

                    try {
                        nsdManager.discoverServices(
                            AppConstants.SyncConstants.NSD_SERVICE_TYPE,
                            NsdManager.PROTOCOL_DNS_SD,
                            discoveryListener,
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "NSD discover error", e)
                        cont.resume(null)
                    }
                }
            }
    }
