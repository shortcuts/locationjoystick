package com.locationjoystick.feature.widget.impl

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * The debug readout strings carry format specifiers, so they are only correct if the resource
 * survives [String.format] with its arguments substituted. Escaping them as `%%` made
 * `getString` return literal `%.2f` text and the readout showed the template instead of the
 * values, in every locale.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DebugStatsFormatTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var previousLocale: Locale

    @Before
    fun pinLocale() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(previousLocale)
    }

    @Test
    fun `coordinate line substitutes latitude and longitude`() {
        val formatted =
            context
                .getString(R.string.widget_panel_content_2f_6f)
                .format(48.8566, 2.3522)

        assertEquals("48.86, 2.352200", formatted)
    }

    @Test
    fun `speed and altitude line substitutes both values`() {
        val formatted =
            context
                .getString(R.string.widget_panel_content_speed_2f_m_s_alt_ellipsoidal_2f_m)
                .format(1.5f, 120.0)

        assertEquals("speed 1.50 m/s · alt (ellipsoidal) 120.00 m", formatted)
    }

    @Test
    fun `accuracy line substitutes accuracy, bearing and tick rate`() {
        val formatted =
            context
                .getString(R.string.widget_panel_content_acc_1f_m_bearing_s_1f_hz)
                .format(5f, "90°", 1.0f)

        assertEquals("acc 5.0 m · bearing 90° · 1.0 Hz", formatted)
    }

    @Test
    fun `no debug stats string leaks an unsubstituted specifier`() {
        val formatted =
            listOf(
                context.getString(R.string.widget_panel_content_2f_6f).format(0.0, 0.0),
                context
                    .getString(R.string.widget_panel_content_speed_2f_m_s_alt_ellipsoidal_2f_m)
                    .format(0f, 0.0),
                context
                    .getString(R.string.widget_panel_content_acc_1f_m_bearing_s_1f_hz)
                    .format(0f, "—", 0f),
            )

        formatted.forEach { assertFalse("leaked a specifier: $it", it.contains('%')) }
    }
}
