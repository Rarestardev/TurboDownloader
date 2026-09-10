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

    private var _sessionManager: SessionManager? = null
    private val sessionManager get() = _sessionManager!!
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pollingJobs = mutableMapOf<DownloadId, Job>()
    private val torrentHashes = mutableMapOf<DownloadId, Sha1Hash>()

    /**
     * Flag used for testing to verify if the torrent engine has been initialized.
     */
    internal var isInitialized: Boolean = false
        private set

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
            when (alert.type()) {
                AlertType.TORRENT_FINISHED -> {
                    if (alert is TorrentFinishedAlert) {
                        Log.i(TurboConstants.TURBO_DOWNLOADER_LOG, "Torrent finished: ${alert.handle().status().name()}")
                    }
                }
                AlertType.METADATA_RECEIVED -> {
                    Log.i(TurboConstants.TURBO_DOWNLOADER_LOG, "Metadata received for torrent")
                }
                AlertType.DHT_BOOTSTRAP -> {
                    Log.i(TurboConstants.TURBO_DOWNLOADER_LOG, "DHT bootstrapped successfully.")
                }
                AlertType.TRACKER_REPLY -> {
                    Log.d(TurboConstants.TURBO_DOWNLOADER_LOG, "Tracker reply: ${alert.message()}")
                }
                AlertType.TRACKER_ERROR -> {
                    Log.w(TurboConstants.TURBO_DOWNLOADER_LOG, "Tracker error: ${alert.message()}")
                }
                else -> {}
            }
        }
    }

    /**
     * Starts the torrent download process.
     * This method is called if torrent support is enabled in the TurboDownloader settings.
     */
    fun initTorrentDownload() {
        if (isInitialized) return

        try {
            Log.d(TurboConstants.TURBO_DOWNLOADER_LOG, "Torrent download engine initializing...")
            _sessionManager = SessionManager()

            val settings = SettingsPack()
            settings.setEnableDht(true)
            settings.setEnableLsd(true)

            // Enable UPnP and NAT-PMP safely using swig values
            settings.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), true)
            settings.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), true)

            settings.setDhtBootstrapNodes("router.bittorrent.com:6881,router.utorrent.com:6881,dht.transmissionbt.com:6881,router.bitcomet.com:6881,dht.aelitis.com:6881")

            sessionManager.addListener(alertListener)
            sessionManager.start(SessionParams(settings))
            isInitialized = true
            Log.i(TurboConstants.TURBO_DOWNLOADER_LOG, "Torrent engine initialized successfully.")
        } catch (e: Throwable) {
            Log.e(TurboConstants.TURBO_DOWNLOADER_LOG, "Failed to initialize Torrent Engine", e)
            isInitialized = false
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
            
        } catch (e: Exception) {
            Log.e(TurboConstants.TURBO_DOWNLOADER_LOG, "Failed to start torrent download", e)
            downloadManager.update(id, DownloadState.Failed(id, e))
        }
    }

    private fun startPolling(id: DownloadId, infoHash: Sha1Hash, downloadManager: DownloadManager) {
        pollingJobs[id]?.cancel()
        pollingJobs[id] = scope.launch {
            while (isActive) {
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
            val handle = sessionManager.find(infoHash)
            if (handle != null && handle.isValid) {
                sessionManager.remove(handle)
            }
        }
        torrentHashes.remove(id)
    }

    /**
     * Stops the torrent engine and releases resources.
     */
    fun stop() {
        if (isInitialized) {
            pollingJobs.values.forEach { it.cancel() }
            pollingJobs.clear()
            sessionManager.removeListener(alertListener)
            sessionManager.stop()
            _sessionManager = null
            isInitialized = false
        }
    }
}
