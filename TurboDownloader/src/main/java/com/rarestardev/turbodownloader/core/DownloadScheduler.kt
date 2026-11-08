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

    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val storage = DownloadScheduleStorage(appContext)

    fun schedule(schedule: DownloadSchedule) {
        require(schedule.endAtMillis > System.currentTimeMillis()) {
            "Schedule end time must be in the future"
        }
        storage.save(schedule)
        cancelStartAlarm()
        cancelEndAlarm()

        val now = System.currentTimeMillis()
        when {
            now < schedule.startAtMillis -> {
                scheduleAlarm(
                    triggerAt = schedule.startAtMillis,
                    action = DownloadScheduleReceiver.ACTION_START
                )

                scheduleAlarm(
                    triggerAt = schedule.endAtMillis,
                    action = DownloadScheduleReceiver.ACTION_END
                )
            }

            now < schedule.endAtMillis -> {
                sendAction()

                scheduleAlarm(
                    triggerAt = schedule.endAtMillis,
                    action = DownloadScheduleReceiver.ACTION_END
                )
            }

            else -> storage.clear()
        }
    }

    fun cancel(){
        cancelStartAlarm()
        cancelEndAlarm()
        storage.clear()
    }

    fun restoreAfterBoot() {
        val schedule = storage.get() ?: return
        val now = System.currentTimeMillis()

        when {
            now >= schedule.endAtMillis -> {
                cancel()
            }

            now >= schedule.startAtMillis -> {
                sendAction()

                scheduleAlarm(
                    triggerAt = schedule.endAtMillis,
                    action = DownloadScheduleReceiver.ACTION_END
                )
            }

            else -> {
                scheduleAlarm(
                    triggerAt = schedule.startAtMillis,
                    action = DownloadScheduleReceiver.ACTION_START
                )

                scheduleAlarm(
                    triggerAt = schedule.endAtMillis,
                    action = DownloadScheduleReceiver.ACTION_END
                )
            }
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun scheduleAlarm(
        triggerAt: Long,
        action: String
    ) {
        val pendingIntent = createPendingIntent(action)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        } else {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        }
    }

    private fun sendAction() {
        val intent = Intent(appContext, DownloadScheduleReceiver::class.java)
            .setAction(DownloadScheduleReceiver.ACTION_START)

        appContext.sendBroadcast(intent)
    }

    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(
            appContext,
            DownloadScheduleReceiver::class.java
        ).setAction(action)

        val requestCode = when (action) {
            DownloadScheduleReceiver.ACTION_START -> 1001
            DownloadScheduleReceiver.ACTION_END -> 1002
            else -> 1003
        }

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