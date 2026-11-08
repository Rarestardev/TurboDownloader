package com.rarestardev.turbodownloader.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.util.Log
import androidx.room.Room
import com.rarestardev.turbodownloader.core.DownloadManager
import com.rarestardev.turbodownloader.state.DownloadId
import com.rarestardev.turbodownloader.state.DownloadState
import com.rarestardev.turbodownloader.storage.DownloadDatabase
import com.rarestardev.turbodownloader.utils.TurboConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

internal class DownloadService : Service() {
    companion object {
        const val ACTION_SCHEDULE_START =
            "com.rarestardev.turbodownloader.service.SCHEDULE_START"

        const val ACTION_SCHEDULE_END =
            "com.rarestardev.turbodownloader.service.SCHEDULE_END"
    }
    private lateinit var manager: DownloadManager
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        val db = Room.databaseBuilder(
            applicationContext,
            DownloadDatabase::class.java,
            "chunk_downloads.db"
        )
            .addMigrations(DownloadDatabase.MIGRATION_2_3)
            .build()

        manager =
            DownloadManagerHolder.manager
                ?: run {
                    val newManager = DownloadManager(
                        db.dao(),
                        serviceScope,
                        applicationContext
                    )

                    DownloadManagerHolder.manager = newManager
                    newManager
                }

        /*try {
            startForeground(
                1,
                NotificationHelper.createForeground(this)
            )
            Log.d("DownloadService", "Foreground started")
        } catch (e: Exception) {
            Log.e("DownloadService", "startForeground failed", e)
        }*/

        serviceScope.launch {
            manager.state.collect { map ->
                map.forEach { (id, state) ->
                    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

                    when (state) {
                        is DownloadState.Running -> {
                            if (!foregroundStarted){

                                startForeground(
                                    notificationId(id),
                                    NotificationHelper.create(
                                        this@DownloadService,
                                        id,
                                        state.progress.percent
                                    )
                                )

                                foregroundStarted = true
                            }else {
                                nm.notify(
                                    notificationId(id),
                                    NotificationHelper.create(
                                        this@DownloadService,
                                        id,
                                        state.progress.percent
                                    )
                                )
                            }
                        }

                        is DownloadState.Paused -> {
                            nm.notify(
                                notificationId(id),
                                NotificationHelper.createPaused(
                                    this@DownloadService,
                                    id,
                                    state.progress.percent.toFloat()
                                )
                            )
                        }

                        is DownloadState.Completed -> {
                            nm.notify(
                                notificationId(id),
                                NotificationHelper.createCompleted(
                                    this@DownloadService
                                )
                            )
                        }

                        is DownloadState.Failed -> {
                            nm.notify(
                                notificationId(id),
                                NotificationHelper.createFailed(
                                    this@DownloadService
                                )
                            )
                        }

                        is DownloadState.Cancelled -> {
                            nm.cancel(
                                notificationId(id)
                            )
                        }

                        else -> {}
                    }
                }
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (!foregroundStarted) {
            startForeground(
                1,
                NotificationHelper.createForeground(this)
            )
            foregroundStarted = true
        }

        when (intent?.action) {

            ACTION_SCHEDULE_START -> {
                manager.onScheduleStart()
            }

            ACTION_SCHEDULE_END -> {
                manager.onScheduleEnd()
            }

            NotificationHelper.ACTION_PAUSE -> {
                intent.getStringExtra("DOWNLOAD_ID")?.let {
                    manager.pause(DownloadId(it))
                }
            }

            NotificationHelper.ACTION_RESUME -> {
                intent.getStringExtra("DOWNLOAD_ID")?.let {
                    manager.resume(DownloadId(it))
                }
            }

            NotificationHelper.ACTION_CANCEL -> {
                intent.getStringExtra("DOWNLOAD_ID")?.let {
                    manager.cancel(DownloadId(it))
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.e(TurboConstants.TURBO_DOWNLOADER_LOG, "Destroy")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?) = null

    private fun notificationId(id: DownloadId): Int {
        return id.value.hashCode().absoluteValue + 1000
    }
}