package com.rarestardev.turbodownloader.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.rarestardev.turbodownloader.R
import com.rarestardev.turbodownloader.state.DownloadId
import com.rarestardev.turbodownloader.state.DownloadStatus
import kotlin.jvm.java

/**
 * Helper class for creating and managing Android Notifications for the download service.
 */
internal object NotificationHelper {
    private const val CHANNEL_ID = "turbodownloader_channel"

    const val ACTION_PAUSE = "ACTION_PAUSE"
    const val ACTION_RESUME = "ACTION_RESUME"
    const val ACTION_CANCEL = "ACTION_CANCEL"
    const val ACTION_CLICK = "ACTION_CLICK"

    /**
     * Ensures the notification channel is created for Android O and above.
     */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW // Use LOW to avoid sound on every update
            )
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    /**
     * Creates a simple foreground notification to keep the service alive.
     */
    @SuppressLint("LaunchActivityFromNotification")
    fun createForeground(
        context: Context
    ): Notification {
        createChannel(context)
        val intent = Intent(context, DownloadReceiver::class.java).apply {
            action = ACTION_CLICK
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_MUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.download_ic)
            .setContentTitle("Turbo Downloader")
            .setContentText("Monitoring downloads...")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Creates or updates a notification for an active download.
     */
    fun create(
        context: Context, 
        id: DownloadId, 
        progress: Int, 
        fileName: String = "Downloading...", 
        status: DownloadStatus = DownloadStatus.RUNNING,
        speed: String = "",
        peers: Int = 0
    ): Notification {
        createChannel(context)
        
        val title = when (status) {
            DownloadStatus.SEEDING -> "Seeding: $fileName"
            DownloadStatus.CONNECTING -> "Connecting: $fileName"
            else -> fileName
        }

        val contentText = buildString {
            append("Progress: $progress%")
            if (speed.isNotEmpty()) append(" • $speed")
            if (peers > 0) append(" • Peers: $peers")
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(contentText)
            .setProgress(100, progress, status == DownloadStatus.CONNECTING)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(notificationClickIntent(context, id))
            .addAction(
                android.R.drawable.ic_media_pause,
                "Pause",
                actionIntent(context, id, ACTION_PAUSE)
            )
            .build()
    }

    /**
     * Creates a notification for a paused download.
     */
    fun createPaused(
        context: Context,
        id: DownloadId,
        progress: Float
    ): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Download Paused")
            .setContentText("Progress: $progress%")
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_media_play,
                "Resume",
                actionIntent(context, id, ACTION_RESUME)
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    /**
     * Creates a notification for a completed download.
     */
    fun createCompleted(
        context: Context
    ): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download Complete")
            .setContentText("File is ready.")
            .setAutoCancel(true)
            .build()
    }

    /**
     * Creates a notification for a failed download.
     */
    fun createFailed(
        context: Context
    ): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download Failed")
            .setContentText("An error occurred during download.")
            .setAutoCancel(true)
            .build()
    }

    private fun actionIntent(
        context: Context,
        id: DownloadId,
        action: String
    ): PendingIntent {
        val intent = Intent(context, DownloadReceiver::class.java).apply {
            this.action = action
            putExtra("DOWNLOAD_ID", id.value)
        }

        return PendingIntent.getBroadcast(
            context,
            id.value.hashCode() + action.hashCode(),
            intent,
            PendingIntent.FLAG_MUTABLE
        )
    }

    private fun notificationClickIntent(context: Context, id: DownloadId): PendingIntent? {
        val clickIntent = Intent(context, DownloadReceiver::class.java).apply {
            action = ACTION_CLICK
            putExtra("DOWNLOAD_ID", id.value)
        }

        return PendingIntent.getBroadcast(
            context,
            id.value.hashCode(),
            clickIntent,
            PendingIntent.FLAG_MUTABLE
        )
    }
}
