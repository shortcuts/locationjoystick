package com.locationjoystick.core.common.util

/**
 * Small thread-safe LRU cache with an optional freshness TTL. [getStale] ignores age so callers
 * can fall back to an expired entry when the network call fails.
 */
class TtlLruCache<K : Any, V : Any>(
    private val maxEntries: Int,
    private val ttlMs: Long = Long.MAX_VALUE,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private class Entry<V>(
        val value: V,
        val storedAtMs: Long,
    )

    private val map =
        object : LinkedHashMap<K, Entry<V>>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>): Boolean = size > maxEntries
        }

    /** Returns the value if present and younger than the TTL; a hit refreshes its LRU position. */
    fun getFresh(key: K): V? =
        synchronized(map) {
            map[key]?.takeIf { nowMs() - it.storedAtMs < ttlMs }?.value
        }

    /** Returns the value if present, however old. */
    fun getStale(key: K): V? = synchronized(map) { map[key]?.value }

    fun put(
        key: K,
        value: V,
    ) {
        synchronized(map) { map[key] = Entry(value, nowMs()) }
    }
}
