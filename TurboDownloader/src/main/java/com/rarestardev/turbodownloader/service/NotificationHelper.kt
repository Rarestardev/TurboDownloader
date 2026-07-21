package com.rarestardev.turbodownloader.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.rarestardev.turbodownloader.state.DownloadId
import kotlin.jvm.java

object NotificationHelper {
    private const val CHANNEL_ID = "turbodownloader_channel"

    const val ACTION_PAUSE = "ACTION_PAUSE"
    const val ACTION_RESUME = "ACTION_RESUME"
    const val ACTION_CANCEL = "ACTION_CANCEL"
    const val ACTION_CLICK = "ACTION_CLICK"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "turbodownloader",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    fun createForeground(
        context: Context
    ): Notification {
        createChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(
                android.R.drawable.stat_sys_download
            )
            .setContentTitle(
                "Turbo Downloader"
            )
            .setContentText(
                "Download service running"
            )
            .setOngoing(true)
            .setCategory(
                NotificationCompat.CATEGORY_SERVICE
            )
            .build()
    }

    fun create(context: Context, id: DownloadId, progress: Int): Notification {
        createChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(
                android.R.drawable.stat_sys_download
            )
            .setContentTitle(
                "در حال دانلود"
            )
            .setContentText(
                "پیشرفت: $progress%"
            )
            .setProgress(
                100,
                progress,
                false
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                "توقف",
                actionIntent(
                    context,
                    id,
                    ACTION_PAUSE
                )
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "لغو",
                actionIntent(
                    context,
                    id,
                    ACTION_CANCEL
                )
            )
            .build()
    }


    fun createPaused(
        context: Context,
        id: DownloadId,
        progress: Float
    ): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(
                android.R.drawable.ic_media_play
            )
            .setContentTitle(
                "دانلود متوقف شد"
            )
            .setContentText(
                "$progress%"
            )
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_media_play,
                "ادامه",
                actionIntent(
                    context,
                    id,
                    ACTION_RESUME
                )
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "لغو",
                actionIntent(
                    context,
                    id,
                    ACTION_CANCEL
                )
            )
            .build()
    }


    fun createCompleted(
        context: Context
    ): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(
                android.R.drawable.stat_sys_download_done
            )
            .setContentTitle(
                "دانلود کامل شد"
            )
            .setContentText(
                "فایل آماده است"
            )
            .setAutoCancel(true)
            .build()
    }


    fun createFailed(
        context: Context
    ): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(
                android.R.drawable.stat_notify_error
            )
            .setContentTitle(
                "خطا در دانلود"
            )
            .setAutoCancel(true)
            .build()
    }

    private fun actionIntent(
        context: Context,
        id: DownloadId,
        action: String
    ): PendingIntent {
        val intent = Intent(
            context,
            DownloadReceiver::class.java
        )

        intent.action = action

        intent.putExtra(
            "DOWNLOAD_ID",
            id.value
        )

        return PendingIntent.getBroadcast(
            context,
            id.value.hashCode() + action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )
    }
}