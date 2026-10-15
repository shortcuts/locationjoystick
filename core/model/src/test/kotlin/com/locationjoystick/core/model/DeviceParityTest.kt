package com.locationjoystick.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Parity against bench fixtures exported per device by `scripts/export_device_golden.py` (regenerate command and
 * provenance in docs/testing.md "Bench parity fixtures"). A failure here is a port defect: do not widen tolerances.
 */
class DeviceParityTest {
    private val devices = listOf("pixel5_1080x2340", "pixel7_1080x2400")

    private fun resource(
        device: String,
        name: String,
    ): List<String> {
        val stream = javaClass.getResourceAsStream("/device_golden/$device/$name")
        assertNotNull("missing fixture $device/$name", stream)
        return stream!!
            .readBytes()
            .decodeToString()
            .trim()
            .lines()
    }

    private fun meta(device: String): Map<String, String> =
        resource(device, "meta.txt").associate { line ->
            val (key, value) = line.split(" ", limit = 2)
            key to value
        }

    private fun moves(device: String): List<LegMove> =
        resource(device, "moves.txt")
            .map { line -> line.split(" ").map { it.toDouble() } }
            .map { v -> LegMove(GroundOffset(v[0], v[1]), listOf(BandFlow(v[2], v[3], v[4], v[5], v[6]))) }

    @Test
    fun fitMatchesBenchPerDevice() {
        for (device in devices) {
            val m = meta(device)
            val width = m.getValue("width").toInt()
            val height = m.getValue("height").toInt()
            val feet = resource(device, "fit.txt")[1].split(" ").map { it.toDouble() }
            val e = resource(device, "fit.txt")[0].split(" ").map { it.toDouble() }
            val fit = GroundMapFit.fit(moves(device), feet[0], feet[1], width, height)
            assertNotNull("$device: fit returned null", fit)
            val c = fit!!.calibration
            // bench returns the mirrored equivalent (s < 0, theta + 180); the port reports the positive-scale one
            val flip = e[3] < 0
            val sg = if (flip) -1.0 else 1.0
            val theta = e[2] - if (flip) 180.0 else 0.0
            assertEquals("$device theta", theta, c.headingDeg, 1.5)
            assertEquals("$device lateral", sg * e[3], c.lateralPxPerM, 0.03 * abs(e[3]))
            assertEquals("$device forward", sg * e[4], c.forwardPxPerM, 0.03 * abs(e[4]))
            assertEquals("$device perspective", sg * e[5], c.perspective, 0.05 * abs(e[5]))
            assertEquals("$device median residual", e[6], fit.medianAbsPx, 0.3)
            assertEquals("$device flow count", e[7].toInt() * 2, fit.flowCount)
        }
    }

    @Test
    fun tapOffsetsMatchBenchPerDevice() {
        for (device in devices) {
            val m = meta(device)
            val width = m.getValue("width").toInt()
            val height = m.getValue("height").toInt()
            val feet = resource(device, "fit.txt")[1].split(" ").map { it.toDouble() }
            val fit = GroundMapFit.fit(moves(device), feet[0], feet[1], width, height)!!
            val map = GroundMap(fit.calibration, width.toDouble(), height.toDouble())
            for (line in resource(device, "taps.txt")) {
                val v = line.split(" ").map { it.toDouble() }
                val probe = "$device probe (${v[0]}, ${v[1]})"
                val actual = map.pixelToGround(v[0], v[1])
                assertNotNull("$probe: no ground point", actual)
                val tol = maxOf(1.0, 0.03 * hypot(v[2], v[3]))
                assertEquals("$probe east", v[2], actual!!.eastMeters, tol)
                assertEquals("$probe north", v[3], actual.northMeters, tol)
            }
        }
    }
}
