package com.rarestardev.turbodownloader.core

import android.content.Context
import android.net.Uri
import android.util.Log
import com.rarestardev.turbodownloader.model.DownloadProgress
import com.rarestardev.turbodownloader.state.DownloadId
import com.rarestardev.turbodownloader.state.DownloadState
import com.rarestardev.turbodownloader.state.DownloadStatus
import com.rarestardev.turbodownloader.utils.TurboConstants
import kotlinx.coroutines.*
import org.libtorrent4j.*
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.TorrentFinishedAlert
import org.libtorrent4j.swig.settings_pack
import org.libtorrent4j.swig.torrent_flags_t
import java.io.File

/**
 * Manager for Torrent downloads using libtorrent4j.
 * This class is responsible for initializing and controlling the torrent download process.
 *
 * @property context The application context to access resources.
 */
class TorrentManager(private val context: Context) {

    private val sessionManager get() = TorrentEngine.sessionManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pollingJobs = mutableMapOf<DownloadId, Job>()
    private val torrentHashes = mutableMapOf<DownloadId, Sha1Hash>()

    /**
     * Flag used for testing to verify if the torrent engine has been initialized.
     */
    internal val isInitialized: Boolean get() = TorrentEngine.isInitialized

    private val alertListener = object : AlertListener {
        override fun types(): IntArray = intArrayOf(
            AlertType.TORRENT_FINISHED.swig(),
            AlertType.METADATA_RECEIVED.swig(),
            AlertType.DHT_BOOTSTRAP.swig(),
            AlertType.TRACKER_REPLY.swig(),
            AlertType.TRACKER_ERROR.swig(),
            AlertType.PEER_DISCONNECTED.swig()
        )

        override fun alert(alert: Alert<*>) {
            try {
                when (alert.type()) {
                    AlertType.TORRENT_FINISHED -> {
                        if (alert is TorrentFinishedAlert) {
                            Log.i(TurboConstants.TURBO_DOWNLOADER_LOG, "Torrent finished: ${alert.handle()?.status()?.name() ?: "unknown"}")
                        }
                    }
                    AlertType.METADATA_RECEIVED -> {
                        Log.i(TurboConstants.TURBO_DOWNLOADER_LOG, "Metadata received for torrent")
                    }
                    AlertType.DHT_BOOTSTRAP -> {
                        Log.i(TurboConstants.TURBO_DOWNLOADER_LOG, "DHT bootstrapped successfully.")
                    }
                    AlertType.TRACKER_REPLY -> {
                        Log.d(TurboConstants.TURBO_DOWNLOADER_LOG, "Tracker reply: ${alert.message() ?: ""}")
                    }
                    AlertType.TRACKER_ERROR -> {
                        Log.w(TurboConstants.TURBO_DOWNLOADER_LOG, "Tracker error: ${alert.message() ?: ""}")
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                Log.e(TurboConstants.TURBO_DOWNLOADER_LOG, "Error in Torrent AlertListener", e)
            }
        }
    }

    /**
     * Starts the torrent download process.
     * This method is called if torrent support is enabled in the TurboDownloader settings.
     */
    fun initTorrentDownload() {
        if (!TorrentEngine.isInitialized) {
            TorrentEngine.initialize()
            sessionManager.addListener(alertListener)
        }
    }

    /**
     * Enqueues a torrent download via a magnet link or a .torrent file path.
     * @param id The download ID for tracking.
     * @param uri The magnet link or path to the .torrent file.
     * @param saveDir The directory where the downloaded files will be saved.
     * @param downloadManager The manager to report progress to.
     */
    fun download(id: DownloadId, uri: String, saveDir: File, downloadManager: DownloadManager) {
        if (!isInitialized) initTorrentDownload()
        
        Log.d(TurboConstants.TURBO_DOWNLOADER_LOG, "Starting torrent download: $id, uri: $uri")
        
        try {
            val infoHash: Sha1Hash
            if (uri.startsWith("magnet:")) {
                val params = AddTorrentParams.parseMagnetUri(uri)
                infoHash = params.infoHashes.getV1()
                sessionManager.download(uri, saveDir, torrent_flags_t())
            } else {
                val torrentFile = File(uri)
                if (!torrentFile.exists()) throw Exception("Torrent file not found: $uri")
                val ti = TorrentInfo(torrentFile)
                infoHash = ti.infoHash()
                sessionManager.download(ti, saveDir)
            }
            
            torrentHashes[id] = infoHash
            startPolling(id, infoHash, downloadManager)
            
        } catch (e: Throwable) {
            Log.e(TurboConstants.TURBO_DOWNLOADER_LOG, "Failed to start torrent download", e)
            downloadManager.update(id, DownloadState.Failed(id, Exception(e.message)))
        }
    }

    private fun startPolling(id: DownloadId, infoHash: Sha1Hash, downloadManager: DownloadManager) {
        pollingJobs[id]?.cancel()
        pollingJobs[id] = scope.launch {
            while (isActive) {
                try {
                    val handle = sessionManager.find(infoHash)
                    if (handle != null && handle.isValid) {
                        val status = handle.status()
                        val totalBytes = if (status.total() > 0) status.total() else 0L
                        val downloaded = status.totalDone()
                        val speed = status.downloadPayloadRate().toLong()
                        val peers = status.numPeers()
                        
                        val downloadStatus = when {
                            status.state() == TorrentStatus.State.SEEDING -> DownloadStatus.SEEDING
                            status.isFinished -> DownloadStatus.COMPLETED
                            else -> DownloadStatus.RUNNING
                        }

                        if (downloadStatus == DownloadStatus.COMPLETED) {
                            Log.i(TurboConstants.TURBO_DOWNLOADER_LOG, "Torrent $id completed and moved to seeding")
                            downloadManager.updateDownloadStatusInternal(id, DownloadStatus.COMPLETED)
                            downloadManager.update(id, DownloadState.Completed(id, Uri.fromFile(File(handle.savePath(), status.name()))))
                            break
                        }

                        // Update filename and size once metadata is available
                        if (status.hasMetadata() && totalBytes > 0) {
                            downloadManager.updateDownloadInfoInternal(id, status.name(), totalBytes)
                        }

                        Log.v(TurboConstants.TURBO_DOWNLOADER_LOG, "Torrent $id: $downloaded / $totalBytes bytes, Speed: $speed, Peers: $peers")

                        downloadManager.update(id, DownloadState.Running(
                            id, 
                            DownloadProgress(
                                totalBytes = totalSizeOrUnknown(totalBytes),
                                downloadBytes = downloaded,
                                speedBytesPerSec = speed,
                                status = downloadStatus
                            )
                        ))
                    }
                } catch (e: Exception) {
                    Log.e(TurboConstants.TURBO_DOWNLOADER_LOG, "Error polling torrent $id", e)
                }
                delay(1000)
            }
        }
    }

    private fun totalSizeOrUnknown(size: Long): Long {
        return if (size <= 0) -1L else size
    }

    /**
     * Removes a specific torrent from the session.
     * @param id The download ID of the torrent to remove.
     */
    fun removeTorrent(id: DownloadId) {
        pollingJobs[id]?.cancel()
        pollingJobs.remove(id)
        
        val infoHash = torrentHashes[id]
        if (infoHash != null && isInitialized) {
            try {
                val handle = sessionManager.find(infoHash)
                if (handle != null && handle.isValid) {
                    sessionManager.remove(handle)
                }
            } catch (e: Exception) {
                Log.e(TurboConstants.TURBO_DOWNLOADER_LOG, "Error removing torrent $id", e)
            }
        }
        torrentHashes.remove(id)
    }

    /**
     * Stops the torrent engine and releases resources.
     */
    fun stop() {
        pollingJobs.values.forEach { it.cancel() }
        pollingJobs.clear()
        try {
            sessionManager.removeListener(alertListener)
        } catch (_: Exception) {}
        TorrentEngine.stop()
    }
}
