package com.rarestardev.turbodownloader.app

import android.content.Context
import androidx.startup.Initializer
import com.rarestardev.turbodownloader.service.NotificationHelper

class DownloadInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        NotificationHelper.createChannel(context.applicationContext)
    }


    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}