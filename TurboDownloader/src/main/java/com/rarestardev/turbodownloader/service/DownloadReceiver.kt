package com.rarestardev.turbodownloader.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.rarestardev.turbodownloader.api.ChunkDownloadApi
import com.rarestardev.turbodownloader.service.NotificationHelper.ACTION_CANCEL
import com.rarestardev.turbodownloader.service.NotificationHelper.ACTION_CLICK
import com.rarestardev.turbodownloader.service.NotificationHelper.ACTION_PAUSE
import com.rarestardev.turbodownloader.service.NotificationHelper.ACTION_RESUME
import com.rarestardev.turbodownloader.state.DownloadId

class DownloadReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val downloadId =
            intent.getStringExtra("DOWNLOAD_ID")
                ?: return


        val serviceIntent = Intent(
            context,
            DownloadService::class.java
        ).apply {

            action = intent.action

            putExtra(
                "DOWNLOAD_ID",
                downloadId
            )
        }


        ContextCompat.startForegroundService(
            context,
            serviceIntent
        )

        when(intent.action) {
            ACTION_CLICK -> {
                ChunkDownloadApi
                    .getNotificationListener()
                    ?.onNotificationClick(DownloadId(downloadId))

            }

            ACTION_PAUSE -> {
                ChunkDownloadApi
                    .getNotificationListener()
                    ?.onPauseClick(DownloadId(downloadId))

            }

            ACTION_RESUME -> {
                ChunkDownloadApi
                    .getNotificationListener()
                    ?.onResumeClick(DownloadId(downloadId))

            }

            ACTION_CANCEL -> {
                ChunkDownloadApi
                    .getNotificationListener()
                    ?.onCancelClick(DownloadId(downloadId))

            }
        }
    }
}