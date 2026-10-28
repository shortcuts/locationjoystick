package com.locationjoystick.feature.settings.impl

import com.locationjoystick.core.common.util.NsdCodeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns one QR export share: the HTTP server plus its NSD advertisement.
 *
 * Every [start] and [stop] waits for the previous one to end, so a stop always wins over a
 * start still in flight and a start never runs while an older stop is unfinished.
 */
internal class ExportShareSession(
    private val server: ExportSyncServer,
    private val nsd: NsdCodeManager,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null
    private var code: String? = null

    /** Runs [block]; its `serve` argument starts the server and returns the bound port. */
    fun start(block: suspend (serve: (token: String, json: String) -> Int) -> Unit) {
        val previous = job
        job =
            scope.launch(Dispatchers.IO) {
                previous?.cancelAndJoin()
                block { token, json ->
                    ensureActive()
                    server.start(token, json).also {
                        nsd.startAdvertising(token, it, export = true)
                        code = token
                    }
                }
            }
    }

    fun stop() {
        val previous = job
        job =
            scope.launch(Dispatchers.IO) {
                // A later start cancels this job; the teardown must still finish.
                withContext(NonCancellable) {
                    previous?.cancelAndJoin()
                    server.stop()
                    code?.let(nsd::stopAdvertising)
                    code = null
                }
            }
    }
}
