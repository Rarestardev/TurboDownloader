package com.rarestardev.turbodownloader.core

/**
 * How many files may transfer at once. This is not the per-file chunk thread count.
 * Earliest admitted work keeps its slot when the cap shrinks. A slot stays occupied
 * until that transfer has actually stopped, so a new file cannot start early.
 */
internal class ConcurrentTransferGate(
    initialCap: Int = ConcurrentTransferGate.DEFAULT
) {
    private val lock = Any()
    private val occupying = mutableListOf<String>()
    private val stopRequested = mutableSetOf<String>()
    private val held = mutableListOf<String>()

    var cap: Int = initialCap
        private set

    init {
        requireValid(initialCap)
    }

    fun activeIds(): List<String> = synchronized(lock) { occupying.filter { it !in stopRequested } }

    fun heldIds(): List<String> = synchronized(lock) { held.toList() }

    fun isStopRequested(id: String): Boolean = synchronized(lock) { id in stopRequested }

    fun isOccupying(id: String): Boolean = synchronized(lock) { id in occupying }

    fun setCap(newCap: Int): TransferAdjustment = synchronized(lock) {
        requireValid(newCap)
        cap = newCap
        val keep = occupying.take(cap).toSet()
        val surplus = occupying.filter { it !in keep }
        val continueRunning = keep.filter { it in stopRequested }
        stopRequested.removeAll(keep)
        stopRequested.addAll(surplus)
        return TransferAdjustment(
            stop = surplus,
            continueRunning = continueRunning,
            start = admitHeld()
        )
    }

    /**
     * Asks for a slot for a download that the user or the schedule has already released.
     * Queue-only items must not call this.
     */
    fun requestSlot(id: String): Boolean = synchronized(lock) {
        if (id in occupying) {
            if (id !in stopRequested) return true
            val keep = occupying.take(cap).toSet()
            if (id in keep) {
                stopRequested.remove(id)
                held.remove(id)
                return true
            }
            if (id !in held) held.add(id)
            return false
        }
        held.remove(id)
        if (occupying.size < cap) {
            occupying.add(id)
            stopRequested.remove(id)
            return true
        }
        held.add(id)
        false
    }

    fun onConcurrencyStopped(id: String): List<String> = synchronized(lock) {
        occupying.remove(id)
        stopRequested.remove(id)
        if (id !in held) held.add(id)
        admitHeld()
    }

    fun onSlotReleased(id: String): List<String> = synchronized(lock) {
        occupying.remove(id)
        stopRequested.remove(id)
        held.remove(id)
        admitHeld()
    }

    private fun admitHeld(): List<String> {
        val started = mutableListOf<String>()
        while (occupying.size < cap && held.isNotEmpty()) {
            val next = held.removeAt(0)
            occupying.add(next)
            stopRequested.remove(next)
            started.add(next)
        }
        return started
    }

    companion object {
        const val DEFAULT = 1
        const val MIN = 1
        const val MAX = 5

        fun requireValid(value: Int) {
            require(value in MIN..MAX) {
                "maxConcurrentDownloads must be from 1 to 5, was $value"
            }
        }
    }
}

internal data class TransferAdjustment(
    val stop: List<String>,
    val continueRunning: List<String>,
    val start: List<String>
)
