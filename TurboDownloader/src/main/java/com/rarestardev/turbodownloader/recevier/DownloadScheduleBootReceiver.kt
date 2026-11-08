package com.rarestardev.turbodownloader.recevier

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rarestardev.turbodownloader.core.DownloadScheduler

class DownloadScheduleBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                DownloadScheduler(context).restoreAfterBoot()
            }
        }
    }
}