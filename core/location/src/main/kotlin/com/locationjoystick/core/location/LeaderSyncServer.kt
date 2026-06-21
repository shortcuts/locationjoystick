package com.locationjoystick.core.location

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.SyncPositionUpdate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.PrintWriter
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LeaderSyncServer"

@Singleton
class LeaderSyncServer
    @Inject
    constructor() : TokenAuthHttpServer(TAG) {
        private val latestUpdate = AtomicReference<SyncPositionUpdate?>(null)
        private val seq = AtomicLong(0L)

        private val activeFollowers = ConcurrentHashMap<String, Long>()
        private val _followerCount = MutableStateFlow(0)
        val followerCount: StateFlow<Int> = _followerCount.asStateFlow()
        private var cleanupExecutor: ScheduledExecutorService? = null

        fun start(groupId: String): Int {
            val port = startServer(groupId)
            val cleanupEx = Executors.newSingleThreadScheduledExecutor()
            cleanupExecutor = cleanupEx
            cleanupEx.scheduleAtFixedRate(
                ::pruneStaleFollowers,
                AppConstants.SyncConstants.POSITION_STALE_THRESHOLD_MS,
                AppConstants.SyncConstants.POSITION_STALE_THRESHOLD_MS,
                TimeUnit.MILLISECONDS,
            )
            return port
        }

        fun stop() {
            stopServer()
            latestUpdate.set(null)
            seq.set(0L)
            cleanupExecutor?.shutdown()
            cleanupExecutor = null
            activeFollowers.clear()
            _followerCount.value = 0
        }

        private fun pruneStaleFollowers() {
            val threshold = System.currentTimeMillis() - AppConstants.SyncConstants.POSITION_STALE_THRESHOLD_MS
            activeFollowers.entries.removeIf { it.value < threshold }
            _followerCount.value = activeFollowers.size
        }

        fun push(update: SyncPositionUpdate) {
            latestUpdate.set(update.copy(seq = seq.incrementAndGet()))
        }

        override fun configureSocket(socket: Socket) {
            socket.soTimeout = AppConstants.SyncConstants.POLL_TIMEOUT_MS.toInt()
        }

        override fun handleRequest(
            path: String,
            socket: Socket,
            writer: PrintWriter,
        ) {
            when {
                path.startsWith("/health") -> {
                    val body = "{\"status\":\"ok\"}"
                    writer.print("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\n\r\n$body")
                }

                path.startsWith("/position") -> {
                    val ip = socket.inetAddress.hostAddress ?: "unknown"
                    activeFollowers[ip] = System.currentTimeMillis()
                    _followerCount.value = activeFollowers.size
                    val update = latestUpdate.get()
                    if (update == null) {
                        writer.print("HTTP/1.1 204 No Content\r\nContent-Length: 0\r\n\r\n")
                    } else {
                        val count = _followerCount.value
                        val body =
                            "{\"ts\":${update.timestamp},\"lat\":${update.latitude}," +
                                "\"lon\":${update.longitude},\"speedMs\":${update.speedMs}," +
                                "\"bearing\":${update.bearing},\"seq\":${update.seq}," +
                                "\"followers\":$count}"
                        writer.print(
                            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\n\r\n$body",
                        )
                    }
                }

                else -> {
                    writer.print("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n")
                }
            }
            writer.flush()
        }
    }
