package com.locationjoystick.app.di

import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.routing.OsrmClient
import com.locationjoystick.core.routing.di.OsrmClientModule
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Singleton
import kotlin.concurrent.thread

/** Local stand-in for the OSRM servers: answers every route request with [shape] applied to the requested points. */
object FakeRoad {
    private val requests = AtomicInteger()

    @Volatile var shape: (List<LatLng>) -> List<LatLng> = { it }

    val requestCount: Int get() = requests.get()

    private val server =
        MockWebServer().apply {
            dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        requests.incrementAndGet()
                        val coordinates =
                            request.path
                                .orEmpty()
                                .substringAfterLast('/')
                                .substringBefore('?')
                        val requested =
                            coordinates.split(';').map { pair ->
                                val (lon, lat) = pair.split(',').map(String::toDouble)
                                LatLng(lat, lon)
                            }
                        val body =
                            JSONObject()
                                .put("code", "Ok")
                                .put(
                                    "routes",
                                    JSONArray().put(
                                        JSONObject()
                                            .put(
                                                "geometry",
                                                JSONObject()
                                                    .put("type", "LineString")
                                                    .put(
                                                        "coordinates",
                                                        JSONArray(shape(requested).map { JSONArray(listOf(it.longitude, it.latitude)) }),
                                                    ),
                                            ).put("distance", 0.0)
                                            .put("duration", 0.0),
                                    ),
                                )
                        return MockResponse().setBody(body.toString())
                    }
                }
        }

    // StrictMode forbids the host lookup in start() on the main thread, where Hilt builds the client.
    val baseUrl: String by lazy {
        thread { server.start() }.join()
        "http://127.0.0.1:${server.port}"
    }

    fun reset() {
        shape = { it }
        requests.set(0)
    }
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [OsrmClientModule::class])
object FakeRoadModule {
    @Provides
    @Singleton
    fun provideOsrmClient(): OsrmClient = OsrmClient(FakeRoad.baseUrl)
}
