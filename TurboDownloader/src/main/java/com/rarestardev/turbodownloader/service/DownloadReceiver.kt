package com.rarestardev.turbodownloader.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

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
    }
}