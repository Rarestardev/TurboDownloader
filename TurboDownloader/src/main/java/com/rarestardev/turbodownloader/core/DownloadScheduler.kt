package com.rarestardev.turbodownloader.core

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.rarestardev.turbodownloader.model.DownloadSchedule
import com.rarestardev.turbodownloader.model.ScheduleSignal
import com.rarestardev.turbodownloader.model.ScheduleSignalRecord
import com.rarestardev.turbodownloader.recevier.DownloadScheduleReceiver
import com.rarestardev.turbodownloader.storage.DownloadScheduleStorage
import com.rarestardev.turbodownloader.utils.TurboConstants
import androidx.core.net.toUri

internal class DownloadScheduler(context: Context) {

    private val appContext = context.applicationContext

    private val alarmManager =
        appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val storage = DownloadScheduleStorage(appContext)

    private val engine = ScheduleEngine(
        storage = storage,
        clock = { System.currentTimeMillis() },
        delivery = { SchedulePlanner.delivery(Build.VERSION.SDK_INT, exactAlarmsAllowed()) },
        cancelAlarm = { signal -> alarmManager.cancel(createPendingIntent(signal)) },
        scheduleAlarm = { alarm -> scheduleAlarm(alarm) },
        dispatchNow = { signal -> sendAction(signal) }
    )

    fun schedule(schedule: DownloadSchedule) = engine.schedule(schedule)

    fun cancel() = engine.cancel()

    fun suspendAlarms() = engine.suspendAlarms()

    fun restoreAfterBoot() = engine.restoreAfterBoot()

    fun current(): DownloadSchedule? = engine.current()

    fun peek(): DownloadSchedule? = storage.peek()

    fun isSchedulingEnabled(): Boolean = storage.isSchedulingEnabled()

    fun setSchedulingEnabled(enabled: Boolean) = storage.setSchedulingEnabled(enabled)

    fun lastSignal(): ScheduleSignalRecord? = storage.lastSignal()

    fun exactAlarmAccess(): ExactAlarmAccess {
        val required = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val granted = exactAlarmsAllowed()
        val settingsIntent = if (required && !granted) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = "package:${appContext.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            null
        }
        return ExactAlarmAccess(
            required = required,
            granted = granted,
            settingsIntent = settingsIntent
        )
    }

    private fun exactAlarmsAllowed(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    private fun scheduleAlarm(alarm: PlannedAlarm) {
        val pendingIntent = createPendingIntent(alarm.signal)
        try {
            if (alarm.delivery == AlarmDelivery.EXACT) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    alarm.triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    alarm.triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (security: SecurityException) {
            Log.e(
                TurboConstants.TURBO_DOWNLOADER_LOG,
                "Exact alarm was rejected; falling back to an inexact alarm",
                security
            )
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                alarm.triggerAtMillis,
                pendingIntent
            )
        }
        Log.i(
            TurboConstants.TURBO_DOWNLOADER_LOG,
            "Scheduled ${alarm.signal} at ${alarm.triggerAtMillis} via ${alarm.delivery}"
        )
    }

    private fun sendAction(signal: ScheduleSignal) {
        val intent = Intent(
            appContext,
            DownloadScheduleReceiver::class.java
        ).setAction(actionFor(signal))
        appContext.sendBroadcast(intent)
    }

    private fun createPendingIntent(signal: ScheduleSignal): PendingIntent {
        val requestCode = when (signal) {
            ScheduleSignal.START -> 1001
            ScheduleSignal.END -> 1002
        }
        val intent = Intent(
            appContext,
            DownloadScheduleReceiver::class.java
        ).setAction(actionFor(signal))
        return PendingIntent.getBroadcast(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun actionFor(signal: ScheduleSignal): String {
        return when (signal) {
            ScheduleSignal.START -> DownloadScheduleReceiver.ACTION_START
            ScheduleSignal.END -> DownloadScheduleReceiver.ACTION_END
        }
    }
}
