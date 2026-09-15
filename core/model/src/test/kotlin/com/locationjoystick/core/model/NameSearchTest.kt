package com.locationjoystick.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NameSearchTest {
    @Test
    fun `blank query matches any name`() {
        assertTrue(matchesNameSearch("Tokyo", ""))
        assertTrue(matchesNameSearch("Tokyo", "   "))
    }

    @Test
    fun `name match is case-insensitive substring`() {
        assertTrue(matchesNameSearch("Tokyo", "kyo"))
        assertTrue(matchesNameSearch("Tokyo", "TOKYO"))
        assertFalse(matchesNameSearch("Tokyo", "Paris"))
    }

    @Test
    fun `extra field is matched when name is not`() {
        assertTrue(matchesNameSearch("Tokyo", "Japan", extra = "Japan"))
        assertFalse(matchesNameSearch("Tokyo", "France", extra = "Japan"))
        assertFalse(matchesNameSearch("Tokyo", "Japan", extra = null))
    }

    @Test
    fun `favorite matches name or category`() {
        val favorite =
            FavoriteLocation(
                id = "1",
                name = "Tokyo",
                position = LatLng(35.6762, 139.6503),
                category = "Japan",
            )
        assertTrue(favorite.matchesSearch("tok"))
        assertTrue(favorite.matchesSearch("japan"))
        assertFalse(favorite.matchesSearch("paris"))
    }

    @Test
    fun `route matches name only`() {
        val route = Route(id = "1", name = "Morning loop")
        assertTrue(route.matchesSearch("morning"))
        assertFalse(route.matchesSearch("evening"))
    }
}
