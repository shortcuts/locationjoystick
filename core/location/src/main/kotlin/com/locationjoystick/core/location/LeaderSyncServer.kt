package com.locationjoystick.core.location

import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.SyncPositionUpdate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import java.io.PrintWriter
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LeaderSyncServer"

data class ApiResponse(
    val status: Int,
    val body: String,
)

/** Uniform API error body. Pass fixed strings only (no JSON escaping is done). */
fun apiError(
    status: Int,
    code: String,
    message: String,
) = ApiResponse(status, "{\"error\":{\"code\":\"$code\",\"message\":\"$message\"}}")

private val REASONS =
    mapOf(
        200 to "OK",
        201 to "Created",
        400 to "Bad Request",
        401 to "Unauthorized",
        404 to "Not Found",
        405 to "Method Not Allowed",
        409 to "Conflict",
        413 to "Payload Too Large",
        500 to "Internal Server Error",
    )

/** The API key lives only in [Running], so "API enabled without a running server" cannot exist. */
sealed interface LeaderServerState {
    data object Stopped : LeaderServerState

    data class Running(
        val executor: ScheduledExecutorService,
        val apiKey: String? = null,
    ) : LeaderServerState
}

val LeaderServerState.apiEnabled: Boolean get() = this is LeaderServerState.Running && apiKey != null

@Singleton
class LeaderSyncServer
    @Inject
    constructor() : TokenAuthHttpServer(TAG) {
        private val latestUpdate = AtomicReference<SyncPositionUpdate?>(null)
        private val seq = AtomicLong(0L)

        private val activeFollowers = ConcurrentHashMap<String, Long>()
        private val _followerCount = MutableStateFlow(0)
        val followerCount: StateFlow<Int> = _followerCount.asStateFlow()

        // Path -> method -> handler. Exact match first, then a one-segment `{id}` pattern (e.g. /api/v1/favorites/{id}).
        // ponytail: handlers are non-suspend, run on the connection thread; bridge coroutines with runBlocking.
        private val routes = ConcurrentHashMap<String, MutableMap<String, (ApiRequest) -> ApiResponse>>()

        private val _state = MutableStateFlow<LeaderServerState>(LeaderServerState.Stopped)
        val state: StateFlow<LeaderServerState> = _state.asStateFlow()

        /** Applies [next] to the state and returns the previous one. A null result rejects the transition. */
        private fun transition(next: (LeaderServerState) -> LeaderServerState?): LeaderServerState =
            _state.getAndUpdate { current ->
                next(current) ?: current.also { Log.w(TAG, "ignored transition from $it") }
            }

        init {
            registerApiRoute("GET", "/api/v1/status") {
                ApiResponse(200, "{\"apiVersion\":1,\"role\":\"leader\",\"followers\":${_followerCount.value}}")
            }
        }

        fun registerApiRoute(
            method: String,
            path: String,
            handler: (ApiRequest) -> ApiResponse,
        ) {
            routes.getOrPut(path) { ConcurrentHashMap() }[method] = handler
        }

        /** Registered API routes as path -> methods, for the docs-vs-code smoke test. */
        fun registeredApiRoutes(): Map<String, Set<String>> = routes.mapValues { it.value.keys.toSet() }

        /** Enables the API with [key] (call again to rotate). No-op unless the server is running. */
        fun enableApi(key: String) {
            transition { if (it is LeaderServerState.Running) it.copy(apiKey = key) else null }
        }

        fun disableApi() {
            transition { if (it is LeaderServerState.Running) it.copy(apiKey = null) else it }
        }

        @Synchronized
        fun start(groupId: String): Int {
            if (isRunning) return currentPort
            val port = startServer(groupId)
            val cleanupEx = Executors.newSingleThreadScheduledExecutor()
            transition { if (it is LeaderServerState.Stopped) LeaderServerState.Running(cleanupEx) else null }
            cleanupEx.scheduleWithFixedDelay(
                ::pruneStaleFollowers,
                AppConstants.SyncConstants.POSITION_STALE_THRESHOLD_MS,
                AppConstants.SyncConstants.POSITION_STALE_THRESHOLD_MS,
                TimeUnit.MILLISECONDS,
            )
            return port
        }

        @Synchronized
        fun stop() {
            stopServer()
            val previous = transition { LeaderServerState.Stopped }
            (previous as? LeaderServerState.Running)?.executor?.shutdown()
            latestUpdate.set(null)
            seq.set(0L)
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

        override fun handleApiRequest(
            request: ApiRequest,
            writer: PrintWriter,
        ) {
            val response = routeApi(request)
            val bytes = response.body.toByteArray(Charsets.UTF_8)
            val extra =
                when (response.status) {
                    401 -> "WWW-Authenticate: Bearer\r\n"
                    405 -> "Allow: ${resolve(request.path)?.first?.keys?.sorted()?.joinToString(", ")}\r\n"
                    else -> ""
                }
            writer.print(
                "HTTP/1.1 ${response.status} ${REASONS[response.status]}\r\n$extra" +
                    "Content-Type: application/json\r\nContent-Length: ${bytes.size}\r\n\r\n${response.body}",
            )
            writer.flush()
        }

        /** Exact path, else the same path with its last segment replaced by `{id}`. Returns methods + captured segment. */
        private fun resolve(path: String): Pair<Map<String, (ApiRequest) -> ApiResponse>, String?>? {
            routes[path]?.let { return it to null }
            val segment = path.substringAfterLast('/')
            if (segment.isEmpty()) return null
            val methods = routes[path.substringBeforeLast('/') + "/{id}"] ?: return null
            val decoded = if ('%' in segment) runCatching { URLDecoder.decode(segment, "UTF-8") }.getOrNull() ?: return null else segment
            return methods to decoded
        }

        private fun routeApi(request: ApiRequest): ApiResponse {
            val key = (_state.value as? LeaderServerState.Running)?.apiKey
            if (key == null) return apiError(404, "not_found", "Not found")
            val expected = "Bearer $key".toByteArray(Charsets.UTF_8)
            val given = (request.headers["authorization"] ?: "").toByteArray(Charsets.UTF_8)
            if (!MessageDigest.isEqual(expected, given)) return apiError(401, "unauthorized", "Missing or invalid API key")
            if (request.body == null) return apiError(413, "payload_too_large", "Request body too large")
            val (methods, pathParam) = resolve(request.path) ?: return apiError(404, "not_found", "Not found")
            val handler = methods[request.method] ?: return apiError(405, "method_not_allowed", "Method not allowed")
            return try {
                handler(if (pathParam == null) request else request.copy(pathParam = pathParam))
            } catch (e: Exception) {
                Log.w(TAG, "API handler failed for ${request.path}", e)
                apiError(500, "internal_error", "Internal error")
            }
        }

        override fun handleRequest(
            path: String,
            socket: Socket,
            writer: PrintWriter,
        ) {
            when {
                path.startsWith("/health") -> {
                    val body = "{\"status\":\"ok\"}"
                    writer.print(
                        "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\n\r\n$body",
                    )
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
                                "\"followers\":$count,\"active\":${update.active}," +
                                "\"teleportSeq\":${update.teleportSeq},\"ageMs\":${System.currentTimeMillis() - update.timestamp}}"
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
