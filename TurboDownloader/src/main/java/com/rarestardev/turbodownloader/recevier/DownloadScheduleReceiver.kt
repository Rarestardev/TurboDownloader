package com.rarestardev.turbodownloader.recevier

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.rarestardev.turbodownloader.service.DownloadService

class DownloadScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (
            action != ACTION_START &&
            action != ACTION_END
        ) return

        val serviceIntent = Intent(
            context,
            DownloadService::class.java
        ).apply {
            this.action = when (action) {
                ACTION_START -> DownloadService.ACTION_SCHEDULE_START
                ACTION_END -> DownloadService.ACTION_SCHEDULE_END
                else -> return
            }
        }

        try {
            ContextCompat.startForegroundService(
                context,
                serviceIntent
            )
        } catch (exception: Exception) {
            // بهتر است خطا در logger کتابخانه ثبت شود.
            exception.printStackTrace()
        }
    }

    companion object {
        const val ACTION_START =
            "com.rarestardev.turbodownloader.SCHEDULE_START"

        const val ACTION_END =
            "com.rarestardev.turbodownloader.SCHEDULE_END"
    }
}