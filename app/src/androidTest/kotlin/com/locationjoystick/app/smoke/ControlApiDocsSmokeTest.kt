package com.locationjoystick.app.smoke

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.locationjoystick.core.data.GroupRepository
import com.locationjoystick.core.location.LeaderSyncServer
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/**
 * Checks that the leader exposes exactly the endpoints documented in `docs/wiki/control-api.html`, not what they do.
 * Method OPTIONS is registered nowhere, so each documented path answers 405 with an `Allow` header listing
 * its real methods. No handler runs, so the probe has no side effects.
 */
@HiltAndroidTest
class ControlApiDocsSmokeTest : BaseSmokeTest() {
    @Inject lateinit var groupRepository: GroupRepository

    @Inject lateinit var leaderSyncServer: LeaderSyncServer

    private val endpointRegex = Regex("""method-\w+">(\w+)</span><code class="endpoint-path">(/[^<]*)</code>""")

    private fun documented(): Map<String, Set<String>> {
        val html =
            InstrumentationRegistry
                .getInstrumentation()
                .context.assets
                .open("control-api.html")
                .bufferedReader()
                .readText()
        val byPath = mutableMapOf<String, MutableSet<String>>()
        endpointRegex.findAll(html).forEach { byPath.getOrPut(it.groupValues[2]) { mutableSetOf() }.add(it.groupValues[1]) }
        assertTrue("no endpoints parsed from control-api.html", byPath.isNotEmpty())
        return byPath
    }

    @Before
    override fun setup() {
        super.setup()
        runBlocking { groupRepository.leaveGroup() }
        composeRule.waitForIdleScreen()
        composeRule.navigateViaDrawer("Group Sync")
    }

    private fun request(
        port: Int,
        path: String,
        method: String,
        key: String?,
    ): HttpURLConnection {
        val conn = URL("http://127.0.0.1:$port/api/v1$path").openConnection() as HttpURLConnection
        conn.requestMethod = method
        key?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
        return conn
    }

    @Test
    fun leader_exposes_documented_api() {
        composeRule.onNodeWithText("Create group: I'm the leader").performClick()
        composeRule.waitUntil(10_000) { runBlocking { groupRepository.groupState.first().leaderPort } != null }
        val port = runBlocking { groupRepository.groupState.first().leaderPort!! }

        // Documented: "If the Control API is off, all /api/v1/ paths return 404".
        assertEquals(404, request(port, "/status", "GET", null).responseCode)

        // Only the Switch is clickable, not its label; the Sharing switch has a different label sibling.
        composeRule
            .onNode(isToggleable() and hasAnySibling(hasText("Control API")))
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        // The credentials card only renders once the server reports the API as enabled.
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText("Regenerate key").fetchSemanticsNodes().isNotEmpty() }
        val key = runBlocking { groupRepository.getOrCreateApiKey() }

        // Documented: missing or wrong key is 401 with `WWW-Authenticate: Bearer`.
        request(port, "/status", "GET", "wrong").let {
            assertEquals(401, it.responseCode)
            assertEquals("Bearer", it.getHeaderField("WWW-Authenticate"))
        }

        // Documented: errors are `{"error":{"code","message"}}`.
        val error = JSONObject(request(port, "/status", "OPTIONS", key).let { it.errorStream.bufferedReader().readText() })
        assertNotNull(error.getJSONObject("error").getString("code"))
        assertNotNull(error.getJSONObject("error").getString("message"))

        val docs = documented()
        // Catches routes that exist but are undocumented (the probe below only sees documented paths).
        assertEquals(docs, leaderSyncServer.registeredApiRoutes().mapKeys { it.key.removePrefix("/api/v1") })

        docs.forEach { (path, methods) ->
            val conn = request(port, path.replace("{id}", "probe"), "OPTIONS", key)
            assertEquals("$path: not routed", 405, conn.responseCode)
            val allow =
                conn
                    .getHeaderField("Allow")
                    .split(",")
                    .map { it.trim() }
                    .toSet()
            assertEquals("$path: documented methods differ from exposed", methods, allow)
        }
    }
}
