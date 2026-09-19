package com.rarestardev.turbodownloader.core

/**
 * Pure (JVM testable) gate that limits how many downloads run at the same time.
 *
 * - [tryAcquire] either grants a slot immediately or puts the caller into a
 *   FIFO waiting queue. In BOTH cases [onSlotReady] is invoked: immediately
 *   when the slot is granted, later (when another download releases its slot)
 *   when it was queued.
 * - [release] frees a slot and starts the next waiting download.
 * - [cancelWaiting] removes a still-queued download from the wait list.
 * - [setMaxConcurrent] changes the limit on the fly; raising it starts as
 *   many waiting downloads as the new limit allows.
 *
 * Releasing a key that is not held, or cancelling a key that is not waiting,
 * is a safe no-op.
 */
class ConcurrentDownloadGate(defaultMaxConcurrent: Int = DEFAULT_MAX_CONCURRENT_DOWNLOADS) {

    companion object {
        const val DEFAULT_MAX_CONCURRENT_DOWNLOADS = 3
    }

    private val lock = Any()
    private var max = defaultMaxConcurrent
    private var active = 0
    private val held = HashSet<String>()
    private val waiting = ArrayDeque<Pair<String, () -> Unit>>()

    fun setMaxConcurrent(value: Int) {
        require(value >= 1) { "Max concurrent downloads must be at least 1" }
        synchronized(lock) {
            max = value
            drainLocked()
        }
    }

    fun maxConcurrent(): Int = synchronized(lock) { max }

    fun activeCount(): Int = synchronized(lock) { active }

    fun waitingCount(): Int = synchronized(lock) { waiting.size }

    /**
     * Requests a download slot. Returns true when the slot was granted
     * immediately, false when the download was queued. [onSlotReady] is
     * invoked exactly once either way.
     */
    fun tryAcquire(key: String, onSlotReady: () -> Unit): Boolean {
        synchronized(lock) {
            if (active < max) {
                active++
                held.add(key)
                onSlotReady()
                return true
            }
            waiting.addLast(key to onSlotReady)
            return false
        }
    }

    /** Frees the slot held by [key] (if any) and starts the next queued download. */
    fun release(key: String) {
        synchronized(lock) {
            if (held.remove(key) && active > 0) {
                active--
            }
            drainLocked()
        }
    }

    /** Removes [key] from the waiting queue. Returns true when it was queued. */
    fun cancelWaiting(key: String): Boolean {
        synchronized(lock) {
            val removed = waiting.removeAll { it.first == key }
            if (removed) drainLocked()
            return removed
        }
    }

    private fun drainLocked() {
        while (active < max && waiting.isNotEmpty()) {
            val (key, ready) = waiting.removeFirst()
            active++
            held.add(key)
            ready()
        }
    }
}
