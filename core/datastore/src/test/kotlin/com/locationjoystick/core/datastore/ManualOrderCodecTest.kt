package com.locationjoystick.core.datastore

import org.junit.Assert.assertEquals
import org.junit.Test

class ManualOrderCodecTest {
    @Test
    fun `serialize joins ids with the unit separator`() {
        assertEquals("b\u001Fa", serializeManualOrder(listOf("b", "a")))
    }

    @Test
    fun `deserialize splits ids and drops empty segments`() {
        assertEquals(listOf("b", "a"), deserializeManualOrder("b\u001Fa"))
        assertEquals(listOf("a", "b"), deserializeManualOrder("a\u001F\u001Fb"))
    }

    @Test
    fun `deserialize of a missing value is empty`() {
        assertEquals(emptyList<String>(), deserializeManualOrder(null))
    }

    @Test
    fun `round trip keeps the order`() {
        val ids = listOf("r3", "r1", "r2")
        assertEquals(ids, deserializeManualOrder(serializeManualOrder(ids)))
    }
}
