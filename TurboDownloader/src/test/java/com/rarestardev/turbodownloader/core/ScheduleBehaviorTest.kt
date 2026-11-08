package com.rarestardev.turbodownloader.core

import com.rarestardev.turbodownloader.model.DownloadSchedule
import com.rarestardev.turbodownloader.model.ScheduleIssue
import com.rarestardev.turbodownloader.model.SchedulePhase
import com.rarestardev.turbodownloader.model.ScheduleSignal
import com.rarestardev.turbodownloader.model.ScheduleSignalRecord
import com.rarestardev.turbodownloader.model.schedulePhase
import com.rarestardev.turbodownloader.model.validateDownloadSchedule
import com.rarestardev.turbodownloader.storage.PreferenceScheduleStore
import com.rarestardev.turbodownloader.storage.SchedulePrefEditor
import com.rarestardev.turbodownloader.storage.SchedulePrefs
import com.rarestardev.turbodownloader.storage.ScheduleStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleBehaviorTest {

    @Test
    fun windowBoundaries_andValidation() {
        val start = 5_000L
        val end = 8_000L
        val schedule = DownloadSchedule(start, end)

        assertTrue(schedule.isActive(start))
        assertTrue(schedule.isActive(end - 1))
        assertFalse(schedule.isActive(end))
        assertEquals(SchedulePhase.WAITING, schedulePhase(schedule, start - 1))
        assertEquals(SchedulePhase.ACTIVE, schedulePhase(schedule, start))
        assertEquals(SchedulePhase.EXPIRED, schedulePhase(schedule, end))
        assertEquals(SchedulePhase.NONE, schedulePhase(null, start))

        assertEquals(ScheduleIssue.END_NOT_IN_FUTURE, validateDownloadSchedule(start, end, end))
        assertEquals(ScheduleIssue.END_NOT_AFTER_START, validateDownloadSchedule(start, start, start + 1))
        assertNull(validateDownloadSchedule(start, end, start))
    }

    @Test
    fun preAndroid12_usesExactAlarms_evenWhenPermissionFlagIsFalse() {
        assertEquals(AlarmDelivery.EXACT, SchedulePlanner.delivery(30, exactAlarmsAllowed = false))
        assertEquals(AlarmDelivery.EXACT, SchedulePlanner.delivery(31, exactAlarmsAllowed = true))
        assertEquals(AlarmDelivery.INEXACT, SchedulePlanner.delivery(34, exactAlarmsAllowed = false))
    }

    @Test
    fun futureWindow_schedulesStartAndEnd_andGatesDownloads() {
        val now = 1_000_000L
        val store = MemoryScheduleStore()
        val alarms = mutableListOf<PlannedAlarm>()
        val dispatched = mutableListOf<ScheduleSignal>()
        val engine = engine(store, alarms, dispatched, now, AlarmDelivery.EXACT)
        val schedule = DownloadSchedule(now + 60_000, now + 120_000)

        engine.schedule(schedule)

        assertEquals(schedule, store.get())
        assertEquals(
            listOf(
                schedule.startAtMillis to ScheduleSignal.START,
                schedule.endAtMillis to ScheduleSignal.END
            ),
            alarms.map { it.triggerAtMillis to it.signal }
        )
        assertTrue(alarms.all { it.delivery == AlarmDelivery.EXACT })
        assertTrue(dispatched.isEmpty())
        assertFalse(ScheduleGate.allowsDownload(store.get(), now))
        assertTrue(ScheduleGate.allowsDownload(store.get(), schedule.startAtMillis))
        assertFalse(ScheduleGate.allowsDownload(store.get(), schedule.endAtMillis))
        assertEquals(schedule, store.get())
    }

    @Test
    fun activeWindow_dispatchesStartImmediately_andKeepsEndAlarm() {
        val now = 1_000_000L
        val store = MemoryScheduleStore()
        val alarms = mutableListOf<PlannedAlarm>()
        val dispatched = mutableListOf<ScheduleSignal>()
        val schedule = DownloadSchedule(now - 10_000, now + 50_000)

        engine(store, alarms, dispatched, now, AlarmDelivery.EXACT).schedule(schedule)

        assertEquals(listOf(schedule.endAtMillis), alarms.map { it.triggerAtMillis })
        assertEquals(listOf(ScheduleSignal.START), dispatched)
        assertTrue(ScheduleGate.allowsDownload(store.get(), now))
    }

    @Test
    fun rejectedSchedule_doesNotReplaceSavedWindow() {
        val now = 1_000_000L
        val store = MemoryScheduleStore()
        val engine = engine(store, mutableListOf(), mutableListOf(), now, AlarmDelivery.EXACT)
        val saved = DownloadSchedule(now + 5_000, now + 15_000)
        engine.schedule(saved)

        assertThrows(IllegalArgumentException::class.java) {
            engine.schedule(DownloadSchedule(now - 5_000, now - 1))
        }
        assertEquals(saved, store.get())
    }

    @Test
    fun restoreInsideWindow_delaysStart_andDoesNotDispatchFromBoot() {
        val now = 1_000_000L
        val store = MemoryScheduleStore()
        val alarms = mutableListOf<PlannedAlarm>()
        val dispatched = mutableListOf<ScheduleSignal>()
        val schedule = DownloadSchedule(now - 1_000, now + 30_000)
        store.save(schedule)

        engine(store, alarms, dispatched, now, AlarmDelivery.INEXACT).restoreAfterBoot()

        assertEquals(
            listOf(
                now + SchedulePlanner.BOOT_GRACE_MILLIS to ScheduleSignal.START,
                schedule.endAtMillis to ScheduleSignal.END
            ),
            alarms.map { it.triggerAtMillis to it.signal }
        )
        assertTrue(alarms.all { it.delivery == AlarmDelivery.INEXACT })
        assertTrue(dispatched.isEmpty())
    }

    @Test
    fun expiredWindow_stillBlocksUntilCleared() {
        val now = 1_000_000L
        val schedule = DownloadSchedule(now - 20_000, now - 1_000)
        val store = MemoryScheduleStore()
        store.save(schedule)

        assertFalse(ScheduleGate.allowsDownload(store.get(), now))
        store.clear()
        assertTrue(ScheduleGate.allowsDownload(store.get(), now))
    }

    @Test
    fun persistence_roundTrip_corruptRange_andSignal() {
        val prefs = MemoryPrefs()
        val store = PreferenceScheduleStore(prefs)
        val schedule = DownloadSchedule(10, 20)

        store.save(schedule)
        assertEquals(schedule, store.get())
        store.recordSignal(ScheduleSignal.START, 15)
        assertEquals(ScheduleSignalRecord(ScheduleSignal.START, 15), store.lastSignal())

        store.clear()
        assertNull(store.get())
        assertNull(store.lastSignal())

        prefs.edit {
            putBoolean(PreferenceScheduleStore.KEY_ENABLED, true)
            putLong(PreferenceScheduleStore.KEY_START, 50)
            putLong(PreferenceScheduleStore.KEY_END, 40)
        }
        assertNull(store.get())
        assertFalse(prefs.getBoolean(PreferenceScheduleStore.KEY_ENABLED, true))
    }

    @Test
    fun inProcessWatcher_firesStartThenEnd_onVirtualClock() = runBlocking {
        var now = 1_000L
        val schedule = DownloadSchedule(5_000L, 8_000L)
        var stored: DownloadSchedule? = schedule
        val events = mutableListOf<String>()
        val watcher = ScheduleWindowWatcher(
            scope = this,
            clock = { now },
            readSchedule = { stored },
            onStart = { events += "start@$now" },
            onEnd = { events += "end@$now" },
            delayFn = { wait -> now += wait }
        )

        watcher.arm()
        yield()

        assertEquals(listOf("start@5000", "end@8000"), events)
        assertFalse(ScheduleGate.allowsDownload(stored, now))
    }

    private fun engine(
        store: ScheduleStore,
        alarms: MutableList<PlannedAlarm>,
        dispatched: MutableList<ScheduleSignal>,
        now: Long,
        delivery: AlarmDelivery
    ) = ScheduleEngine(
        storage = store,
        clock = { now },
        delivery = { delivery },
        cancelAlarm = {},
        scheduleAlarm = { alarms += it },
        dispatchNow = { dispatched += it }
    )
}

