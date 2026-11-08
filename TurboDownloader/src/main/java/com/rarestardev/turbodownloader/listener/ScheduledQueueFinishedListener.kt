package com.rarestardev.turbodownloader.listener

/**
 * Called once after the downloads released by a schedule start have all completed
 * and nothing else is still transferring.
 * The library has already asked its own download service to stop.
 * The host decides how to close its screens. This does not kill the process.
 */
fun interface ScheduledQueueFinishedListener {
    fun onScheduledQueueFinished()
}
