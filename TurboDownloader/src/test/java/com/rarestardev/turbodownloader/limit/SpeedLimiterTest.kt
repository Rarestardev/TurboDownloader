package com.rarestardev.turbodownloader.limit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurboLimitLevelTest {

    @Test
    fun `OFF has zero cap and is not limited`() {
        assertEquals(0L, TurboLimitLevel.OFF.bytesPerSecond)
        assertFalse(TurboLimitLevel.OFF.isLimited)
    }

    @Test
    fun `all other levels are limited`() {
        TurboLimitLevel.entries.filter { it != TurboLimitLevel.OFF }.forEach { level ->
            assertTrue("$level should be limited", level.isLimited)
            assertTrue("$level cap must be positive", level.bytesPerSecond > 0)
        }
    }

    @Test
    fun `higher limitation level means lower speed cap`() {
        assertTrue(TurboLimitLevel.HIGH.bytesPerSecond < TurboLimitLevel.MEDIUM.bytesPerSecond)
        assertTrue(TurboLimitLevel.MEDIUM.bytesPerSecond < TurboLimitLevel.LOW.bytesPerSecond)
    }

    @Test
    fun `valueOf parses enum names`() {
        assertEquals(TurboLimitLevel.OFF, TurboLimitLevel.valueOf("OFF"))
        assertEquals(TurboLimitLevel.LOW, TurboLimitLevel.valueOf("LOW"))
        assertEquals(TurboLimitLevel.MEDIUM, TurboLimitLevel.valueOf("MEDIUM"))
        assertEquals(TurboLimitLevel.HIGH, TurboLimitLevel.valueOf("HIGH"))
    }
}

class SpeedLimiterTest {

    @Test
    fun `acquire is a no-op when limit is OFF`() = runBlocking {
        var now = 0L
        val limiter = SpeedLimiter(clock = { now }, sleeper = { now += it })
        limiter.setLimit(TurboLimitLevel.OFF.bytesPerSecond)

        limiter.acquire(Int.MAX_VALUE)

        assertEquals(0L, now)
        assertEquals(0L, limiter.limit)
    }

    @Test
    fun `acquire enforces the bytes-per-second cap`() = runBlocking {
        var now = 0L
        val limiter = SpeedLimiter(clock = { now }, sleeper = { now += it })
        val cap = 1_000L // 1 KB/s
        limiter.setLimit(cap)

        limiter.acquire(1_000)
        val afterFirst = now
        limiter.acquire(1_000)

        // each 1000 bytes at 1000 B/s needs ~1 second
        assertTrue("first acquire took $afterFirst ms", afterFirst >= 1_000)
        assertTrue("total took $now ms", now >= 2_000)
    }

    @Test
    fun `tokens accumulate while idle`() = runBlocking {
        var now = 0L
        val limiter = SpeedLimiter(clock = { now }, sleeper = { now += it })
        limiter.setLimit(1_000)

        limiter.acquire(1_000) // consumes the budget, clock ~1000
        now += 500             // idle half a second -> 500 tokens available
        limiter.acquire(500)   // must be free now

        assertEquals(1_500L, now)
    }

    @Test
    fun `raising limit allows bigger chunks through`() = runBlocking {
        var now = 0L
        val limiter = SpeedLimiter(clock = { now }, sleeper = { now += it })
        limiter.setLimit(1_000)
        limiter.acquire(1_000)

        limiter.setLimit(4_000)
        val start = now
        limiter.acquire(1_000)

        assertTrue(now - start <= 500)
    }

    @Test
    fun `setLevel maps the three levels to their caps`() {
        val limiter = SpeedLimiter()
        limiter.setLevel(TurboLimitLevel.HIGH)
        assertEquals(TurboLimitLevel.HIGH.bytesPerSecond, limiter.limit)
        limiter.setLevel(TurboLimitLevel.MEDIUM)
        assertEquals(TurboLimitLevel.MEDIUM.bytesPerSecond, limiter.limit)
        limiter.setLevel(TurboLimitLevel.LOW)
        assertEquals(TurboLimitLevel.LOW.bytesPerSecond, limiter.limit)
    }
}