private class MemoryScheduleStore : ScheduleStore {
    private var schedule: DownloadSchedule? = null
    override fun save(schedule: DownloadSchedule) {
        this.schedule = schedule
    }
    override fun get(): DownloadSchedule? = schedule
    override fun clear() {
        schedule = null
    }
    override fun recordSignal(signal: ScheduleSignal, atMillis: Long) = Unit
    override fun lastSignal(): ScheduleSignalRecord? = null
}

private class MemoryPrefs : SchedulePrefs {
    private val longs = mutableMapOf<String, Long>()
    private val bools = mutableMapOf<String, Boolean>()
    private val strings = mutableMapOf<String, String?>()

    override fun getLong(key: String, defaultValue: Long) = longs[key] ?: defaultValue
    override fun getBoolean(key: String, defaultValue: Boolean) = bools[key] ?: defaultValue
    override fun getString(key: String): String? = strings[key]
    override fun edit(transform: SchedulePrefEditor.() -> Unit) {
        object : SchedulePrefEditor {
            override fun putLong(key: String, value: Long) {
                longs[key] = value
            }
            override fun putBoolean(key: String, value: Boolean) {
                bools[key] = value
            }
            override fun putString(key: String, value: String?) {
                strings[key] = value
            }
            override fun remove(key: String) {
                longs.remove(key)
                bools.remove(key)
                strings.remove(key)
            }
        }.transform()
    }
}
