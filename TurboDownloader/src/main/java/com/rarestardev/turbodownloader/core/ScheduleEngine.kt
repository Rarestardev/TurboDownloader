package com.rarestardev.turbodownloader.core

import com.rarestardev.turbodownloader.model.DownloadSchedule
import com.rarestardev.turbodownloader.model.ScheduleIssue
import com.rarestardev.turbodownloader.model.ScheduleSignal
import com.rarestardev.turbodownloader.model.validateDownloadSchedule
import com.rarestardev.turbodownloader.storage.ScheduleStore

internal class ScheduleEngine(
    private val storage: ScheduleStore,
    private val clock: () -> Long,
    private val delivery: () -> AlarmDelivery,
    private val cancelAlarm: (ScheduleSignal) -> Unit,
    private val scheduleAlarm: (PlannedAlarm) -> Unit,
    private val dispatchNow: (ScheduleSignal) -> Unit
) {
    fun schedule(schedule: DownloadSchedule) {
        val now = clock()
        val issue = validateDownloadSchedule(
            schedule.startAtMillis,
            schedule.endAtMillis,
            now
        )
        require(issue == null) {
            when (issue) {
                ScheduleIssue.START_NOT_POSITIVE ->
                    "startAtMillis must be greater than zero"
                ScheduleIssue.END_NOT_AFTER_START ->
                    "endAtMillis must be after startAtMillis"
                ScheduleIssue.END_NOT_IN_FUTURE ->
                    "Schedule end time must be in the future"
                null -> "Invalid schedule"
            }
        }

        val plan = SchedulePlanner.planSet(schedule, now, delivery())
        storage.save(schedule)
        cancelAlarm(ScheduleSignal.START)
        cancelAlarm(ScheduleSignal.END)
        plan.alarms.forEach(scheduleAlarm)
        plan.dispatchImmediately?.let(dispatchNow)
    }

    fun cancel() {
        cancelAlarm(ScheduleSignal.START)
        cancelAlarm(ScheduleSignal.END)
        storage.clear()
    }

    fun restoreAfterBoot() {
        val schedule = storage.get() ?: return
        val plan = SchedulePlanner.planRestore(schedule, clock(), delivery())
        cancelAlarm(ScheduleSignal.START)
        cancelAlarm(ScheduleSignal.END)
        plan.alarms.forEach(scheduleAlarm)
        plan.dispatchImmediately?.let(dispatchNow)
    }

    fun current(): DownloadSchedule? = storage.get()
}
