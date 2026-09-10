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
import com.rarestardev.turbodownloader.utils.FormatUtils
import com.rarestardev.turbodownloader.utils.TurboConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

/**
 * Background service responsible for managing downloads and maintaining foreground status.
 * It observes the download states from [DownloadManager] and updates notifications accordingly.
 */
internal class DownloadService : Service() {

    private lateinit var manager: DownloadManager
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        
        // Satisfy foreground service requirements immediately
        val initialNotification = NotificationHelper.createForeground(this)
        startForeground(999, initialNotification)
        foregroundStarted = true

        val db = Room.databaseBuilder(
            applicationContext,
            DownloadDatabase::class.java,
            "chunk_downloads.db"
        ).build()

        manager =
            DownloadManagerHolder.manager
                ?: run {
                    val newManager = DownloadManager(
                        db.dao(),
                        serviceScope,
                        this
                    )

                    DownloadManagerHolder.manager = newManager
                    newManager
                }

        observeDownloadStates(db)
    }

    /**
     * Observes changes in download states and updates notifications.
     */
    private fun observeDownloadStates(db: DownloadDatabase) {
        serviceScope.launch {
            manager.state.collect { map ->
                map.forEach { (id, state) ->
                    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

                    when (state) {
                        is DownloadState.Running -> {
                            val fileName = withContext(Dispatchers.IO) {
                                db.dao().getDownload(id.value)?.fileName
                            } ?: "File downloading..."
                            
                            val speedStr = FormatUtils.formatSpeed(state.progress.speedBytesPerSec)
                            val peers = state.progress.peers

                            val notification = NotificationHelper.create(
                                this@DownloadService,
                                id,
                                state.progress.percent,
                                fileName,
                                state.status,
                                speedStr,
                                peers
                            )
                            nm.notify(notificationId(id), notification)
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
                            nm.cancel(notificationId(id))
                        }

                        else -> {}
                    }
                }
            }
        }
    }

    /**
     * Handles intent actions for pausing, resuming, and cancelling downloads from notifications.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val id = intent?.getStringExtra("DOWNLOAD_ID")

        if (id != null) {
            when (action) {
                NotificationHelper.ACTION_PAUSE -> {
                    manager.pause(
                        DownloadId(id)
                    )
                }

                NotificationHelper.ACTION_RESUME -> {
                    manager.resume(
                        DownloadId(id)
                    )
                }

                NotificationHelper.ACTION_CANCEL -> {
                    manager.cancel(
                        DownloadId(id)
                    )

                    val nm =
                        getSystemService(
                            NOTIFICATION_SERVICE
                        ) as NotificationManager

                    nm.cancel(
                        notificationId(DownloadId(id))
                    )
                }
            }
        }

        Log.i(TurboConstants.TURBO_DOWNLOADER_LOG, "Download service start commend...")
        return START_REDELIVER_INTENT
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