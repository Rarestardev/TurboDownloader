package com.rarestardev.turbodownloader.core

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import com.rarestardev.turbodownloader.api.ChunkDownloadApi
import com.rarestardev.turbodownloader.listener.DownloadNotificationListener
import com.rarestardev.turbodownloader.model.DownloadRequest
import com.rarestardev.turbodownloader.state.DownloadId
import com.rarestardev.turbodownloader.utils.FormatUtils
import com.rarestardev.turbodownloader.utils.TurboConstants
import java.io.File

class TurboDownloader private constructor(
    private val context: Context,
    private val threadCount: Int,
    private val destinationDir: File,
    private val showFormatter: Boolean,
    private val notificationListener: DownloadNotificationListener?
) {
    private val manager = ChunkDownloadApi.get(context)

    init {
        notificationListener?.let {
            ChunkDownloadApi.setNotificationListener(it)
        }
    }

    fun startDownload(
        url: String,
        fileName: String? = null
    ): DownloadId {
        val ext = extractExtension(url)
        val finalName = fileName ?: "file_${System.currentTimeMillis()}.$ext"

        val request = DownloadRequest(
            uri = url,
            fileName = finalName,
            threadCount = threadCount
        )
        return manager.enqueue(request)
    }

    fun pause(id: DownloadId) = manager.pause(id)
    fun resume(id: DownloadId) = manager.resume(id)
    suspend fun cancel(id: DownloadId) = manager.cancel(id)
    fun release() = manager.release()
    fun downloadState() = manager.state
    fun getAllDownloads() = manager.allDownloads()

    fun formatSpeed(speed: Long): String {
        return if (showFormatter) {
            FormatUtils.formatSpeed(speed)
        } else {
            speed.toString()
        }
    }

    fun formatEta(eta: Long): String {
        return if (showFormatter) {
            FormatUtils.formatEta(eta)
        } else {
            eta.toString()
        }
    }

    private fun extractExtension(url: String): String {
        return url.substringAfterLast('.', "").substringBefore('?')
    }

    class Builder(private val activity: Activity, private val context: Context) {

        private var notificationListener:
                DownloadNotificationListener? = null
        private var threadCount: Int = 4
        private var destinationDir: File? = null
        private var checkPermission: Boolean = false
        private var showFormatter: Boolean = false

        fun setShowFormatter(enabled: Boolean) = apply {
            showFormatter = enabled
        }

        fun setThread(count: Int) = apply {
            threadCount = count
        }

        fun setDir(dir: File) = apply {
            destinationDir = dir
        }

        fun setPermissionChecked(enabled: Boolean) = apply {
            checkPermission = enabled
        }

        fun setNotificationListener(
            listener: DownloadNotificationListener
        ) = apply {
            notificationListener = listener
        }

        fun build(): TurboDownloader {

            val dir = destinationDir
                ?: Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

            if (threadCount <= 0)
                throw IllegalArgumentException("Thread count must be greater than 0")

            if (threadCount > 16)
                throw IllegalArgumentException("Thread count cannot exceed 16")

            if (checkPermission) {
                if (!hasNotificationPermission(activity)) {
                    requestNotificationPermission(activity)
                    Log.w(TurboConstants.TURBO_DOWNLOADER_LOG, "Notification permission required!")
                }
            }

            return TurboDownloader(
                context = context.applicationContext,
                threadCount = threadCount,
                destinationDir = dir,
                showFormatter = showFormatter,
                notificationListener = notificationListener
            )
        }

        private fun hasNotificationPermission(
            activity: Activity
        ): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                NotificationManagerCompat
                    .from(activity)
                    .areNotificationsEnabled()
            }
        }

        @SuppressLint("InlinedApi")
        private fun requestNotificationPermission(
            activity: Activity
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(
                        Manifest.permission.POST_NOTIFICATIONS
                    ),
                    100
                )
            } else {
                val intent = Intent().apply {
                    action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    putExtra(
                        Settings.EXTRA_APP_PACKAGE,
                        activity.packageName
                    )
                }
                activity.startActivity(intent)
            }
        }
    }
}