package com.locationjoystick.core.location

import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.NetworkUtils
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.Executors

const val API_PREFIX = "/api/v1/"

/** [body] is null when the declared Content-Length is over the cap or unparsable. */
data class ApiRequest(
    val method: String,
    val path: String,
    val query: String,
    val headers: Map<String, String>,
    val body: String?,
    /** Value of the trailing `{id}` segment when a pattern route matched, else null. */
    val pathParam: String? = null,
)

/**
 * A minimal single-endpoint-family HTTP/1.1 server over a raw [ServerSocket], gated (non-API paths only;
 * `/api/v1/` paths do their own auth) by a
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

    val isRunning: Boolean get() = serverSocket?.isClosed == false
    val currentPort: Int get() = serverSocket?.localPort ?: 0

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

    /** Called for `/api/v1/` requests, which bypass the `?token=` gate. Default: bare 404. */
    protected open fun handleApiRequest(
        request: ApiRequest,
        writer: PrintWriter,
    ) {
        writer.print("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n")
        writer.flush()
    }

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
                if (!NetworkUtils.isLanPeer(it.inetAddress)) return
                // latin1: one char == one byte, so Content-Length counts chars correctly.
                val reader = BufferedReader(InputStreamReader(it.getInputStream(), Charsets.ISO_8859_1))
                val writer = PrintWriter(it.getOutputStream(), true)
                val headers = mutableMapOf<String, String>()
                val requestLine: String
                try {
                    requestLine = reader.readLineBounded() ?: return
                    var line = reader.readLineBounded()
                    var count = 0
                    while (!line.isNullOrBlank()) {
                        if (++count > AppConstants.SyncConstants.API_MAX_HEADER_LINES) throw HeadersTooLarge()
                        headers[line.substringBefore(":").trim().lowercase()] = line.substringAfter(":", "").trim()
                        line = reader.readLineBounded()
                    }
                } catch (_: HeadersTooLarge) {
                    writer.print("HTTP/1.1 431 Request Header Fields Too Large\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                    writer.flush()
                    return
                }

                val method = requestLine.substringBefore(" ")
                val path = requestLine.substringAfter(" ").substringBefore(" HTTP")
                if (path.startsWith(API_PREFIX)) {
                    val declared = headers["content-length"]?.toIntOrNull() ?: 0
                    val body =
                        if (declared < 0 || declared > AppConstants.SyncConstants.API_MAX_BODY_BYTES) {
                            null
                        } else {
                            val buf = CharArray(declared)
                            var read = 0
                            while (read < declared) {
                                val n = reader.read(buf, read, declared - read)
                                if (n < 0) break
                                read += n
                            }
                            String(String(buf, 0, read).toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
                        }
                    handleApiRequest(
                        ApiRequest(method, path.substringBefore("?"), path.substringAfter("?", ""), headers, body),
                        writer,
                    )
                    return
                }
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

    private class HeadersTooLarge : Exception()

    // BufferedReader.readLine buffers a newline-free stream without limit.
    private fun BufferedReader.readLineBounded(): String? {
        val sb = StringBuilder()
        while (true) {
            val c = read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            if (sb.length >= AppConstants.SyncConstants.API_MAX_HEADER_LINE_CHARS) throw HeadersTooLarge()
            sb.append(c.toChar())
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
