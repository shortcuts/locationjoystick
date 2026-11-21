package com.locationjoystick.core.location

import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.isPaused
import com.locationjoystick.core.routing.OsrmClient
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Chains every ordered pair of position-change entry points, and every triple of the
 * target-setting ones, then checks what the map and the repository show after the last action.
 * Overlay entry points (floating map, Tap to Walk) end in the same MapController calls as the
 * in-app map, so they have no steps of their own. Add a [Kind] when a new entry point appears.
 */
private val START = LatLng(48.8566, 2.3522)
private val ROUTE_END = LatLng(48.9500, 2.4000)
private val POINTS = listOf(LatLng(48.9000, 2.3522), LatLng(48.8700, 2.3000), LatLng(48.8800, 2.3300))

private enum class Schedule { SETTLED, BACK_TO_BACK }

// Cells known to fail on a real product defect. Each needs a backlog id next to its name.
private val KNOWN_GAPS = emptySet<String>()

private sealed interface Walk {
    data object Idle : Walk

    data class Walking(
        val target: LatLng,
        val viaRoads: Boolean,
    ) : Walk

    data class Ephemeral(
        val last: LatLng,
    ) : Walk
}

/** What a user-facing surface must show. A null [mode] means the service owns it and the test cannot know. */
private data class Expect(
    val walk: Walk = Walk.Idle,
    val trace: LatLng? = null,
    val mode: MockMode? = MockMode.TELEPORT,
    val position: LatLng? = null,
    val paused: Boolean = false,
) {
    val target: LatLng? get() = (walk as? Walk.Walking)?.target
}

private class Ctx(
    val h: Harness,
    val scope: TestScope,
)

