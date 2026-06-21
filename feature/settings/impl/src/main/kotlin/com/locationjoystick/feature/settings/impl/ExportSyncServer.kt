package com.locationjoystick.feature.settings.impl

import com.locationjoystick.core.location.TokenAuthHttpServer
import java.io.PrintWriter
import java.net.Socket
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
    constructor() : TokenAuthHttpServer(TAG) {
        private var json: String = ""

        /** Starts the server and returns the bound port. */
        fun start(
            token: String,
            json: String,
        ): Int {
            this.json = json
            return startServer(token)
        }

        fun stop() {
            stopServer()
            json = ""
        }

        override fun handleRequest(
            path: String,
            socket: Socket,
            writer: PrintWriter,
        ) {
            if (path.startsWith("/export")) {
                val bytes = json.toByteArray(Charsets.UTF_8)
                writer.print(
                    "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\n\r\n",
                )
                writer.flush()
                socket.getOutputStream().write(bytes)
                socket.getOutputStream().flush()
            } else {
                writer.print("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n")
                writer.flush()
            }
        }
    }
