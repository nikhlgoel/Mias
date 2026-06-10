package dev.mias.core.common.cache

/**
 * Small, thread-safe in-memory cache with a per-entry time-to-live and a hard
 * entry cap (LRU eviction).
 *
 * Built for on-device use where the goals are: avoid repeating expensive work
 * (network round-trips, parsing) within a short window, never grow unbounded,
 * and stay dependency-free. Not a general-purpose cache — values should be
 * modest in size and callers must tolerate misses at any time.
 *
 * Thread safety: all operations synchronize on an internal lock. Do NOT hold
 * suspending work inside cache operations (there is deliberately no
 * `getOrPut(loader)` — a suspending loader would either hold the lock across
 * suspension or stampede; callers do `get` → compute → `put`).
 *
 * @param ttlMillis  how long an entry stays valid after being written.
 * @param maxEntries hard cap; writing beyond it evicts the least-recently-used.
 * @param clock      injectable time source (epoch millis) for tests.
 */
class TtlCache<K : Any, V : Any>(
    private val ttlMillis: Long,
    private val maxEntries: Int,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    init {
        require(ttlMillis > 0) { "ttlMillis must be positive" }
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    private class Entry<V>(val value: V, val expiresAt: Long)

    // Access-ordered so iteration order == LRU order; eldest entry is first.
    private val map = LinkedHashMap<K, Entry<V>>(16, 0.75f, true)
    private val lock = Any()

    /** The value for [key], or null if absent or expired (expired entries are dropped). */
    fun get(key: K): V? = synchronized(lock) {
        val entry = map[key] ?: return null
        if (clock() >= entry.expiresAt) {
            map.remove(key)
            return null
        }
        entry.value
    }

    /** Store [value] under [key], refreshing its TTL. Evicts LRU entries over the cap. */
    fun put(key: K, value: V): Unit = synchronized(lock) {
        map[key] = Entry(value, clock() + ttlMillis)
        // Evict expired entries first so live data isn't pushed out by corpses.
        val now = clock()
        val it = map.entries.iterator()
        while (it.hasNext()) {
            if (now >= it.next().value.expiresAt) it.remove()
        }
        // Then enforce the size cap, oldest-accessed first.
        while (map.size > maxEntries) {
            val eldest = map.entries.iterator()
            eldest.next()
            eldest.remove()
        }
    }

    fun remove(key: K): Unit = synchronized(lock) {
        map.remove(key)
    }

    fun clear(): Unit = synchronized(lock) {
        map.clear()
    }

    /** Current entry count, including not-yet-collected expired entries. */
    val size: Int get() = synchronized(lock) { map.size }
}
