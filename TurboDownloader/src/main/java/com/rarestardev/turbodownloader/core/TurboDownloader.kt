package com.rarestardev.turbodownloader.core

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import com.rarestardev.turbodownloader.api.ChunkDownloadApi
import com.rarestardev.turbodownloader.model.DownloadRequest
import com.rarestardev.turbodownloader.state.DownloadId
import com.rarestardev.turbodownloader.utils.FormatUtils
import com.rarestardev.turbodownloader.utils.TurboConstants
import java.io.File

class TurboDownloader private constructor(
    private val context: Context,
    private val threadCount: Int,
    private val destinationDir: File,
    private val showFormatter: Boolean
) {

    private val api = ChunkDownloadApi(context)
    private val manager = api.manager

    // -------------------------
    // PUBLIC API
    // -------------------------

    fun startDownload(
        url: String,
        fileName: String? = null
    ): DownloadId {
        val ext = extractExtension(url)
        val finalName = fileName ?: "file_${System.currentTimeMillis()}.$ext"

        val request = DownloadRequest(
            uri = url,
            fileName = finalName,
            destinationDir = destinationDir,
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

        fun build(): TurboDownloader {

            val dir = destinationDir
                ?: throw IllegalStateException("Destination directory is required")

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
                showFormatter = showFormatter
            )
        }

        private fun hasNotificationPermission(activity: Activity): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        }

        private fun requestNotificationPermission(activity: Activity) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }
    }
}