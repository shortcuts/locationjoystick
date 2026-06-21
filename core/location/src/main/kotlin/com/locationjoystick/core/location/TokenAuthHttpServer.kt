package com.locationjoystick.core.location

import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.Executors

/**
 * A minimal single-endpoint-family HTTP/1.1 server over a raw [ServerSocket], gated by a
 * `?token=` query param shared out-of-band (QR code / typed code / NSD).
 *
 * Shared by [LeaderSyncServer] (Group Sync) and [ExportSyncServer] (QR export) — both serve a
 * tiny JSON payload to exactly one other device on the local network and need nothing more than
 * accept-loop + token-check + GET routing.
 */
abstract class TokenAuthHttpServer(
    private val tag: String,
) {
    @Volatile private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()

    /** Starts the server bound to an OS-assigned port and returns it. */
    protected fun startServer(token: String): Int {
        val socket = ServerSocket(0, AppConstants.SyncConstants.SERVER_BACKLOG)
        serverSocket = socket
        executor.submit { acceptLoop(socket, token) }
        Log.i(tag, "Server started on port ${socket.localPort}")
        return socket.localPort
    }

    protected fun stopServer() {
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.w(tag, "Error closing server socket", e)
        }
        serverSocket = null
        Log.i(tag, "Server stopped")
    }

    /** Called once per accepted connection, after the token has already been verified. */
    protected abstract fun handleRequest(
        path: String,
        socket: Socket,
        writer: PrintWriter,
    )

    /** Override to set per-socket options (e.g. read timeout) before the request is parsed. */
    protected open fun configureSocket(socket: Socket) {}

    private fun acceptLoop(
        serverSocket: ServerSocket,
        token: String,
    ) {
        try {
            while (!serverSocket.isClosed) {
                val client = serverSocket.accept()
                executor.submit { handleClient(client, token) }
            }
        } catch (_: SocketException) {
            // Socket closed — normal shutdown
        } catch (e: Exception) {
            Log.e(tag, "Accept loop error", e)
        }
    }

    private fun handleClient(
        socket: Socket,
        token: String,
    ) {
        try {
            configureSocket(socket)
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
                val requestToken = extractQueryParam(path, "token")

                if (requestToken != token) {
                    writer.print("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\n\r\n")
                    writer.flush()
                    return
                }

                handleRequest(path, it, writer)
            }
        } catch (e: Exception) {
            Log.w(tag, "Error handling client request", e)
        }
    }

    protected fun extractQueryParam(
        path: String,
        name: String,
    ): String? {
        val query = path.substringAfter("?", "")
        return query.split("&").firstOrNull { it.startsWith("$name=") }?.substringAfter("=")
    }
}
