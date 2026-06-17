package com.locationjoystick.core.location

import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.SyncPositionUpdate
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LeaderSyncServer"

@Singleton
class LeaderSyncServer
    @Inject
    constructor() {
        @Volatile private var serverSocket: ServerSocket? = null

        private val executor = Executors.newCachedThreadPool()
        private val latestUpdate = AtomicReference<SyncPositionUpdate?>(null)
        private val seq = AtomicLong(0L)

        val port: Int get() = serverSocket?.localPort ?: 0

        fun start(groupId: String): Int {
            // ServerSocket(0) lets the OS pick a free port — avoids TOCTOU race of probe-then-bind.
            val socket = ServerSocket(0, AppConstants.SyncConstants.SERVER_BACKLOG)
            serverSocket = socket
            executor.submit { acceptLoop(socket, groupId) }
            Log.i(TAG, "Leader server started on port ${socket.localPort}")
            return socket.localPort
        }

        fun stop() {
            try {
                serverSocket?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing server socket", e)
            }
            serverSocket = null
            latestUpdate.set(null)
            executor.shutdown()
            Log.i(TAG, "Leader server stopped")
        }

        fun push(update: SyncPositionUpdate) {
            latestUpdate.set(update.copy(seq = seq.incrementAndGet()))
        }

        private fun acceptLoop(
            serverSocket: ServerSocket,
            groupId: String,
        ) {
            try {
                while (!serverSocket.isClosed) {
                    val client = serverSocket.accept()
                    executor.submit { handleRequest(client, groupId) }
                }
            } catch (_: SocketException) {
                // Socket closed — normal shutdown
            } catch (e: Exception) {
                Log.e(TAG, "Accept loop error", e)
            }
        }

        private fun handleRequest(
            socket: Socket,
            groupId: String,
        ) {
            try {
                socket.soTimeout = AppConstants.SyncConstants.POLL_TIMEOUT_MS.toInt()
                socket.use {
                    val reader = BufferedReader(InputStreamReader(it.getInputStream()))
                    val writer = PrintWriter(it.getOutputStream(), true)
                    val requestLine = reader.readLine() ?: return
                    // Consume remaining headers
                    var line = reader.readLine()
                    while (!line.isNullOrBlank()) {
                        line = reader.readLine()
                    }

                    val path = requestLine.substringAfter("GET ").substringBefore(" HTTP")
                    val token = extractQueryParam(path, "token")

                    if (token != groupId) {
                        writer.print("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\n\r\n")
                        writer.flush()
                        return
                    }

                    when {
                        path.startsWith("/health") -> {
                            val body = "{\"status\":\"ok\"}"
                            writer.print("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\n\r\n$body")
                        }

                        path.startsWith("/position") -> {
                            val update = latestUpdate.get()
                            if (update == null) {
                                writer.print("HTTP/1.1 204 No Content\r\nContent-Length: 0\r\n\r\n")
                            } else {
                                val body =
                                    "{\"ts\":${update.timestamp},\"lat\":${update.latitude}," +
                                        "\"lon\":${update.longitude},\"speedMs\":${update.speedMs}," +
                                        "\"bearing\":${update.bearing},\"seq\":${update.seq}}"
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
            } catch (e: Exception) {
                Log.w(TAG, "Error handling client request", e)
            }
        }

        private fun extractQueryParam(
            path: String,
            name: String,
        ): String? {
            val query = path.substringAfter("?", "")
            return query.split("&").firstOrNull { it.startsWith("$name=") }?.substringAfter("=")
        }
    }
