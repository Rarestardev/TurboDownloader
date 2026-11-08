package com.rarestardev.turbodownloader.core

import com.rarestardev.turbodownloader.model.DownloadSchedule

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
