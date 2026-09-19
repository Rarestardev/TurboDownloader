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
import com.rarestardev.turbodownloader.limit.TurboLimitLevel
import com.rarestardev.turbodownloader.model.DownloadRequest
import com.rarestardev.turbodownloader.schedule.DownloadScheduler
import com.rarestardev.turbodownloader.schedule.ScheduledDownload
import com.rarestardev.turbodownloader.state.DownloadId
import com.rarestardev.turbodownloader.state.DownloadState
import com.rarestardev.turbodownloader.utils.FormatUtils
import com.rarestardev.turbodownloader.utils.TurboConstants
import com.rarestardev.turbodownloader.utils.TorrentUrlDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class TurboDownloader private constructor(
    private val context: Context,
    private val threadCount: Int,
    private val showFormatter: Boolean,
    private val autoThreading: Boolean,
    torrentEnabled: Boolean,
    speedLimit: TurboLimitLevel,
    maxConcurrentDownloads: Int
) {
    private val manager = ChunkDownloadApi.get(context)
    private val torrents = ChunkDownloadApi.getTorrentManager(context)
    private var notificationListener: DownloadNotificationListener? = null
    private var connectionListener: NetworkConnectionListener? = null

    /** The torrent ON/OFF switch. */
    @Volatile
    var torrentEnabled: Boolean = torrentEnabled
        private set

    private val schedulerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val scheduler = DownloadScheduler(schedulerScope) { item ->
        Log.i(
            TurboConstants.TURBO_DOWNLOADER_LOG,
            "Scheduled time reached (${item.triggerAtMillis}) - starting download: ${item.url}"
        )
        startDownload(item.url, item.fileName)
    }

    init {
        manager.setSpeedLimit(speedLimit)
        manager.setMaxConcurrentDownloads(maxConcurrentDownloads)
        Log.i(
            TurboConstants.TURBO_DOWNLOADER_LOG,
            "TurboDownloader initialized: threads=$threadCount, torrent=${if (torrentEnabled) "ON" else "OFF"}, " +
                    "speedLimit=$speedLimit, maxConcurrent=$maxConcurrentDownloads"
        )
    }

    // ------------------------------------------------------------------
    // Downloads (HTTP + torrent routing)
    // ------------------------------------------------------------------

    /**
     * Starts a download. Magnet links and .torrent URLs are routed to the
     * torrent engine when the torrent switch is ON; anything else goes to
     * the regular multi-thread HTTP engine.
     */
    suspend fun startDownload(
        url: String,
        fileName: String? = null
    ): DownloadId? {
        val isTorrentLink = TorrentUrlDetector.isTorrentLink(url)

        if (isTorrentLink && !torrentEnabled) {
            Log.e(
                TurboConstants.TURBO_DOWNLOADER_LOG,
                "Torrent download is DISABLED (torrent switch is OFF). " +
                        "Enable it with Builder.setTorrentEnabled(true) or setTorrentEnabled(true)."
            )
            return null
        }

        val maxRetries = 5
        val delayMs = 5000L

        for (attempt in 1..maxRetries) {
            if (isInternetAvailable()) {
                connectionListener?.onInternetAvailable()
                return if (isTorrentLink) {
                    Log.i(
                        TurboConstants.TURBO_DOWNLOADER_LOG,
                        "Torrent switch is ON - routing to torrent engine: $url"
                    )
                    torrents.start(url, fileName)
                } else {
                    val request = DownloadRequest(
                        uri = url,
                        fileName = fileName ?: defaultFileName(url),
                        threadCount = threadCount,
                        autoThreading = autoThreading
                    )
                    manager.enqueue(request)
                }
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
        return null
    }

    fun pause(id: DownloadId) {
        if (torrents.isTorrentDownload(id)) torrents.pause(id) else manager.pause(id)
    }

    fun resume(id: DownloadId) {
        if (torrents.isTorrentDownload(id)) torrents.resume(id) else manager.resume(id)
    }

    fun removeDownloadFile(id: DownloadId, withFile: Boolean) =
        manager.removeCompleted(id, withFile)

    suspend fun cancel(id: DownloadId) {
        if (torrents.isTorrentDownload(id)) torrents.cancel(id) else manager.cancel(id)
    }

    fun release() {
        schedulerScope.cancel()
        Log.d(
            TurboConstants.TURBO_DOWNLOADER_LOG,
            "TurboDownloader released (${scheduler.pendingCount()} pending schedules discarded)"
        )
        manager.release()
    }

    fun downloadState() = manager.state

    /** Live state of torrent downloads (magnet / .torrent). */
    fun torrentState(): StateFlow<Map<DownloadId, DownloadState>> = torrents.state

    /** Display names of torrent downloads keyed by download id. */
    fun torrentTitles(): StateFlow<Map<DownloadId, String>> = torrents.titles

    fun getAllDownloads() = manager.allDownloads()

    // ------------------------------------------------------------------
    // Scheduling
    // ------------------------------------------------------------------

    /**
     * Schedules a download to start at an absolute time
     * ([triggerAtMillis] = epoch milliseconds). The schedule lives while
     * the app process lives.
     */
    fun scheduleDownload(
        url: String,
        fileName: String? = null,
        triggerAtMillis: Long
    ): ScheduledDownload {
        Log.i(
            TurboConstants.TURBO_DOWNLOADER_LOG,
            "Download scheduled at $triggerAtMillis (epoch ms) for: $url"
        )
        return scheduler.schedule(url, fileName, triggerAtMillis)
    }

    /** Schedules a download to start after [delayMillis] from now. */
    fun scheduleDownloadAfter(
        url: String,
        fileName: String? = null,
        delayMillis: Long
    ): ScheduledDownload {
        Log.i(
            TurboConstants.TURBO_DOWNLOADER_LOG,
            "Download scheduled in $delayMillis ms for: $url"
        )
        return scheduler.scheduleAfter(url, fileName, delayMillis)
    }

    /** Cancels a pending schedule. Returns false when the id is unknown/already fired. */
    fun cancelScheduled(scheduleId: String): Boolean {
        val cancelled = scheduler.cancel(scheduleId)
        if (cancelled) {
            Log.i(TurboConstants.TURBO_DOWNLOADER_LOG, "Scheduled download cancelled: $scheduleId")
        } else {
            Log.w(
                TurboConstants.TURBO_DOWNLOADER_LOG,
                "cancelScheduled: schedule id not found or already fired: $scheduleId"
            )
        }
        return cancelled
    }

    /** All pending schedules ordered by trigger time. */
    fun getScheduledDownloads(): List<ScheduledDownload> = scheduler.scheduled()

    // ------------------------------------------------------------------
    // Settings (runtime versions of the Builder options)
    // ------------------------------------------------------------------

    /** Torrent ON/OFF switch (same option as [Builder.setTorrentEnabled]). */
    fun setTorrentEnabled(enabled: Boolean) {
        torrentEnabled = enabled
        Log.i(
            TurboConstants.TURBO_DOWNLOADER_LOG,
            "Torrent download switch turned ${if (enabled) "ON" else "OFF"}"
        )
    }

    /** Three-level speed limit: OFF / LOW / MEDIUM / HIGH. */
    fun setSpeedLimit(level: TurboLimitLevel) {
        manager.setSpeedLimit(level)
    }

    fun speedLimit(): TurboLimitLevel = manager.speedLimit()

    /** Max number of downloads running at the same time (HTTP + torrent). */
    fun setMaxConcurrentDownloads(count: Int) {
        manager.setMaxConcurrentDownloads(count)
    }

    fun maxConcurrentDownloads(): Int = manager.maxConcurrentDownloads()

    fun activeDownloadCount(): Int = manager.activeDownloadCount()

    fun queuedDownloadCount(): Int = manager.queuedDownloadCount()

    // ------------------------------------------------------------------
    // Formatting / listeners / helpers
    // ------------------------------------------------------------------

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

    fun setNetworkConnectionListener(listener: NetworkConnectionListener) {
        this.connectionListener = listener
    }

    private fun defaultFileName(url: String): String {
        val ext = extractExtension(url)
        return "file_${System.currentTimeMillis()}.$ext"
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

    class Builder(private val context: Context) {
        private var threadCount: Int = 4
        private var showFormatter: Boolean = false
        private var autoThreading: Boolean = false
        private var torrentEnabled: Boolean = false
        private var speedLimit: TurboLimitLevel = TurboLimitLevel.OFF
        private var maxConcurrentDownloads: Int = ConcurrentDownloadGate.DEFAULT_MAX_CONCURRENT_DOWNLOADS

        fun setAutoThreading(enabled: Boolean) = apply {
            autoThreading = enabled
        }

        fun setShowFormatter(enabled: Boolean) = apply {
            showFormatter = enabled
        }

        fun setThread(count: Int) = apply {
            threadCount = count
        }

        /**
         * The torrent ON/OFF switch. When ON, magnet links and .torrent URLs
         * are downloaded by the torrent engine; when OFF they are rejected
         * with a clear log message.
         */
        fun setTorrentEnabled(enabled: Boolean) = apply {
            torrentEnabled = enabled
        }

        /** Three-level speed limit (OFF / LOW / MEDIUM / HIGH). */
        fun setSpeedLimit(level: TurboLimitLevel) = apply {
            speedLimit = level
        }

        /** Max number of downloads running at the same time (default 3). */
        fun setMaxConcurrentDownloads(count: Int) = apply {
            maxConcurrentDownloads = count
        }

        fun build(): TurboDownloader {
            if (threadCount <= 0)
                throw IllegalArgumentException("Thread count must be greater than 0")

            if (threadCount > 16)
                throw IllegalArgumentException("Thread count cannot exceed 16")

            if (maxConcurrentDownloads <= 0)
                throw IllegalArgumentException("Max concurrent downloads must be at least 1")

            return TurboDownloader(
                context = context.applicationContext,
                threadCount = threadCount,
                showFormatter = showFormatter,
                autoThreading = autoThreading,
                torrentEnabled = torrentEnabled,
                speedLimit = speedLimit,
                maxConcurrentDownloads = maxConcurrentDownloads
            )
        }
    }
}
