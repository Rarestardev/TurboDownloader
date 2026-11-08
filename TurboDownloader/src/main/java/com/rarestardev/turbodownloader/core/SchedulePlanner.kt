package com.rarestardev.turbodownloader.core

import com.rarestardev.turbodownloader.model.DownloadSchedule
import com.rarestardev.turbodownloader.model.ScheduleSignal

/**
 * First SDK in which exact alarms require [android.Manifest.permission.SCHEDULE_EXACT_ALARM]
 * and may be revoked by the user.
 */
internal const val EXACT_ALARM_PERMISSION_SDK = 31

internal enum class AlarmDelivery {
    EXACT,
    INEXACT
}

internal data class PlannedAlarm(
    val triggerAtMillis: Long,
    val signal: ScheduleSignal,
    val delivery: AlarmDelivery
)

internal data class ScheduleAlarmPlan(
    val alarms: List<PlannedAlarm>,
    val dispatchImmediately: ScheduleSignal?
)

internal data class InProcessDelays(
    val untilStartMillis: Long,
    val untilEndMillis: Long
)

internal object SchedulePlanner {
    const val BOOT_GRACE_MILLIS = 1_000L

    fun delivery(sdkInt: Int, exactAlarmsAllowed: Boolean): AlarmDelivery {
        val exactAvailable = sdkInt < EXACT_ALARM_PERMISSION_SDK || exactAlarmsAllowed
        return if (exactAvailable) AlarmDelivery.EXACT else AlarmDelivery.INEXACT
    }

    fun planSet(
        schedule: DownloadSchedule,
        now: Long,
        delivery: AlarmDelivery
    ): ScheduleAlarmPlan {
        require(schedule.endAtMillis > now) {
            "Schedule end time must be in the future"
        }
        return if (now < schedule.startAtMillis) {
            ScheduleAlarmPlan(
                alarms = listOf(
                    PlannedAlarm(schedule.startAtMillis, ScheduleSignal.START, delivery),
                    PlannedAlarm(schedule.endAtMillis, ScheduleSignal.END, delivery)
                ),
                dispatchImmediately = null
            )
        } else {
            ScheduleAlarmPlan(
                alarms = listOf(
                    PlannedAlarm(schedule.endAtMillis, ScheduleSignal.END, delivery)
                ),
                dispatchImmediately = ScheduleSignal.START
            )
        }
    }

    /**
     * Re-arms persisted alarms after boot or force-stop.
     * The start signal is delayed by [BOOT_GRACE_MILLIS] while a window is already open so the
     * process is not started directly from the boot broadcast. Opening the app reconciles immediately.
     */
    fun planRestore(
        schedule: DownloadSchedule,
        now: Long,
        delivery: AlarmDelivery
    ): ScheduleAlarmPlan {
        return when {
            now >= schedule.endAtMillis -> ScheduleAlarmPlan(
                alarms = listOf(
                    PlannedAlarm(now + BOOT_GRACE_MILLIS, ScheduleSignal.END, delivery)
                ),
                dispatchImmediately = null
            )

            now >= schedule.startAtMillis -> ScheduleAlarmPlan(
                alarms = listOf(
                    PlannedAlarm(now + BOOT_GRACE_MILLIS, ScheduleSignal.START, delivery),
                    PlannedAlarm(schedule.endAtMillis, ScheduleSignal.END, delivery)
                ),
                dispatchImmediately = null
            )

            else -> ScheduleAlarmPlan(
                alarms = listOf(
                    PlannedAlarm(schedule.startAtMillis, ScheduleSignal.START, delivery),
                    PlannedAlarm(schedule.endAtMillis, ScheduleSignal.END, delivery)
                ),
                dispatchImmediately = null
            )
        }
    }

    fun inProcessDelays(schedule: DownloadSchedule, now: Long): InProcessDelays {
        return InProcessDelays(
            untilStartMillis = (schedule.startAtMillis - now).coerceAtLeast(0L),
            untilEndMillis = (schedule.endAtMillis - now).coerceAtLeast(0L)
        )
    }
}
