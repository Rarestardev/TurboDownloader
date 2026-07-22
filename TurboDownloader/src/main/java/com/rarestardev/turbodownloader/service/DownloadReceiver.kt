package com.rarestardev.turbodownloader.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.rarestardev.turbodownloader.api.ChunkDownloadApi
import com.rarestardev.turbodownloader.service.NotificationHelper.ACTION_CLICK
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

        if (intent.action == ACTION_CLICK) {
            ChunkDownloadApi
                .getNotificationListener()
                ?.onNotificationClick(DownloadId(downloadId))

        }
    }
}