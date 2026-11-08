package com.rarestardev.turbodownloader.recevier

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.rarestardev.turbodownloader.service.DownloadService

class DownloadScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val serviceAction = when (intent.action) {
            ACTION_START -> DownloadService.ACTION_SCHEDULE_START
            ACTION_END -> DownloadService.ACTION_SCHEDULE_END
            else -> return
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
            android.util.Log.e(
                "TurboDownloader",
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