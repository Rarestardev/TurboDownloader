package com.rarestardev.turbodownloader.core

import com.rarestardev.turbodownloader.model.DownloadSchedule

internal object ScheduleGate {
    /**
     * Downloads are allowed only inside a saved window.
     * An expired window keeps blocking until the schedule is cleared.
     */
    fun allowsDownload(schedule: DownloadSchedule?, now: Long): Boolean {
        return schedule?.isActive(now) ?: true
    }
}
