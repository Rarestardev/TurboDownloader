package com.rarestardev.turbodownloader.core

import com.rarestardev.turbodownloader.model.DownloadSchedule
import com.rarestardev.turbodownloader.state.DownloadStatus
import com.rarestardev.turbodownloader.state.PauseReason

internal object ScheduleGate {
    /**
     * Downloads are allowed only inside a saved window.
     * An expired window keeps blocking until the schedule is cleared.
     */
    fun allowsDownload(
        schedulingEnabled: Boolean,
        schedule: DownloadSchedule?,
        now: Long
    ): Boolean {
        if (!schedulingEnabled) return true
        return schedule?.isActive(now) ?: true
    }
}

/**
 * Held queue entries start only when [releaseHeldQueue] is true, which is the schedule start
 * edge. Reconcile, an already-open window, and turning scheduling off do not pass that flag.
 * A download the user paused stays paused.
 */
internal fun shouldStartWhenWindowOpens(
    status: DownloadStatus,
    pauseReason: PauseReason,
    manuallyPaused: Boolean,
    releaseHeldQueue: Boolean
): Boolean {
    if (manuallyPaused || pauseReason == PauseReason.MANUAL) return false
    return when (status) {
        DownloadStatus.QUEUED -> releaseHeldQueue
        DownloadStatus.RUNNING -> true
        DownloadStatus.PAUSED -> pauseReason == PauseReason.SCHEDULE
        else -> false
    }
}

/**
 * True only when this schedule-start cohort is non-empty and every one of its downloads
 * has completed, nothing is still transferring, and the host asked for this behavior.
 * A new queue entry that is not in the cohort does not block or trigger it.
 */
internal fun scheduledQueueRunFinished(
    finishEnabled: Boolean,
    cohortIds: Set<String>,
    statusById: Map<String, DownloadStatus>,
    runningIds: Set<String>
): Boolean {
    if (!finishEnabled || cohortIds.isEmpty() || runningIds.isNotEmpty()) return false
    return cohortIds.all { statusById[it] == DownloadStatus.COMPLETED }
}
