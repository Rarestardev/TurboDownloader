package com.rarestardev.turbodownloader.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.util.Log
import androidx.room.Room
import com.rarestardev.turbodownloader.core.DownloadManager
import com.rarestardev.turbodownloader.state.DownloadState
import com.rarestardev.turbodownloader.storage.DownloadDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DownloadService : Service() {

    private lateinit var manager: DownloadManager
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()

        println("Download service on create run...")

        val db = Room.databaseBuilder(
            applicationContext,
            DownloadDatabase::class.java,
            "chunk_downloads.db"
        ).build()

        manager = DownloadManager(db.dao(), serviceScope,this)

        try {
            startForeground(1, NotificationHelper.create(this, 0))
            Log.d("DownloadService", "Foreground started")
        } catch (e: Exception) {
            Log.e("DownloadService", "startForeground failed", e)
        }

        serviceScope.launch {
            manager.state.collect { map ->
                map.values.forEach { state ->
                    if (state is DownloadState.Running) {
                        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                        nm.notify(1, NotificationHelper.create(this@DownloadService, state.progress.percent))
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // اینجا می‌تونی بر اساس intent دانلود جدید رو enqueue کنی
        println("Download service start commend...")
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.e("DownloadService","Destroy")
    }

    override fun onBind(intent: Intent?) = null
}