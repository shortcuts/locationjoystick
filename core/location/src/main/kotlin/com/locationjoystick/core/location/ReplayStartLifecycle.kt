package com.locationjoystick.core.location

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

private const val TAG = "ReplayStartLifecycle"

/**
 * Owns the start generation counter, the per-start phase and the lock that
 * keeps them atomic with [ReplayOrchestrator]'s mode changes — extracted from the orchestrator,
 * mirroring [FollowerCatchUpCoordinator]: state ownership lives in one small class. Engine and
 * repository side effects stay in the orchestrator.
 */
internal class ReplayStartLifecycle {
    /** Where a start is. Planning and the approach walk are both [Starting]; the engine takes over in [EngineRunning]. */
    sealed interface Phase {
        val paused: Boolean

        data class Starting(
            override val paused: Boolean = false,
        ) : Phase

        data class EngineRunning(
            override val paused: Boolean = false,
        ) : Phase
    }

    /** Pause survives planning and the approach walk without cancelling their saved progress. */
    class Session(
        val generation: Int,
    ) {
        // Written only through the lifecycle's transition functions, under its lock.
        internal val phase = MutableStateFlow<Phase>(Phase.Starting())
        val paused: Boolean get() = phase.value.paused
        val engineStarted: Boolean get() = phase.value is Phase.EngineRunning
    }

    private val generation = AtomicInteger(0)

    @PublishedApi internal val lock = Any()

    @Volatile var current: Session? = null
        private set

    inline fun <T> locked(block: () -> T): T = synchronized(lock, block)

    fun begin(): Session =
        locked {
            val previous = current
            Session(generation.incrementAndGet()).also {
                current = it
                previous?.let { setPaused(it, false) }
            }
        }

    /** Invalidates the current session and wakes its paused callbacks so they discard themselves. */
    fun abort() {
        locked {
            generation.incrementAndGet()
            val previous = current
            current = null
            previous?.let { setPaused(it, false) }
        }
    }

    fun setPaused(
        session: Session,
        paused: Boolean,
    ) = locked {
        session.phase.update {
            when (it) {
                is Phase.Starting -> it.copy(paused = paused)
                is Phase.EngineRunning -> it.copy(paused = paused)
            }
        }
    }

    fun markEngineStarted(session: Session) =
        locked {
            session.phase.update {
                if (it is Phase.Starting) {
                    Phase.EngineRunning(it.paused)
                } else {
                    Log.w(TAG, "ignored engine start from $it")
                    it
                }
            }
        }

    /** The return walk after a route leaves the engine phase and runs under [Phase.Starting] again. */
    fun markEngineStopped(session: Session) =
        locked {
            session.phase.update {
                if (it is Phase.EngineRunning) {
                    Phase.Starting(it.paused)
                } else {
                    Log.w(TAG, "ignored engine stop from $it")
                    it
                }
            }
        }

    fun clear() = locked { current = null }

    fun isCurrent(generation: Int): Boolean = generation == this.generation.get()

    /** True while a session exists whose engine has not started (planning / approach walk). */
    fun isStarting(engineJobActive: Boolean = true): Boolean = current?.let { !it.engineStarted && engineJobActive } ?: false

    suspend fun awaitResume(session: Session) {
        session.phase.first { !it.paused }
        coroutineContext.ensureActive()
        if (!isCurrent(session.generation)) throw CancellationException("Replay replaced")
    }

    suspend fun runWhenResumed(
        session: Session,
        action: () -> Unit,
    ) {
        while (true) {
            awaitResume(session)
            val started =
                locked {
                    if (session !== current || session.paused) {
                        false
                    } else {
                        action()
                        true
                    }
                }
            if (started) return
        }
    }
}
