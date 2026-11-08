package com.rarestardev.turbodownloader.core

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.rarestardev.turbodownloader.model.DownloadSchedule
import com.rarestardev.turbodownloader.recevier.DownloadScheduleReceiver
import com.rarestardev.turbodownloader.storage.DownloadScheduleStorage
import kotlin.jvm.java

internal class DownloadScheduler(context: Context) {

    private val appContext = context.applicationContext

    private val alarmManager =
        appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val storage = DownloadScheduleStorage(appContext)

    fun schedule(schedule: DownloadSchedule) {
        require(schedule.endAtMillis > System.currentTimeMillis()) {
            "Schedule end time must be in the future"
        }

        storage.save(schedule)

        cancelStartAlarm()
        cancelEndAlarm()

        val now = System.currentTimeMillis()

        if (now < schedule.startAtMillis) {
            scheduleAlarm(
                schedule.startAtMillis,
                DownloadScheduleReceiver.ACTION_START
            )
        } else {
            sendAction(DownloadScheduleReceiver.ACTION_START)
        }

        scheduleAlarm(
            schedule.endAtMillis,
            DownloadScheduleReceiver.ACTION_END
        )
    }

    fun cancel() {
        cancelStartAlarm()
        cancelEndAlarm()
        storage.clear()
    }

    fun restoreAfterBoot() {
        val schedule = storage.get() ?: return
        val now = System.currentTimeMillis()

        when {
            now >= schedule.endAtMillis -> {
                // برای رسیدگی به دانلودهایی که قبل از Restart در حال اجرا بوده‌اند.
                scheduleAlarm(
                    now + 1_000L,
                    DownloadScheduleReceiver.ACTION_END
                )
            }

            now >= schedule.startAtMillis -> {
                // به جای اجرای مستقیم سرویس از BootReceiver،
                // یک Alarm برای شروع مجدد ثبت می‌کنیم.
                scheduleAlarm(
                    now + 1_000L,
                    DownloadScheduleReceiver.ACTION_START
                )

                scheduleAlarm(
                    schedule.endAtMillis,
                    DownloadScheduleReceiver.ACTION_END
                )
            }

            else -> {
                scheduleAlarm(
                    schedule.startAtMillis,
                    DownloadScheduleReceiver.ACTION_START
                )

                scheduleAlarm(
                    schedule.endAtMillis,
                    DownloadScheduleReceiver.ACTION_END
                )
            }
        }
    }

    private fun scheduleAlarm(
        triggerAt: Long,
        action: String
    ) {
        val pendingIntent = createPendingIntent(action)

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            alarmManager.canScheduleExactAlarms()
        ) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        }
    }

    private fun sendAction(action: String) {
        val intent = Intent(
            appContext,
            DownloadScheduleReceiver::class.java
        ).setAction(action)

        appContext.sendBroadcast(intent)
    }

    private fun createPendingIntent(action: String): PendingIntent {
        val requestCode = when (action) {
            DownloadScheduleReceiver.ACTION_START -> 1001
            DownloadScheduleReceiver.ACTION_END -> 1002
            else -> 1003
        }

        val intent = Intent(
            appContext,
            DownloadScheduleReceiver::class.java
        ).setAction(action)

        return PendingIntent.getBroadcast(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelStartAlarm() {
        alarmManager.cancel(
            createPendingIntent(DownloadScheduleReceiver.ACTION_START)
        )
    }

    private fun cancelEndAlarm() {
        alarmManager.cancel(
            createPendingIntent(DownloadScheduleReceiver.ACTION_END)
        )
    }
}