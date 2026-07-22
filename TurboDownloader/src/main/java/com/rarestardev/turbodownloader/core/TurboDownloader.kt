package com.rarestardev.turbodownloader.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import com.rarestardev.turbodownloader.api.ChunkDownloadApi
import com.rarestardev.turbodownloader.listener.DownloadNotificationListener
import com.rarestardev.turbodownloader.listener.NetworkConnectionListener
import com.rarestardev.turbodownloader.model.DownloadRequest
import com.rarestardev.turbodownloader.state.DownloadId
import com.rarestardev.turbodownloader.utils.FormatUtils
import com.rarestardev.turbodownloader.utils.TurboConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class TurboDownloader private constructor(
    private val context: Context,
    private val threadCount: Int,
    private val showFormatter: Boolean,
    private val autoThreading: Boolean
) {
    private val manager = ChunkDownloadApi.get(context)
    private var notificationListener: DownloadNotificationListener? = null
    private var connectionListener: NetworkConnectionListener? = null

    suspend fun startDownload(
        url: String,
        fileName: String? = null
    ): DownloadId? {
        val ext = extractExtension(url)
        val finalName = fileName ?: "file_${System.currentTimeMillis()}.$ext"

        val maxRetries = 5
        val delayMs = 5000L

        if (hasNotificationPermission()) {
            for (attempt in 1..maxRetries) {
                if (isInternetAvailable()) {
                    connectionListener?.onInternetAvailable()
                    val request = DownloadRequest(
                        uri = url,
                        fileName = finalName,
                        threadCount = threadCount,
                        autoThreading = autoThreading
                    )
                    return manager.enqueue(request)
                }
                Log.w(
                    TurboConstants.TURBO_DOWNLOADER_LOG,
                    "No internet (attempt $attempt/$maxRetries)"
                )

                if (attempt == maxRetries) {
                    Log.e(
                        TurboConstants.TURBO_DOWNLOADER_LOG,
                        "Download failed: no internet after $maxRetries"
                    )
                    connectionListener?.onInternetFailed()
                    return null
                }

                connectionListener?.onRetry(attempt, maxRetries, delayMs)

                delay(delayMs)
            }
        } else {
            Log.e(TurboConstants.TURBO_DOWNLOADER_LOG, "Post notification permission needs!")
            throw IllegalArgumentException("Post notification permission needs!")
        }
        return null
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

    fun setNotificationListener(listener: DownloadNotificationListener) {
        this.notificationListener = listener
        ChunkDownloadApi.setNotificationListener(listener)
    }

    fun setNetworkConnectionListener(listener: NetworkConnectionListener){
        this.connectionListener = listener
    }

    private fun extractExtension(url: String): String {
        return url.substringAfterLast('.', "").substringBefore('?')
    }

    private suspend fun isInternetAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress("8.8.8.8", 53), 3000)
            socket.close()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat
                .from(context)
                .areNotificationsEnabled()
        }
    }

    class Builder(private val context: Context) {
        private var threadCount: Int = 4
        private var showFormatter: Boolean = false
        private var autoThreading: Boolean = false

        fun setAutoThreading(enabled: Boolean) = apply {
            autoThreading = enabled
        }

        fun setShowFormatter(enabled: Boolean) = apply {
            showFormatter = enabled
        }

        fun setThread(count: Int) = apply {
            threadCount = count
        }

        fun build(): TurboDownloader {
            if (threadCount <= 0)
                throw IllegalArgumentException("Thread count must be greater than 0")

            if (threadCount > 16)
                throw IllegalArgumentException("Thread count cannot exceed 16")

            return TurboDownloader(
                context = context.applicationContext,
                threadCount = threadCount,
                showFormatter = showFormatter,
                autoThreading = autoThreading
            )
        }
    }
}