package com.locationjoystick.core.common.geocoding

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class OfflineCityProviderTest {
    private fun provider(vararg rows: String): OfflineCityProvider {
        val bytes = rows.joinToString("\n").toByteArray()
        return OfflineCityProvider { ByteArrayInputStream(bytes) }
    }

    private val munich = "Munich\t48.137\t11.575\tDE\t1260391"
    private val munster = "Münster\t51.962\t7.626\tDE\t270000"
    private val mundelein = "Mundelein\t42.263\t-88.003\tUS\t31000"
    private val zurich = "Zürich\t47.372\t8.542\tCH\t341730"

    @Test
    fun `prefix match returns biggest place first`() =
        runTest {
            val result = provider(munich, munster, mundelein).search("mun")
            assertEquals(listOf("Munich", "Münster", "Mundelein"), result.map { it.displayName.substringBefore(",") })
            assertEquals(48.137, result[0].lat, 0.0)
            assertEquals(11.575, result[0].lon, 0.0)
        }

    @Test
    fun `diacritics are ignored in query and name`() =
        runTest {
            assertEquals(
                "Zürich",
                provider(zurich)
                    .search("zur")
                    .single()
                    .displayName
                    .substringBefore(","),
            )
            assertEquals(1, provider(zurich).search("Zürich").size)
        }

    @Test
    fun `display name carries the country`() =
        runTest {
            val name = provider(zurich).search("zur").single().displayName
            assertEquals("Zürich, ${java.util.Locale("", "CH").displayCountry}", name)
        }

    @Test
    fun `result count is capped at five`() =
        runTest {
            val rows = (1..8).map { "Town$it\t1.000\t1.000\tDE\t${100 - it}" }.toTypedArray()
            assertEquals(5, provider(*rows).search("town").size)
        }

    @Test
    fun `query shorter than two characters finds nothing`() =
        runTest {
            assertTrue(provider(munich).search("m").isEmpty())
            assertTrue(provider(munich).search(" m ").isEmpty())
        }

    @Test
    fun `malformed row is skipped`() =
        runTest {
            assertEquals(1, provider("broken", "Munich\tx\ty\tDE\t5", munich).search("mun").size)
        }

    @Test
    fun `no stream factory result returns empty`() =
        runTest {
            assertTrue(OfflineCityProvider { null }.search("paris").isEmpty())
        }

    @Test(expected = IOException::class)
    fun `unreadable asset throws`() =
        runTest {
            OfflineCityProvider { throw IOException("missing") }.search("paris")
        }

    @Test
    fun `reverse returns null`() =
        runTest {
            assertNull(provider(munich).reverse(48.1, 11.5))
        }
}
