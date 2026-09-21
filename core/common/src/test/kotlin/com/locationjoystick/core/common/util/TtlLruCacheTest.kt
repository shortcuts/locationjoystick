package com.locationjoystick.core.common.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtlLruCacheTest {
    private var now = 0L

    @Test
    fun `evicts least recently used at capacity and hit refreshes order`() {
        val cache = TtlLruCache<String, Int>(2, nowMs = { now })
        cache.put("a", 1)
        cache.put("b", 2)
        cache.getFresh("a")
        cache.put("c", 3)
        assertEquals(1, cache.getFresh("a"))
        assertNull(cache.getFresh("b"))
        assertEquals(3, cache.getFresh("c"))
    }

    @Test
    fun `fresh expires after ttl but stale still returns`() {
        val cache = TtlLruCache<String, Int>(2, ttlMs = 100, nowMs = { now })
        cache.put("a", 1)
        now = 99
        assertEquals(1, cache.getFresh("a"))
        now = 100
        assertNull(cache.getFresh("a"))
        assertEquals(1, cache.getStale("a"))
    }
}
