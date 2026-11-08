package com.rarestardev.turbodownloader.storage

import android.content.Context
import androidx.core.content.edit
import com.rarestardev.turbodownloader.model.DownloadSchedule

internal class DownloadScheduleStorage(context: Context) {

    companion object {
        private const val KEY_START = "schedule_start"
        private const val KEY_END = "schedule_end"
        private const val KEY_ENABLED = "schedule_enabled"
    }

    private val prefs =
        context.getSharedPreferences("turbo_download_scheduler", Context.MODE_PRIVATE)

    fun save(schedule: DownloadSchedule) {
        prefs.edit {
            putLong(KEY_START, schedule.startAtMillis)
            putLong(KEY_END, schedule.endAtMillis)
            putBoolean(KEY_ENABLED, true)
            apply()
        }
    }

    fun isEnabled(): Boolean {
        return prefs.getBoolean(KEY_ENABLED, false)
    }

    fun get(): DownloadSchedule? {
        if (!isEnabled()) return null

        val start = prefs.getLong(KEY_START, -1L)
        val end = prefs.getLong(KEY_END, -1L)

        if (start !in 1..<end) return null
        return DownloadSchedule(start, end)
    }

    fun clear(){
        prefs.edit {
            remove(KEY_START)
            remove(KEY_END)
            putBoolean(KEY_ENABLED,false)
            apply()
        }
    }
}