package com.locationjoystick.core.common.util

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.util.concurrent.atomic.AtomicBoolean
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
        private val registrationListeners = mutableMapOf<String, NsdManager.RegistrationListener>()

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
            export: Boolean = false,
        ) {
            stopAdvertising(code)
            val info =
                NsdServiceInfo().apply {
                    serviceName = code
                    serviceType = AppConstants.SyncConstants.NSD_SERVICE_TYPE
                    this.port = port
                    if (export) setAttribute(AppConstants.SyncConstants.NSD_ATTR_ROLE, AppConstants.SyncConstants.NSD_ROLE_EXPORT)
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
            synchronized(registrationListeners) { registrationListeners[code] = listener }
            acquireMulticastLock()
            try {
                nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (e: Exception) {
                Log.e(TAG, "NSD register error", e)
            }
        }

        fun stopAdvertising(code: String) {
            synchronized(registrationListeners) { registrationListeners.remove(code) }?.let { listener ->
                try {
                    nsdManager.unregisterService(listener)
                } catch (e: Exception) {
                    Log.w(TAG, "NSD unregister error", e)
                }
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
         * Finds every leader on the network within one probe window, for the idle-view hint. Leaders
         * advertise a [AppConstants.SyncConstants.GROUP_CODE_LENGTH]-char code; other services of the type are skipped.
         */
        suspend fun discoverAllLeaders(): List<DiscoveredLeader> {
            acquireMulticastLock()
            try {
                return probeLeaders()
            } finally {
                releaseMulticastLock()
            }
        }

        private suspend fun probeLeaders(): List<DiscoveredLeader> {
            val lock = Any()
            val pending = ArrayDeque<NsdServiceInfo>()
            val seen = mutableSetOf<String>()
            val leaders = mutableListOf<DiscoveredLeader>()
            var resolving = false
            var resolvingName = ""
            val startFailed = CompletableDeferred<Unit>()

            // Before API 34 only one resolveService may be active; a second fails with FAILURE_ALREADY_ACTIVE.
            lateinit var resolveListener: NsdManager.ResolveListener

            fun resolveNext() {
                val next =
                    synchronized(lock) {
                        if (resolving) return
                        pending.removeFirstOrNull()?.also {
                            resolving = true
                            resolvingName = it.serviceName
                        }
                    } ?: return
                try {
                    @Suppress("DEPRECATION")
                    nsdManager.resolveService(next, resolveListener)
                } catch (e: Exception) {
                    Log.w(TAG, "NSD resolve error", e)
                    synchronized(lock) { resolving = false }
                    resolveNext()
                }
            }

            @Suppress("DEPRECATION")
            resolveListener =
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(
                        info: NsdServiceInfo,
                        errorCode: Int,
                    ) {
                        Log.e(TAG, "NSD resolve failed: $errorCode")
                        synchronized(lock) { resolving = false }
                        resolveNext()
                    }

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val host = (info.host as? Inet4Address)?.hostAddress
                        if (info.attributes?.containsKey(AppConstants.SyncConstants.NSD_ATTR_ROLE) == true) {
                            Log.d(TAG, "NSD skipped export advert")
                        } else if (host != null && info.port > 0) {
                            synchronized(lock) { leaders += DiscoveredLeader(resolvingName, host, info.port) }
                        } else {
                            Log.w(TAG, "NSD resolved non-IPv4 or no host — cannot connect")
                        }
                        synchronized(lock) { resolving = false }
                        resolveNext()
                    }
                }

            val discoveryListener =
                object : NsdManager.DiscoveryListener {
                    override fun onStartDiscoveryFailed(
                        serviceType: String,
                        errorCode: Int,
                    ) {
                        Log.e(TAG, "NSD discovery start failed: $errorCode")
                        startFailed.complete(Unit)
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
                        if (info.serviceName.length != AppConstants.SyncConstants.GROUP_CODE_LENGTH) return
                        if (synchronized(lock) { seen.add(info.serviceName) && pending.add(info) }) resolveNext()
                    }

                    override fun onServiceLost(info: NsdServiceInfo) {}
                }

            try {
                nsdManager.discoverServices(
                    AppConstants.SyncConstants.NSD_SERVICE_TYPE,
                    NsdManager.PROTOCOL_DNS_SD,
                    discoveryListener,
                )
            } catch (e: Exception) {
                Log.e(TAG, "NSD discover error", e)
                return emptyList()
            }
            try {
                withTimeoutOrNull(AppConstants.SyncConstants.NSD_LEADER_PROBE_TIMEOUT_MS) { startFailed.await() }
            } finally {
                try {
                    nsdManager.stopServiceDiscovery(discoveryListener)
                } catch (e: Exception) {
                    Log.w(TAG, "Stop discovery error", e)
                }
            }
            return synchronized(lock) { leaders.toList() }
        }

        private suspend fun discover(
            timeoutMs: Long,
            accept: (NsdServiceInfo) -> Boolean = { true },
            matches: (String) -> Boolean,
        ): DiscoveredLeader? {
            acquireMulticastLock()
            try {
                return discoverLocked(timeoutMs, accept, matches)
            } finally {
                releaseMulticastLock()
            }
        }

        private suspend fun discoverLocked(
            timeoutMs: Long,
            accept: (NsdServiceInfo) -> Boolean,
            matches: (String) -> Boolean,
        ): DiscoveredLeader? =
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    var discoveryListener: NsdManager.DiscoveryListener? = null
                    var foundName = ""
                    val resolving = AtomicBoolean(false)

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
                                if (!accept(info)) {
                                    if (cont.isActive) cont.resume(null)
                                } else if (host != null && port > 0) {
                                    if (cont.isActive) cont.resume(DiscoveredLeader(foundName, host, port))
                                } else {
                                    Log.w(TAG, "NSD resolved non-IPv4 or no host — cannot connect")
                                    if (cont.isActive) cont.resume(null)
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
                                // onServiceFound can fire again before stopServiceDiscovery lands; a second resolve with the same listener throws.
                                if (matches(info.serviceName) && resolving.compareAndSet(false, true)) {
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
