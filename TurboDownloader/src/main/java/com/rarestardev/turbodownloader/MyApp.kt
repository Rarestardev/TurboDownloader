package com.rarestardev.turbodownloader

import android.app.Application
import com.rarestardev.turbodownloader.service.NotificationHelper

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
    }
}