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
import java.io.File
import java.net.InetSocketAddress
import java.util.UUID
import com.rarestardev.turbodownloader.storage.DownloadEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import com.rarestardev.turbodownloader.state.DownloadStatus
import java.net.Socket
import kotlin.time.Duration.Companion.milliseconds

/**
 * Main entry point for the TurboDownloader library.
 * Provides a high-level API for starting, pausing, and managing HTTP and Torrent downloads.
 * Use the [Builder] class to create an instance.
 */
class TurboDownloader private constructor(
    private val context: Context,
    private val threadCount: Int,
    private val showFormatter: Boolean,
    private val autoThreading: Boolean
) : com.rarestardev.turbodownloader.listener.DownloadInternalListener {
    private val manager = ChunkDownloadApi.get(context)
    private var notificationListener: DownloadNotificationListener? = null
    private var connectionListener: NetworkConnectionListener? = null

    /**
     * Enables or disables torrent download capability.
     * When set to true, it initializes the torrent engine if not already initialized.
     */
    var enableTorrent: Boolean = false
        set(value) {
            field = value
            if (value) {
                manager.torrentManager.initTorrentDownload()
            } else {
                manager.torrentManager.stop()
            }
        }

    init {
        manager.internalListener = this
    }

    override fun onDownloadCancelled(id: DownloadId) {
        manager.torrentManager.removeTorrent(id)
    }

    /**
     * Starts a new download (HTTP link or Magnet/Torrent).
     * @param url The download URL or magnet link.
     * @param fileName Optional filename. If null, it will be extracted or set to a default.
     * @return The [DownloadId] if successfully started, null otherwise.
     */
    suspend fun startDownload(
        url: String,
        fileName: String? = null
    ): DownloadId? {
        if (url.startsWith("magnet:") || url.endsWith(".torrent")) {
            if (!enableTorrent) {
                Log.e(TurboConstants.TURBO_DOWNLOADER_LOG, "Torrent download requested but not enabled.")
                return null
            }
            manager.ensureServiceRunning()
            val saveDir = manager.getPublicDownloadDir()

            val id = DownloadId("torrent_${UUID.randomUUID()}")
            val name = if (url.startsWith("magnet:")) {
                "Magnet Download..."
            } else {
                url.substringAfterLast("/")
            }

            val entity = DownloadEntity(
                id = id.value,
                url = url,
                fileName = name,
                totalBytes = -1,
                chunkCount = 1,
                status = DownloadStatus.RUNNING,
                isTorrent = true
            )
            manager.insertDownloadInternal(entity)

            manager.torrentManager.download(id, url, saveDir, manager)
            return id
        }

        val ext = extractExtension(url)
        val finalName = fileName ?: "file_${System.currentTimeMillis()}.$ext"

        val maxRetries = 5
        val delayMs = 5000L

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

            delay(delayMs.milliseconds)
        }
        return null
    }

    suspend fun addToQueues(
        url: String,
        fileName: String? = null
    ): DownloadId {
        val ext = extractExtension(url)
        val finalName = fileName ?: "file_${System.currentTimeMillis()}.$ext"

        val request = DownloadRequest(
            uri = url,
            fileName = finalName,
            threadCount = threadCount,
            autoThreading = autoThreading
        )

        val downloadId = manager.addToQueue(request)

        return downloadId
    }

    /**
     * Pauses a download.
     */
    fun pause(id: DownloadId) = manager.pause(id)
    
    /**
     * Resumes a download.
     */
    fun resume(id: DownloadId) = manager.resume(id)

    /**
     * Removes a download record and optionally deletes the physical file.
     * Works for both completed and active downloads.
     */
    fun removeDownloadFile(id: DownloadId, withFile: Boolean) =
        manager.removeCompleted(id, withFile)

    /**
     * Cancels an active download.
     */
    suspend fun cancel(id: DownloadId) = manager.cancel(id)
    
    /**
     * Releases library resources. Background downloads will continue.
     */
    fun release() = manager.release()
    
    /**
     * Returns a [StateFlow] of current download states.
     */
    fun downloadState() = manager.state
    
    /**
     * Returns a [Flow] of all downloads in history.
     */
    fun getAllDownloads() = manager.allDownloads()
    
    internal val isTorrentInitialized: Boolean get() = manager.torrentManager.isInitialized

    /**
     * Returns a [Flow] of queued downloads.
     */
    fun queuedListDownload() = manager.allQueuedList()

    /**
     * Formats speed bytes to a human-readable string.
     */
    fun formatSpeed(speed: Long): String {
        return if (showFormatter) {
            FormatUtils.formatSpeed(speed)
        } else {
            speed.toString()
        }
    }

    /**
     * Formats ETA milliseconds to a human-readable string.
     */
    fun formatEta(eta: Long): String {
        return if (showFormatter) {
            FormatUtils.formatEta(eta)
        } else {
            eta.toString()
        }
    }

    /**
     * Sets a listener for notification interactions.
     */
    fun setNotificationListener(listener: DownloadNotificationListener) {
        this.notificationListener = listener
        ChunkDownloadApi.setNotificationListener(listener)
    }

    /**
     * Sets a listener for network connectivity events.
     */
    fun setNetworkConnectionListener(listener: NetworkConnectionListener) {
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

    /**
     * Checks if the app has permission to post notifications.
     */
    fun hasNotificationPermission(): Boolean {
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

    /**
     * Builder class for configuring and creating a [TurboDownloader] instance.
     */
    class Builder(private val context: Context) {
        private var threadCount: Int = 4
        private var showFormatter: Boolean = false
        private var autoThreading: Boolean = false

        /**
         * Enables automatic adjustment of thread count based on file size.
         */
        fun setAutoThreading(enabled: Boolean) = apply {
            autoThreading = enabled
        }

        /**
         * Enables built-in formatting for speed and ETA strings.
         */
        fun setShowFormatter(enabled: Boolean) = apply {
            showFormatter = enabled
        }

        /**
         * Sets the number of concurrent threads for HTTP downloads.
         */
        fun setThread(count: Int) = apply {
            threadCount = count
        }

        /**
         * Builds the [TurboDownloader] instance.
         */
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
