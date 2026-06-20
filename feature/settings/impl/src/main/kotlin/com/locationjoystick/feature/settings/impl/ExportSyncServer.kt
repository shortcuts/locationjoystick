package com.locationjoystick.feature.settings.impl

import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ExportSyncServer"

/**
 * Serves a single export JSON payload to one other device over the local network.
 *
 * Started only while the QR share dialog is open (see [SettingsViewModel.prepareQrExport]
 * / [SettingsViewModel.stopQrExport]) — never runs otherwise.
 */
@Singleton
class ExportSyncServer
    @Inject
    constructor() {
        @Volatile private var serverSocket: ServerSocket? = null
        private val executor = Executors.newCachedThreadPool()

        /** Starts the server and returns the bound port. */
        fun start(
            token: String,
            json: String,
        ): Int {
            val socket = ServerSocket(0, AppConstants.SyncConstants.SERVER_BACKLOG)
            serverSocket = socket
            executor.submit { acceptLoop(socket, token, json) }
            Log.i(TAG, "Export server started on port ${socket.localPort}")
            return socket.localPort
        }

        fun stop() {
            try {
                serverSocket?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing server socket", e)
            }
            serverSocket = null
            Log.i(TAG, "Export server stopped")
        }

        private fun acceptLoop(
            serverSocket: ServerSocket,
            token: String,
            json: String,
        ) {
            try {
                while (!serverSocket.isClosed) {
                    val client = serverSocket.accept()
                    executor.submit { handleRequest(client, token, json) }
                }
            } catch (_: SocketException) {
                // Socket closed — normal shutdown
            } catch (e: Exception) {
                Log.e(TAG, "Accept loop error", e)
            }
        }

        private fun handleRequest(
            socket: Socket,
            token: String,
            json: String,
        ) {
            try {
                socket.use {
                    val reader = BufferedReader(InputStreamReader(it.getInputStream()))
                    val writer = PrintWriter(it.getOutputStream(), true)
                    val requestLine = reader.readLine() ?: return
                    var line = reader.readLine()
                    while (!line.isNullOrBlank()) {
                        line = reader.readLine()
                    }

                    val path = requestLine.substringAfter("GET ").substringBefore(" HTTP")
                    val requestToken = extractQueryParam(path, "token")

                    if (requestToken != token) {
                        writer.print("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\n\r\n")
                        writer.flush()
                        return
                    }

                    if (path.startsWith("/export")) {
                        val bytes = json.toByteArray(Charsets.UTF_8)
                        writer.print(
                            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\n\r\n",
                        )
                        writer.flush()
                        it.getOutputStream().write(bytes)
                        it.getOutputStream().flush()
                    } else {
                        writer.print("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n")
                        writer.flush()
                    }
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
