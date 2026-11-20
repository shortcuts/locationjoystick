package com.locationjoystick.core.designsystem.component

import com.locationjoystick.core.designsystem.R
import org.junit.Assert.assertEquals
import org.junit.Test

class CooldownTextTest {
    private val names =
        mapOf(
            R.string.cooldown_time_hours_minutes to "hm",
            R.string.cooldown_time_minutes_seconds to "ms",
            R.string.cooldown_time_seconds to "s",
            R.string.cooldown_distance_km to "km",
            R.string.cooldown_distance_m to "m",
            R.string.cooldown_advisory_label to "advisory",
            R.string.cooldown_standing_label to "standing",
            R.string.cooldown_badge_suggested_wait to "wait",
            R.string.cooldown_badge_distance_away to "away",
            R.string.cooldown_badge_no_wait to "nowait",
        )
    private val fake: StringLookup = { id, args -> "${names.getValue(id)}(${args.joinToString(",")})" }

    private fun advisory(
        s: Long,
        m: Double,
    ) = cooldownAdvisoryLabel(s, m, fake)

    @Test
    fun `standing label shows remaining time and finish time`() =
        assertEquals("standing(ms(1,20),14:32)", cooldownStandingLabel(80, "14:32", fake))

    @Test
    fun `seconds under one minute`() = assertEquals("advisory(s(45),m(500.0))", advisory(45, 500.0))

    @Test
    fun `minutes and seconds under one hour`() = assertEquals("advisory(ms(2,5),m(500.0))", advisory(125, 500.0))

    @Test
    fun `hours and minutes`() = assertEquals("advisory(hm(1,2),m(500.0))", advisory(3725, 500.0))

    @Test
    fun `km at 1000m`() = assertEquals("advisory(s(30),km(1.0))", advisory(30, 1000.0))

    @Test
    fun `km with decimal`() = assertEquals("advisory(s(30),km(2.5))", advisory(30, 2500.0))

    @Test
    fun `meters under 1000`() = assertEquals("advisory(s(30),m(999.0))", advisory(30, 999.0))

    @Test
    fun `60s boundary shows minutes`() = assertEquals("advisory(ms(1,0),m(100.0))", advisory(60, 100.0))

    @Test
    fun `3600s boundary shows hours`() = assertEquals("advisory(hm(1,0),m(100.0))", advisory(3600, 100.0))

    @Test
    fun `badge cooling wins over distance`() = assertEquals("wait(advisory(s(45),m(500.0)))", cooldownBadgeText(45L to 500.0, 10.0, fake))

    @Test
    fun `badge distance away in meters`() = assertEquals("away(m(250.0))", cooldownBadgeText(null, 250.0, fake))

    @Test
    fun `badge distance away km at 1000`() = assertEquals("away(km(1.0))", cooldownBadgeText(null, 1000.0, fake))

    @Test
    fun `badge no wait when no position`() = assertEquals("nowait()", cooldownBadgeText(null, null, fake))
}
