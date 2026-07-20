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
import com.rarestardev.turbodownloader.utils.TurboConstants
import java.io.File

class TurboDownloader private constructor(
    private val context: Context,
    private val threadCount: Int,
    private val destinationDir: File
) {
    private val api = ChunkDownloadApi(context)
    private val manager = api.manager

    fun startDownload(
        url: String,
        fileName: String = "file_${System.currentTimeMillis()}.bin"
    ): DownloadId {
        val request = DownloadRequest(
            uri = url,
            fileName = fileName,
            destinationDir = destinationDir,
            threadCount = threadCount
        )
        return manager.enqueue(request)
    }

    fun pause(id: DownloadId) = manager.pause(id)
    fun resume(id: DownloadId) = manager.resume(id)
    fun cancel(id: DownloadId) = manager.cancel(id)
    fun observeState() = manager.state

    class Builder(private val activity: Activity) {
        private var threadCount: Int = 4
        private var destinationDir: File? = null
        private var checkPermission: Boolean = false

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
                context = activity.applicationContext,
                threadCount = threadCount,
                destinationDir = dir
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