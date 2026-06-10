package dev.mias.core.common.cache

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TtlCacheTest {

    /** Manually-advanced clock so expiry is tested deterministically. */
    private var now = 0L
    private fun cache(ttl: Long = 1_000, max: Int = 3) =
        TtlCache<String, String>(ttlMillis = ttl, maxEntries = max, clock = { now })

    @Test
    fun `stores and returns values within ttl`() {
        val c = cache()
        c.put("k", "v")
        now += 999
        assertThat(c.get("k")).isEqualTo("v")
    }

    @Test
    fun `expires entries after ttl`() {
        val c = cache(ttl = 1_000)
        c.put("k", "v")
        now += 1_000
        assertThat(c.get("k")).isNull()
    }

    @Test
    fun `expired entries are dropped on read`() {
        val c = cache(ttl = 100)
        c.put("k", "v")
        now += 200
        c.get("k")
        assertThat(c.size).isEqualTo(0)
    }

    @Test
    fun `evicts least recently used beyond the cap`() {
        val c = cache(max = 3)
        c.put("a", "1")
        c.put("b", "2")
        c.put("c", "3")
        c.get("a") // touch "a" so "b" is now the LRU entry
        c.put("d", "4")
        assertThat(c.get("a")).isEqualTo("1")
        assertThat(c.get("b")).isNull()
        assertThat(c.get("c")).isEqualTo("3")
        assertThat(c.get("d")).isEqualTo("4")
    }

    @Test
    fun `put refreshes ttl for an existing key`() {
        val c = cache(ttl = 1_000)
        c.put("k", "v1")
        now += 900
        c.put("k", "v2")
        now += 900 // 1800 total — beyond the first write's ttl, within the second's
        assertThat(c.get("k")).isEqualTo("v2")
    }

    @Test
    fun `expired entries are evicted before live ones when over cap`() {
        val c = cache(ttl = 100, max = 2)
        c.put("dead", "x")
        now += 200 // "dead" expires
        c.put("a", "1")
        c.put("b", "2") // over cap; the expired entry should be the casualty
        assertThat(c.get("a")).isEqualTo("1")
        assertThat(c.get("b")).isEqualTo("2")
    }

    @Test
    fun `remove and clear work`() {
        val c = cache()
        c.put("a", "1")
        c.put("b", "2")
        c.remove("a")
        assertThat(c.get("a")).isNull()
        c.clear()
        assertThat(c.size).isEqualTo(0)
    }

    @Test
    fun `rejects nonsensical configuration`() {
        try {
            TtlCache<String, String>(ttlMillis = 0, maxEntries = 1)
            assert(false) { "expected IllegalArgumentException" }
        } catch (_: IllegalArgumentException) {
        }
        try {
            TtlCache<String, String>(ttlMillis = 1, maxEntries = 0)
            assert(false) { "expected IllegalArgumentException" }
        } catch (_: IllegalArgumentException) {
        }
    }
}
