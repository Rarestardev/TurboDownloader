package com.rarestardev.turbodownloader.schedule

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * One scheduled (delayed) download request.
 */
data class ScheduledDownload(
    val id: String,
    val url: String,
    val fileName: String?,
    /** Absolute wall-clock time (epoch millis) at which the download fires. */
    val triggerAtMillis: Long
)

/**
 * Schedules downloads to start at a specific time or after a delay.
 *
 * The scheduler is in-memory: schedules live as long as the app process
 * lives (they do not survive process death). When a schedule fires,
 * [onFire] is invoked with the stored [ScheduledDownload].
 *
 * [now] is injectable so the scheduler can be unit tested with a virtual clock.
 */
class DownloadScheduler(
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val onFire: suspend (ScheduledDownload) -> Unit
) {
    private val items = ConcurrentHashMap<String, ScheduledDownload>()
    private val jobs = ConcurrentHashMap<String, Job>()

    /**
     * Schedules a download for the absolute time [triggerAtMillis]
     * (epoch milliseconds). Times already in the past fire immediately.
     */
    fun schedule(url: String, fileName: String?, triggerAtMillis: Long): ScheduledDownload {
        require(url.isNotBlank()) { "Scheduled download url must not be blank" }

        val item = ScheduledDownload(
            id = UUID.randomUUID().toString(),
            url = url,
            fileName = fileName,
            triggerAtMillis = triggerAtMillis
        )
        val delayMs = (triggerAtMillis - now()).coerceAtLeast(0)

        val job = scope.launch {
            delay(delayMs)
            items.remove(item.id)
            jobs.remove(item.id)
            onFire(item)
        }
        items[item.id] = item
        jobs[item.id] = job
        return item
    }

    /** Schedules a download to start after [delayMillis] from now. */
    fun scheduleAfter(url: String, fileName: String?, delayMillis: Long): ScheduledDownload =
        schedule(url, fileName, now() + delayMillis.coerceAtLeast(0))

    /** Cancels a pending schedule. Returns false when the id is unknown/already fired. */
    fun cancel(scheduleId: String): Boolean {
        val job = jobs.remove(scheduleId) ?: return false
        items.remove(scheduleId)
        job.cancel()
        return true
    }

    /** All currently pending schedules, ordered by trigger time. */
    fun scheduled(): List<ScheduledDownload> =
        items.values.sortedBy { it.triggerAtMillis }

    fun pendingCount(): Int = items.size
}
