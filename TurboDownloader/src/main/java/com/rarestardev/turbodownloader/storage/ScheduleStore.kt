package com.rarestardev.turbodownloader.storage

import com.rarestardev.turbodownloader.core.ConcurrentTransferGate
import com.rarestardev.turbodownloader.model.DownloadSchedule
import com.rarestardev.turbodownloader.model.ScheduleSignal
import com.rarestardev.turbodownloader.model.ScheduleSignalRecord

internal interface ScheduleStore {
    fun save(schedule: DownloadSchedule)
    fun get(): DownloadSchedule?
    fun peek(): DownloadSchedule?
    fun isSchedulingEnabled(): Boolean
    fun setSchedulingEnabled(enabled: Boolean)
    fun isFinishAfterScheduledQueue(): Boolean
    fun setFinishAfterScheduledQueue(enabled: Boolean)
    fun maxConcurrentDownloads(): Int
    fun setMaxConcurrentDownloads(value: Int)
    fun clear()
    fun recordSignal(signal: ScheduleSignal, atMillis: Long)
    fun lastSignal(): ScheduleSignalRecord?
}

internal interface SchedulePrefs {
    fun getLong(key: String, defaultValue: Long): Long
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun getString(key: String): String?
    fun edit(transform: SchedulePrefEditor.() -> Unit)
}

internal interface SchedulePrefEditor {
    fun putLong(key: String, value: Long)
    fun putBoolean(key: String, value: Boolean)
    fun putString(key: String, value: String?)
    fun remove(key: String)
}

internal class PreferenceScheduleStore(
    private val prefs: SchedulePrefs
) : ScheduleStore {

    override fun save(schedule: DownloadSchedule) {
        prefs.edit {
            putLong(KEY_START, schedule.startAtMillis)
            putLong(KEY_END, schedule.endAtMillis)
            putBoolean(KEY_ENABLED, true)
        }
    }

    override fun get(): DownloadSchedule? {
        if (!isSchedulingEnabled()) return null
        peek()?.let { return it }
        val start = prefs.getLong(KEY_START, -1L)
        val end = prefs.getLong(KEY_END, -1L)
        if (start != -1L || end != -1L) clear()
        return null
    }

    override fun peek(): DownloadSchedule? {
        val start = prefs.getLong(KEY_START, -1L)
        val end = prefs.getLong(KEY_END, -1L)
        if (start !in 1L..<end) return null
        return DownloadSchedule(startAtMillis = start, endAtMillis = end)
    }

    override fun isSchedulingEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    override fun setSchedulingEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, enabled) }
    }

    override fun isFinishAfterScheduledQueue(): Boolean =
        prefs.getBoolean(KEY_FINISH_AFTER_QUEUE, false)

    override fun setFinishAfterScheduledQueue(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_FINISH_AFTER_QUEUE, enabled) }
    }

    override fun maxConcurrentDownloads(): Int {
        val stored = prefs.getLong(KEY_MAX_CONCURRENT, ConcurrentTransferGate.DEFAULT.toLong()).toInt()
        return stored.takeIf { it in ConcurrentTransferGate.MIN..ConcurrentTransferGate.MAX }
            ?: ConcurrentTransferGate.DEFAULT
    }

    override fun setMaxConcurrentDownloads(value: Int) {
        ConcurrentTransferGate.requireValid(value)
        prefs.edit { putLong(KEY_MAX_CONCURRENT, value.toLong()) }
    }

    override fun clear() {
        prefs.edit {
            remove(KEY_START)
            remove(KEY_END)
            remove(KEY_LAST_SIGNAL)
            remove(KEY_LAST_SIGNAL_AT)
            putBoolean(KEY_ENABLED, false)
        }
    }

    override fun recordSignal(signal: ScheduleSignal, atMillis: Long) {
        prefs.edit {
            putString(KEY_LAST_SIGNAL, signal.name)
            putLong(KEY_LAST_SIGNAL_AT, atMillis)
        }
    }

    override fun lastSignal(): ScheduleSignalRecord? {
        val name = prefs.getString(KEY_LAST_SIGNAL) ?: return null
        val signal = runCatching { ScheduleSignal.valueOf(name) }.getOrNull() ?: return null
        val at = prefs.getLong(KEY_LAST_SIGNAL_AT, -1L)
        if (at <= 0L) return null
        return ScheduleSignalRecord(signal, at)
    }

    companion object {
        const val KEY_START = "schedule_start"
        const val KEY_END = "schedule_end"
        const val KEY_ENABLED = "schedule_enabled"
        const val KEY_FINISH_AFTER_QUEUE = "finish_after_scheduled_queue"
        const val KEY_MAX_CONCURRENT = "max_concurrent_downloads"
        const val KEY_LAST_SIGNAL = "schedule_last_signal"
        const val KEY_LAST_SIGNAL_AT = "schedule_last_signal_at"
    }
}
