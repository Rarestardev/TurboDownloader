package com.rarestardev.turbodownloader.recevier

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.rarestardev.turbodownloader.model.ScheduleSignal
import com.rarestardev.turbodownloader.service.DownloadManagerHolder
import com.rarestardev.turbodownloader.service.DownloadService
import com.rarestardev.turbodownloader.storage.DownloadScheduleStorage
import com.rarestardev.turbodownloader.utils.TurboConstants

class DownloadScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val signal = when (intent.action) {
            ACTION_START -> ScheduleSignal.START
            ACTION_END -> ScheduleSignal.END
            else -> return
        }
        val serviceAction = when (signal) {
            ScheduleSignal.START -> DownloadService.ACTION_SCHEDULE_START
            ScheduleSignal.END -> DownloadService.ACTION_SCHEDULE_END
        }

        val deliveredAt = System.currentTimeMillis()
        DownloadScheduleStorage(context).recordSignal(signal, deliveredAt)
        Log.i(
            TurboConstants.TURBO_DOWNLOADER_LOG,
            "Schedule alarm delivered: $signal at $deliveredAt"
        )

        when (signal) {
            ScheduleSignal.START -> DownloadManagerHolder.manager?.onScheduleStart(releaseHeldQueue = true)
            ScheduleSignal.END -> DownloadManagerHolder.manager?.onScheduleEnd()
        }

        val serviceIntent = Intent(
            context,
            DownloadService::class.java
        ).setAction(serviceAction)

        try {
            ContextCompat.startForegroundService(
                context,
                serviceIntent
            )
        } catch (exception: Exception) {
            Log.e(
                TurboConstants.TURBO_DOWNLOADER_LOG,
                "Unable to start scheduled download service",
                exception
            )
        }
    }

    companion object {
        const val ACTION_START =
            "com.rarestardev.turbodownloader.SCHEDULE_START"

        const val ACTION_END =
            "com.rarestardev.turbodownloader.SCHEDULE_END"
    }
}
