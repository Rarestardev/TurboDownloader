package com.rarestardev.turbodownloader.limit

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Token-bucket based speed limiter.
 *
 * All download workers (HTTP chunks and torrent peers) share one instance,
 * so [limit] is the TOTAL allowed speed across the whole library.
 *
 * A limit of 0 (or [TurboLimitLevel.OFF]) disables throttling completely.
 *
 * [clock] and [sleeper] are injectable so the limiter can be unit tested
 * with a virtual clock.
 */
class SpeedLimiter(
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleeper: suspend (Long) -> Unit = { delay(it) }
) {
    private val mutex = Mutex()

    @Volatile
    private var capBytesPerSec = 0L

    private var tokens = 0.0
    private var lastRefill = clock()

    /** Current cap in bytes/sec, 0 = unlimited. */
    @Volatile
    var limit: Long = 0L
        private set

    fun setLimit(bytesPerSecond: Long) {
        capBytesPerSec = bytesPerSecond.coerceAtLeast(0L)
        limit = capBytesPerSec
    }

    fun setLevel(level: TurboLimitLevel) = setLimit(level.bytesPerSecond)

    /**
     * Suspends until [bytes] can pass through the bucket without
     * exceeding the configured cap. No-op when the limiter is OFF.
     */
    suspend fun acquire(bytes: Int) {
        if (bytes <= 0) return
        while (true) {
            val cap = capBytesPerSec
            if (cap <= 0) return

            val waitMs = mutex.withLock {
                val now = clock()
                val elapsed = (now - lastRefill).coerceAtLeast(0)
                tokens += elapsed * cap / 1000.0
                // never allow more than one second worth of burst
                if (tokens > cap) tokens = cap.toDouble()
                lastRefill = now

                if (tokens >= bytes) {
                    tokens -= bytes
                    return
                }
                val missing = (bytes - tokens) / cap * 1000.0
                missing.toLong().coerceIn(1, 500)
            }
            sleeper(waitMs)
        }
    }
}
