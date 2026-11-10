package com.locationjoystick.feature.settings.impl

import android.util.Log
import com.locationjoystick.core.common.util.NsdCodeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns one QR export share: the HTTP server plus its NSD advertisement.
 *
 * Every [start] and [stop] waits for the previous one to end, so a stop always wins over a
 * start still in flight and a start never runs while an older stop is unfinished.
 */
internal sealed interface ShareState {
    val job: Job?
    val code: String?

    data object Idle : ShareState {
        override val job: Job? = null
        override val code: String? = null
    }

    /** [code] is a stale advertisement that [ExportShareSession.stop] must still end. */
    data class Starting(
        override val job: Job,
        override val code: String?,
    ) : ShareState

    data class Serving(
        override val job: Job,
        override val code: String,
    ) : ShareState

    data class Stopping(
        override val job: Job,
        override val code: String?,
    ) : ShareState
}

internal class ExportShareSession(
    private val server: ExportSyncServer,
    private val nsd: NsdCodeManager,
    private val scope: CoroutineScope,
) {
    private val state = MutableStateFlow<ShareState>(ShareState.Idle)

    val current: ShareState get() = state.value

    /** Runs [block]; its `serve` argument starts the server and returns the bound port. */
    fun start(block: suspend (serve: (token: String, json: String) -> Int) -> Unit) {
        val previous = current.job
        val job =
            scope.launch(Dispatchers.IO, CoroutineStart.LAZY) {
                previous?.cancelAndJoin()
                block { token, json ->
                    ensureActive()
                    server.start(token, json).also {
                        nsd.startAdvertising(token, it, export = true)
                        transition { s ->
                            when (s) {
                                is ShareState.Starting -> ShareState.Serving(s.job, token)
                                is ShareState.Serving -> s.copy(code = token)
                                is ShareState.Stopping -> s.copy(code = token)
                                ShareState.Idle -> null
                            }
                        }
                    }
                }
            }
        transition { s -> ShareState.Starting(job, s.code) }
        job.start()
    }

    fun stop() {
        val previous = current.job
        val job =
            scope.launch(Dispatchers.IO, CoroutineStart.LAZY) {
                val self = coroutineContext[Job]
                // A later start cancels this job; the teardown must still finish.
                withContext(NonCancellable) {
                    previous?.cancelAndJoin()
                    server.stop()
                    current.code?.let(nsd::stopAdvertising)
                    transition { s ->
                        when (s) {
                            is ShareState.Stopping -> if (s.job === self) ShareState.Idle else s.copy(code = null)
                            is ShareState.Starting -> s.copy(code = null)
                            is ShareState.Serving -> null
                            ShareState.Idle -> null
                        }
                    }
                }
            }
        transition { s -> ShareState.Stopping(job, s.code) }
        job.start()
    }

    private inline fun transition(next: (ShareState) -> ShareState?) {
        state.update { s -> next(s) ?: s.also { Log.w(TAG, "ignored transition from $it") } }
    }

    private companion object {
        const val TAG = "ExportShareSession"
    }
}
