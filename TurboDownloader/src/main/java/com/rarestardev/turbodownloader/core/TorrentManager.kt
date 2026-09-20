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
/**
 * Manager for Torrent downloads using libtorrent4j.
 * This class is responsible for initializing and controlling the torrent download process,
 * including polling for progress updates and managing peer connectivity.
 *
 * @property context The application context to access resources and storage.
 */
class TorrentManager(private val context: Context) {

    private val sessionManager get() = TorrentEngine.sessionManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pollingJobs = mutableMapOf<DownloadId, Job>()
    private val torrentHashes = mutableMapOf<DownloadId, Sha1Hash>()

    /**
     * Flag used to verify if the torrent engine has been initialized.
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
     * Starts the torrent engine process.
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
     * 
     * @param id The unique download ID for tracking.
     * @param uri The magnet link or path to the .torrent file.
     * @param saveDir The directory where the downloaded files will be saved.
     * @param downloadManager The manager to report progress and state updates to.
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
                        
                        // If the handle is paused, stop reporting RUNNING/CONNECTING status
                        if (status.flags().and_(TorrentFlags.PAUSED).non_zero()) {
                            val totalBytes = if (status.total() > 0) status.total() else 0L
                            val downloaded = status.totalDone()
                            downloadManager.update(id, DownloadState.Paused(
                                id, 
                                DownloadProgress(
                                    totalBytes = totalSizeOrUnknown(totalBytes),
                                    downloadBytes = downloaded,
                                    status = DownloadStatus.PAUSED
                                )
                            ))
                            delay(2000)
                            continue
                        }

                        val totalBytes = if (status.total() > 0) status.total() else 0L
                        val downloaded = status.totalDone()
                        val speed = status.downloadPayloadRate().toLong()
                        val peers = status.numPeers()
                        
                        var downloadStatus = when {
                            status.state() == TorrentStatus.State.SEEDING -> DownloadStatus.SEEDING
                            status.isFinished -> DownloadStatus.COMPLETED
                            else -> DownloadStatus.RUNNING
                        }

                        // If we are "Running" but have 0 peers or no metadata, mark as "Connecting"
                        if (downloadStatus == DownloadStatus.RUNNING && (peers == 0 || !status.hasMetadata())) {
                            downloadStatus = DownloadStatus.CONNECTING
                        }

                        if (downloadStatus == DownloadStatus.COMPLETED) {
                            Log.i(TurboConstants.TURBO_DOWNLOADER_LOG, "Torrent $id completed and moved to seeding")
                            downloadManager.updateDownloadStatusInternal(id, DownloadStatus.COMPLETED)
                            downloadManager.update(id, DownloadState.Completed(id, Uri.fromFile(File(handle.savePath(), status.name()))))
                            break
                        }

                        // Update status in DB if it changed
                        downloadManager.updateDownloadStatusInternal(id, downloadStatus)

                        // Update filename and size once metadata is available
                        if (status.hasMetadata() && totalBytes > 0) {
                            downloadManager.updateDownloadInfoInternal(id, status.name(), totalBytes)
                        }

                        Log.v(TurboConstants.TURBO_DOWNLOADER_LOG, "Torrent $id: $downloaded / $totalBytes bytes, Speed: $speed, Peers: $peers, Status: $downloadStatus")

                        downloadManager.update(id, DownloadState.Running(
                            id, 
                            DownloadProgress(
                                totalBytes = totalSizeOrUnknown(totalBytes),
                                downloadBytes = downloaded,
                                speedBytesPerSec = speed,
                                status = downloadStatus,
                                peers = peers
                            ),
                            status = downloadStatus
                        ))
                    }
                } catch (e: Exception) {
                    Log.e(TurboConstants.TURBO_DOWNLOADER_LOG, "Error polling torrent $id", e)
                }
                delay(1000)
            }
        }
    }

    /**
     * Pauses the torrent download.
     * @param id The download ID of the torrent to pause.
     */
    fun pauseTorrent(id: DownloadId) {
        val infoHash = torrentHashes[id]
        if (infoHash != null && isInitialized) {
            try {
                val handle = sessionManager.find(infoHash)
                if (handle != null && handle.isValid) {
                    handle.pause()
                    Log.i(TurboConstants.TURBO_DOWNLOADER_LOG, "Paused torrent handle for $id")
                }
            } catch (e: Exception) {
                Log.e(TurboConstants.TURBO_DOWNLOADER_LOG, "Error pausing torrent $id", e)
            }
        }
    }

    /**
     * Resumes the torrent download.
     * @param id The download ID of the torrent to resume.
     */
    fun resumeTorrent(id: DownloadId) {
        val infoHash = torrentHashes[id]
        if (infoHash != null && isInitialized) {
            try {
                val handle = sessionManager.find(infoHash)
                if (handle != null && handle.isValid) {
                    handle.resume()
                    Log.i(TurboConstants.TURBO_DOWNLOADER_LOG, "Resumed torrent handle for $id")
                }
            } catch (e: Exception) {
                Log.e(TurboConstants.TURBO_DOWNLOADER_LOG, "Error resuming torrent $id", e)
            }
        }
    }

    private fun totalSizeOrUnknown(size: Long): Long {
        return if (size <= 0) -1L else size
    }

    /**
     * Removes a specific torrent from the session and stops its polling job.
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
     * Stops the torrent engine, removes all listeners, and releases session resources.
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
