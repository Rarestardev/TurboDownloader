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

class DownloadService : Service() {

    private lateinit var manager: DownloadManager
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
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

        try {
            startForeground(
                1,
                NotificationHelper.createForeground(this)
            )
            Log.d("DownloadService", "Foreground started")
        } catch (e: Exception) {
            Log.e("DownloadService", "startForeground failed", e)
        }

        serviceScope.launch {
            manager.state.collect { map ->
                /*Log.d(
                    TurboConstants.TURBO_DOWNLOADER_LOG,
                    "states size = ${map.size}"
                )*/
                map.forEach { (id, state) ->

                    /*Log.d(
                        TurboConstants.TURBO_DOWNLOADER_LOG,
                        "id=${id.value} state=$state"
                    )*/

                    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

                    when (state) {
                        is DownloadState.Running -> {
                            Log.d(
                                TurboConstants.TURBO_DOWNLOADER_LOG,
                                "show download notification ${id.value}"
                            )

                            nm.notify(
                                notificationId(id),
                                NotificationHelper.create(
                                    this@DownloadService,
                                    id,
                                    state.progress.percent
                                )
                            )
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
    }

    override fun onBind(intent: Intent?) = null

    private fun notificationId(id: DownloadId): Int {
        return id.value.hashCode().absoluteValue + 1000
    }
}