package com.rarestardev.turbodownloader.storage

import android.content.Context
import androidx.core.content.edit
import com.rarestardev.turbodownloader.model.DownloadSchedule

internal class DownloadScheduleStorage(context: Context) {

    companion object {
        private const val PREF_NAME = "turbo_download_scheduler"

        private const val KEY_START = "schedule_start"
        private const val KEY_END = "schedule_end"
        private const val KEY_ENABLED = "schedule_enabled"
    }

    private val prefs = context.applicationContext.getSharedPreferences(
        PREF_NAME,
        Context.MODE_PRIVATE
    )

    fun save(schedule: DownloadSchedule) {
        prefs.edit {
            putLong(KEY_START, schedule.startAtMillis)
                .putLong(KEY_END, schedule.endAtMillis)
                .putBoolean(KEY_ENABLED, true)
        }
    }

    fun get(): DownloadSchedule? {
        if (!isEnabled()) return null

        val start = prefs.getLong(KEY_START, -1L)
        val end = prefs.getLong(KEY_END, -1L)

        if (start !in 1..<end) return null

        return DownloadSchedule(
            startAtMillis = start,
            endAtMillis = end
        )
    }

    fun isEnabled(): Boolean =
        prefs.getBoolean(KEY_ENABLED, false)

    fun clear() {
        prefs.edit {
            remove(KEY_START)
                .remove(KEY_END)
                .putBoolean(KEY_ENABLED, false)
        }
    }
}