private class Gate(
    var pending: CompletableDeferred<Unit>? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
private enum class Kind(
    val async: Boolean,
) {
    WalkHere(false) {
        override fun run(
            c: Ctx,
            p: LatLng,
        ) = c.h.mapController.walkTo(p)

        override fun next(
            prev: Expect,
            p: LatLng,
        ) = Expect(Walk.Walking(p, false), mode = MockMode.WALK_TO)
    },
    WalkViaRoads(true) {
        override fun run(
            c: Ctx,
            p: LatLng,
        ) = c.h.mapController.walkViaRoads(p)

        override fun next(
            prev: Expect,
            p: LatLng,
        ) = Expect(Walk.Walking(p, true), trace = p, mode = MockMode.WALK_TO)
    },
    Teleport(true) {
        override fun run(
            c: Ctx,
            p: LatLng,
        ) = c.h.mapController.teleportTo(p)

        override fun next(
            prev: Expect,
            p: LatLng,
        ) = Expect(position = p)
    },
    ExternalTeleport(true) {
        override fun run(
            c: Ctx,
            p: LatLng,
        ) = c.scope.launchTeleport(c, p)

        override fun next(
            prev: Expect,
            p: LatLng,
        ) = Expect(position = p)
    },
    AddNextPoint(true) {
        override fun run(
            c: Ctx,
            p: LatLng,
        ) {
            c.h.mapController.addEphemeralWaypoint(p)
            c.scope.runCurrent()
            // The service owns ROUTE_REPLAY once the replay intent lands.
            if (c.h.ephemeralController.pendingWaypoints.value
                    .isNotEmpty()
            ) {
                c.h.locationRepository.setMockMode(MockMode.ROUTE_REPLAY)
            }
        }

        override fun offered(prev: Expect) = prev.walk is Walk.Walking || prev.walk is Walk.Ephemeral

        override fun next(
            prev: Expect,
            p: LatLng,
        ) = Expect(Walk.Ephemeral(p), mode = MockMode.ROUTE_REPLAY, position = prev.position)
    },
    StartRoaming(true) {
        override fun run(
            c: Ctx,
            p: LatLng,
        ) = c.h.mapController.startRoaming(RoamingDefaults(), p)

        override fun next(
            prev: Expect,
            p: LatLng,
        ) = Expect(mode = MockMode.ROAMING, position = prev.position)
    },
    StartRoute(true) {
        override fun run(
            c: Ctx,
            p: LatLng,
        ) = c.h.mapController.startRouteReplay("r")

        override fun next(
            prev: Expect,
            p: LatLng,
        ) = Expect(trace = ROUTE_END, mode = MockMode.ROUTE_REPLAY, position = prev.position)
    },
    Joystick(false) {
        override fun run(
            c: Ctx,
            p: LatLng,
        ) = c.h.mapController.pauseAutomatedMovement()

        override fun next(
            prev: Expect,
            p: LatLng,
        ) = if (prev.walk is Walk.Walking) prev.copy(paused = true) else prev
    },
    Stop(false) {
        override fun run(
            c: Ctx,
            p: LatLng,
        ) = c.h.mapController.stopWalk()

        override fun next(
            prev: Expect,
            p: LatLng,
        ) = prev.copy(
            walk = Walk.Idle,
            trace = null,
            mode =
                if (prev.mode ==
                    MockMode.WALK_TO
                ) {
                    MockMode.TELEPORT
                } else {
                    prev.mode
                },
            paused = false,
        )
    },
    ;

    // The UI offers a step only in some states: add next point is valid only during a walk or an ephemeral replay.
    open fun offered(prev: Expect): Boolean = true

    abstract fun run(
        c: Ctx,
        p: LatLng,
    )

    abstract fun next(
        prev: Expect,
        p: LatLng,
    ): Expect
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun TestScope.launchTeleport(
    c: Ctx,
    p: LatLng,
) {
    // Favorites, routes, links, capture, the Control API and the external command all call TeleportUseCase directly.
    backgroundScope.launch { c.h.teleportUseCase.execute(p) }
}

/** A state the app can be in before the last action, built by real steps. */
private enum class First(
    val steps: List<Kind>,
    val roadPending: Boolean = false,
) {
    Idle(emptyList()),
    Walking(listOf(Kind.WalkHere)),
    RoadLookupPending(listOf(Kind.WalkViaRoads), roadPending = true),
    WalkPaused(listOf(Kind.WalkHere, Kind.Joystick)),
    RoadWalk(listOf(Kind.WalkViaRoads)),
    EphemeralReplay(listOf(Kind.WalkHere, Kind.AddNextPoint)),
    Roaming(listOf(Kind.StartRoaming)),
    RouteReplay(listOf(Kind.StartRoute)),
}

private data class Cell(
    val name: String,
    val first: First?,
    val steps: List<Kind>,
    val schedule: Schedule,
)

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class PositionChangeMatrixTest(
    private val name: String,
    private val cell: Any,
) {
    @Before
    fun setUp() {
        mockkObject(MockLocationIntentBuilder)
        every { MockLocationIntentBuilder.startEphemeralReplay(any(), any(), any(), any()) } returns mockk(relaxed = true)
        every { MockLocationIntentBuilder.appendWaypoint(any(), any(), any()) } returns mockk(relaxed = true)
        every { MockLocationIntentBuilder.cancelRouteReplay(any()) } returns mockk(relaxed = true)
        every { MockLocationIntentBuilder.updatePosition(any(), any(), any(), any(), any()) } returns mockk(relaxed = true)
        every { MockLocationIntentBuilder.pauseRouteReplay(any()) } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkObject(MockLocationIntentBuilder)
    }

    @Test
    fun cell_leaves_the_state_of_its_last_action() {
        if (name in KNOWN_GAPS) return
        val c = cell as Cell
        runTest(StandardTestDispatcher()) {
            val gate = Gate()
            val osrm =
                mockk<OsrmClient>(relaxed = true).also {
                    coEvery { it.getRoute(any(), any()) } coAnswers {
                        gate.pending?.await()
                        Result.success(secondArg<List<LatLng>>())
                    }
                    coEvery { it.resolveRoute(any(), any(), any(), any(), any()) } answers {
                        listOf(secondArg<LatLng>(), thirdArg<LatLng>())
                    }
                }
            val h = buildHarness(backgroundScope, osrm)
            h.stubEntryPoints(ROUTE_END)
            val ctx = Ctx(h, this)
            runCurrent()
            h.locationRepository.setPositionInternal(START)
            runCurrent()

            var expect = Expect()
            var argIndex = 0

            fun perform(kind: Kind) {
                if (!kind.offered(expect)) return
                val p = POINTS[argIndex++ % POINTS.size]
                kind.run(ctx, p)
                expect = kind.next(expect, p)
                if (c.schedule == Schedule.SETTLED || kind.async) runCurrent()
            }
            c.first?.let { first ->
                if (first.roadPending) gate.pending = CompletableDeferred()
                first.steps.forEach(::perform)
                if (first.roadPending) expect = Expect()
                gate.pending = null
            }
            c.steps.forEach(::perform)
            runCurrent()

            assertState(h, expect, c.name)
        }
    }

    private fun assertState(
        h: Harness,
        e: Expect,
        cell: String,
    ) {
        val shared = h.mapController.sharedState.value
        val repo = h.locationRepository
        when (val w = e.walk) {
            Walk.Idle -> assertEquals("$cell: walkMode", WalkMode.Idle, shared.walkMode)
            is Walk.Walking -> {
                val actual = shared.walkMode
                assertTrue("$cell: walkMode was $actual", actual is WalkMode.Walking)
                actual as WalkMode.Walking
                assertEquals("$cell: walk target", w.target, actual.target)
                assertEquals("$cell: via roads", w.viaRoads, actual.isViaRoads)
            }
            is Walk.Ephemeral -> {
                val actual = shared.walkMode
                assertTrue("$cell: walkMode was $actual", actual is WalkMode.EphemeralReplay)
                assertEquals("$cell: last waypoint", w.last, (actual as WalkMode.EphemeralReplay).waypoints.last())
            }
        }
        assertEquals("$cell: repository walkTarget", e.target, repo.walkTarget.value)
        assertEquals("$cell: route trace end", e.trace, shared.routeTrace?.last())
        if (e.walk !is Walk.Ephemeral) {
            assertTrue(
                "$cell: leaked waypoints",
                h.ephemeralController.pendingWaypoints.value
                    .isEmpty(),
            )
        }
        e.mode?.let { assertEquals("$cell: mode", it, repo.currentMode.value) }
        e.position?.let { assertEquals("$cell: position", it, repo.currentPosition.value) }
        if (e.paused) assertTrue("$cell: paused", repo.currentMovement().isPaused(MockMode.WALK_TO))
    }

    companion object {
        private val walkKinds = listOf(Kind.WalkHere, Kind.WalkViaRoads, Kind.Teleport, Kind.AddNextPoint)

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cells(): List<Array<Any>> {
            val pairs =
                First.entries.flatMap { first -> Kind.entries.map { first to listOf(it) } }
            val triples =
                walkKinds.flatMap { a -> walkKinds.flatMap { b -> walkKinds.map { c -> null to listOf(a, b, c) } } }
            return (pairs + triples).flatMap { (first, steps) ->
                Schedule.entries.map { schedule ->
                    val label = listOfNotNull(first?.name).plus(steps.map { it.name }).joinToString(" then ")
                    val name = "$label [$schedule]"
                    arrayOf<Any>(name, Cell(name, first, steps, schedule))
                }
            }
        }
    }
}
