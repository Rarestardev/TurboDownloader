package com.rarestardev.turbodownloader.torrent

import android.content.Context
import android.util.Log
import com.rarestardev.turbodownloader.core.ConcurrentDownloadGate
import com.rarestardev.turbodownloader.limit.SpeedLimiter
import com.rarestardev.turbodownloader.model.DownloadProgress
import com.rarestardev.turbodownloader.state.DownloadId
import com.rarestardev.turbodownloader.state.DownloadState
import com.rarestardev.turbodownloader.state.DownloadStatus
import com.rarestardev.turbodownloader.utils.MergeChunksHelper
import com.rarestardev.turbodownloader.utils.TurboConstants
import com.rarestardev.turbodownloader.utils.TorrentUrlDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

internal sealed class TorrentSource {
    data class TorrentFileUrl(val url: String) : TorrentSource()
    data class Magnet(val magnet: MagnetUri) : TorrentSource()
}

/**
 * Runs torrent downloads (magnet links and .torrent file URLs) and publishes
 * their progress as [DownloadState] so the regular TurboDownloader UI can
 * display them. Respects the shared [SpeedLimiter] and [ConcurrentDownloadGate].
 */
class TorrentDownloadManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val limiter: SpeedLimiter,
    private val gate: ConcurrentDownloadGate
) {
    companion object {
        private val TAG = TurboConstants.TURBO_DOWNLOADER_LOG

        const val MAX_CONCURRENT_PEERS = 8
        const val MAX_DISCOVERED_PEERS = 40
        const val METADATA_PEER_TRIES = 10

        private fun buildPeerId(): ByteArray {
            val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
            val random = (1..12).map { alphabet.random() }.joinToString("")
            return "-TD1001-$random".toByteArray(Charsets.ISO_8859_1)
        }
    }

    private val _state = MutableStateFlow<Map<DownloadId, DownloadState>>(emptyMap())
    val state: StateFlow<Map<DownloadId, DownloadState>> = _state.asStateFlow()

    private val _titles = MutableStateFlow<Map<DownloadId, String>>(emptyMap())
    val titles: StateFlow<Map<DownloadId, String>> = _titles.asStateFlow()

    private val tasks = ConcurrentHashMap<String, Task>()
    private val jobs = ConcurrentHashMap<String, Job>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val trackerClient = TrackerClient(client)
    private val peerIdBytes = buildPeerId()

    /** Ids that belong to torrent downloads (for unified pause/cancel routing). */
    val torrentIds: Set<String> get() = tasks.keys.toSet()

    fun isTorrentDownload(id: DownloadId): Boolean = tasks.containsKey(id.value)

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Starts a torrent download from a magnet link or a .torrent file URL.
     * Returns the download id (state is published via [state]) or null when
     * the link is not a torrent link.
     */
    suspend fun start(url: String, fileName: String? = null): DownloadId? =
        withContext(Dispatchers.IO) {
            val source = when {
                TorrentUrlDetector.isMagnetLink(url) ->
                    MagnetUri.parse(url)?.let { TorrentSource.Magnet(it) }
                TorrentUrlDetector.isTorrentLink(url) ->
                    TorrentSource.TorrentFileUrl(url.trim())
                else -> null
            }

            if (source == null) {
                Log.e(TAG, "Torrent start rejected, not a magnet/.torrent link: $url")
                return@withContext null
            }
            if (source is TorrentSource.Magnet && source.magnet.trackerUrls.isEmpty()) {
                Log.e(
                    TAG,
                    "Magnet link has no HTTP trackers (tr=) and DHT is not supported: $url"
                )
                return@withContext null
            }

            val id = DownloadId(UUID.randomUUID().toString())
            val task = Task(id, source, fileName)
            tasks[id.value] = task
            _titles.value = _titles.value + (id to task.title)
            update(id, DownloadState.Queued(id))
            Log.i(TAG, "Torrent download enqueued: ${task.title}")

            val granted = gate.tryAcquire(id.value) { launchTask(task) }
            if (!granted) {
                Log.i(
                    TAG,
                    "Concurrent download limit reached (${gate.activeCount()}/${gate.maxConcurrent()}) " +
                            "- torrent queued: ${task.title}"
                )
            }
            id
        }

    fun pause(id: DownloadId) {
        val task = tasks[id.value]
        if (task == null) {
            Log.w(TAG, "pause: unknown torrent download $id")
            return
        }
        if (task.paused || task.cancelled) return
        task.paused = true
        update(
            id,
            DownloadState.Paused(
                id,
                DownloadProgress(
                    totalBytes = task.totalBytes,
                    downloadBytes = task.bytesDone.get(),
                    status = DownloadStatus.PAUSED
                )
            )
        )
        Log.i(TAG, "Torrent download paused: ${task.title}")
    }

    fun resume(id: DownloadId) {
        val task = tasks[id.value]
        if (task == null) {
            Log.w(TAG, "resume: unknown torrent download $id")
            return
        }
        if (!task.paused || task.cancelled) {
            Log.w(TAG, "resume: torrent is not paused: ${task.title}")
            return
        }
        task.paused = false
        Log.i(TAG, "Torrent download resume requested: ${task.title}")
        val granted = gate.tryAcquire(id.value) { launchTask(task) }
        if (!granted) {
            update(id, DownloadState.Queued(id))
            Log.i(
                TAG,
                "Concurrent download limit reached (${gate.activeCount()}/${gate.maxConcurrent()}) " +
                        "- torrent re-queued: ${task.title}"
            )
        }
    }

    fun cancel(id: DownloadId) {
        val task = tasks.remove(id.value)
        if (task == null) {
            Log.w(TAG, "cancel: unknown torrent download $id")
            return
        }
        task.cancelled = true
        jobs.remove(id.value)?.cancel()
        gate.cancelWaiting(id.value)
        gate.release(id.value)
        update(id, DownloadState.Cancelled(id))
        File(context.filesDir, "torrent_${id.value}").deleteRecursively()
        Log.i(TAG, "Torrent download cancelled, temp files deleted: ${task.title}")
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private inner class Task(
        val id: DownloadId,
        val source: TorrentSource,
        val requestedFileName: String?
    ) {
        @Volatile var paused = false
        @Volatile var cancelled = false
        @Volatile var totalBytes = 0L
        @Volatile var metadata: TorrentMetadata? = null
        val bytesDone = AtomicLong(0L)
        val tempDir: File get() = File(context.filesDir, "torrent_${id.value}")

        val title: String = when (source) {
            is TorrentSource.Magnet ->
                source.magnet.displayName ?: "magnet:${source.magnet.infoHashHex.take(12)}"
            is TorrentSource.TorrentFileUrl ->
                requestedFileName
                    ?: source.url.substringBefore('?').substringAfterLast('/').ifBlank { "torrent file" }
        }
    }

    private fun launchTask(task: Task) {
        val job = scope.launch(Dispatchers.IO) { runTask(task) }
        jobs[task.id.value] = job
    }

    private suspend fun runTask(task: Task) {
        val id = task.id
        try {
            if (task.cancelled) {
                // cancelled while waiting for a concurrency slot
                gate.release(id.value)
                return
            }
            if (task.paused) {
                update(
                    id,
                    DownloadState.Paused(
                        id,
                        DownloadProgress(
                            totalBytes = task.totalBytes,
                            downloadBytes = task.bytesDone.get(),
                            status = DownloadStatus.PAUSED
                        )
                    )
                )
                gate.release(id.value)
                return
            }

            update(
                id,
                DownloadState.Running(
                    id,
                    DownloadProgress(totalBytes = 0, downloadBytes = 0)
                )
            )
            Log.i(TAG, "Torrent download started: ${task.title}")

            val metadata = resolveMetadata(task) ?: return
            if (task.cancelled) return

            if (metadata.isMultiFile) {
                fail(task, "Multi-file torrents are not supported in this version")
                return
            }
            if (metadata.totalBytes <= 0 || metadata.pieceCount == 0) {
                fail(task, "Torrent metadata has no downloadable content")
                return
            }

            task.metadata = metadata
            task.totalBytes = metadata.totalBytes
            _titles.value = _titles.value + (id to (task.requestedFileName ?: metadata.displayName))
            Log.i(
                TAG,
                "Torrent metadata resolved: '${metadata.displayName}' " +
                        "size=${metadata.totalBytes} bytes, ${metadata.pieceCount} pieces, " +
                        "${metadata.announceUrls.size} tracker(s)"
            )

            val pool = PiecePool(metadata.pieceCount)
            task.tempDir.mkdirs()
            val contentFile = File(task.tempDir, "content.bin")

            val verifiedBytes = preVerifyPieces(contentFile, metadata, pool)
            if (verifiedBytes > 0) {
                task.bytesDone.set(verifiedBytes)
                Log.i(
                    TAG,
                    "Torrent resume check: $verifiedBytes/${metadata.totalBytes} bytes " +
                            "already verified on disk"
                )
            }

            Log.i(TAG, "Announcing torrent to ${metadata.announceUrls.size} tracker(s)...")
            val peers = discoverPeers(metadata)
            if (task.cancelled) return
            if (task.paused) {
                updatePaused(task)
                gate.release(id.value)
                return
            }
            if (peers.isEmpty()) {
                fail(task, "No peers found (all trackers returned nothing)")
                return
            }
            Log.i(TAG, "Torrent '${metadata.displayName}': ${peers.size} peers discovered")

            val outputFile = RandomAccessFile(contentFile, "rw")
            val fileLock = Any()
            val progressJob = scope.launch { progressLoop(task) }

            try {
                val peerSemaphore = Semaphore(MAX_CONCURRENT_PEERS)
                coroutineScope {
                    peers.map { peer ->
                        async {
                            peerSemaphore.withPermit {
                                TorrentPieceDownloader(
                                    peer = peer,
                                    metadata = metadata,
                                    infoHash = metadata.infoHashBytes,
                                    peerId = peerIdBytes,
                                    pool = pool,
                                    outputFile = outputFile,
                                    fileLock = fileLock,
                                    limiter = limiter,
                                    onBytesDownloaded = { task.bytesDone.addAndGet(it.toLong()) },
                                    onPieceVerified = {
                                        val done = pool.completedCount()
                                        if (done % 25 == 0 || pool.isComplete()) {
                                            Log.i(
                                                TAG,
                                                "Torrent '${metadata.displayName}': " +
                                                        "$done/${metadata.pieceCount} pieces verified"
                                            )
                                        }
                                    },
                                    shouldStop = { task.cancelled || task.paused }
                                ).run()
                            }
                        }
                    }.awaitAll()
                }
            } finally {
                progressJob.cancel()
                runCatching { outputFile.close() }
            }

            if (task.cancelled) return
            if (task.paused) {
                updatePaused(task)
                gate.release(id.value)
                return
            }
            if (!pool.isComplete()) {
                fail(
                    task,
                    if (pool.exhausted) {
                        "Pieces failed SHA-1 verification too many times"
                    } else {
                        "Not enough reachable peers to complete the torrent"
                    }
                )
                return
            }

            val finalName = sanitizeFileName(task.requestedFileName ?: metadata.displayName)
            val uri = MergeChunksHelper.saveSingleFileToDownloads(
                context = context,
                sourceFile = contentFile,
                fileName = finalName,
                mimeType = mimeTypeFor(finalName)
            )
            if (uri == null) {
                fail(task, "Could not save the completed file to public Downloads")
                return
            }

            contentFile.delete()
            update(id, DownloadState.Completed(id, uri))
            Log.i(TAG, "Torrent download completed: $finalName")
            gate.release(id.value)
        } catch (e: Exception) {
            when {
                task.cancelled -> Unit // cancel() already published the final state
                task.paused -> {
                    updatePaused(task)
                    gate.release(id.value)
                }
                else -> fail(task, "Torrent download error: ${e.message}")
            }
        } finally {
            jobs.remove(id.value)
        }
    }

    private suspend fun resolveMetadata(task: Task): TorrentMetadata? =
        when (val source = task.source) {
            is TorrentSource.TorrentFileUrl -> {
                Log.i(TAG, "Fetching .torrent file: ${source.url}")
                val bytes = fetchTorrentFile(source.url)
                val metadata = bytes?.let { TorrentMetadata.fromTorrentFile(it) }
                if (metadata == null) {
                    fail(task, "Invalid or unsupported .torrent file: ${source.url}")
                    null
                } else {
                    metadata
                }
            }
            is TorrentSource.Magnet -> {
                Log.i(
                    TAG,
                    "Resolving magnet metadata (info hash ${source.magnet.infoHashHex}) " +
                            "via ${source.magnet.trackerUrls.size} tracker(s)"
                )
                val metadata = resolveMagnetMetadata(source.magnet, task)
                if (metadata == null) {
                    fail(
                        task,
                        "Could not fetch torrent metadata from peers for magnet " +
                                "${source.magnet.infoHashHex}"
                    )
                    null
                } else {
                    metadata
                }
            }
        }

    private suspend fun fetchTorrentFile(url: String): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val request = okhttp3.Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Fetching .torrent failed with HTTP ${response.code}")
                        return@withContext null
                    }
                    response.body.bytes()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fetching .torrent failed: ${e.message}")
                null
            }
        }

    private suspend fun resolveMagnetMetadata(magnet: MagnetUri, task: Task): TorrentMetadata? {
        val infoHash = hexToBytes(magnet.infoHashHex) ?: return null
        val peers = discoverPeers(magnet.trackerUrls, infoHash, left = 0)
        if (peers.isEmpty()) {
            Log.e(TAG, "Magnet resolving failed: no peers from ${magnet.trackerUrls}")
            return null
        }
        Log.i(TAG, "Magnet: ${peers.size} peers discovered, requesting metadata (BEP-9)...")

        for (peer in peers.take(METADATA_PEER_TRIES)) {
            if (task.cancelled || task.paused) return null
            val connection = TorrentPeerConnection(peer, infoHash, peerIdBytes)
            try {
                if (!connection.connect()) continue
                val infoBytes = connection.fetchMetadata() ?: continue
                val hash = MessageDigest.getInstance("SHA-1").digest(infoBytes)
                if (!hash.contentEquals(infoHash)) {
                    Log.w(TAG, "Peer $peer metadata info-hash mismatch, trying next peer")
                    continue
                }
                val metadata = TorrentMetadata.fromInfoDictBytes(infoBytes, magnet.trackerUrls)
                if (metadata != null) {
                    Log.i(TAG, "Magnet metadata received from peer $peer")
                    return metadata
                }
            } catch (e: Exception) {
                Log.d(TAG, "Metadata fetch from $peer failed: ${e.message}")
            } finally {
                connection.close()
            }
        }
        return null
    }

    private suspend fun discoverPeers(metadata: TorrentMetadata): List<PeerAddress> =
        discoverPeers(metadata.announceUrls, metadata.infoHashBytes, metadata.totalBytes)

    private suspend fun discoverPeers(
        announceUrls: List<String>,
        infoHash: ByteArray,
        left: Long
    ): List<PeerAddress> {
        val peers = LinkedHashMap<String, PeerAddress>()
        for (tracker in announceUrls) {
            val fromTracker = trackerClient.announce(tracker, infoHash, peerIdBytes, left)
            fromTracker.forEach { peers["${it.host}:${it.port}"] = it }
            if (peers.size >= MAX_DISCOVERED_PEERS) break
        }
        return peers.values.take(MAX_DISCOVERED_PEERS)
    }

    /** Re-verifies pieces already present on disk from a previous run. */
    private fun preVerifyPieces(
        contentFile: File,
        metadata: TorrentMetadata,
        pool: PiecePool
    ): Long {
        if (!contentFile.exists() || contentFile.length() == 0L) return 0L
        var verifiedBytes = 0L
        try {
            RandomAccessFile(contentFile, "r").use { reader ->
                for (index in 0 until metadata.pieceCount) {
                    val length = metadata.pieceLengthAt(index)
                    val start = index.toLong() * metadata.pieceLength
                    if (reader.length() < start + length) break

                    val buffer = ByteArray(length)
                    reader.seek(start)
                    reader.readFully(buffer)
                    val hash = MessageDigest.getInstance("SHA-1").digest(buffer)
                    if (hash.contentEquals(metadata.pieceHashes[index])) {
                        pool.markDone(index)
                        verifiedBytes += length
                    } else {
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Piece pre-verification failed: ${e.message}")
        }
        return verifiedBytes
    }

    private suspend fun progressLoop(task: Task) {
        var lastTime = System.currentTimeMillis()
        var lastBytes = task.bytesDone.get()
        while (true) {
            delay(500)
            val metadata = task.metadata ?: continue
            val total = metadata.totalBytes
            val bytes = task.bytesDone.get().coerceIn(0, total)
            val now = System.currentTimeMillis()
            val elapsed = now - lastTime
            val speed = if (elapsed > 0) (bytes - lastBytes) * 1000 / elapsed else 0L
            val remainBytes = total - bytes
            val remainTime = if (speed > 0 && remainBytes > 0) remainBytes * 1000 / speed else 0L
            lastTime = now
            lastBytes = bytes
            update(
                task.id,
                DownloadState.Running(
                    task.id,
                    DownloadProgress(
                        totalBytes = total,
                        downloadBytes = bytes,
                        speedBytesPerSec = speed.coerceAtLeast(0),
                        remainingTimeMillis = remainTime,
                        status = DownloadStatus.RUNNING
                    )
                )
            )
        }
    }

    private fun updatePaused(task: Task) {
        update(
            task.id,
            DownloadState.Paused(
                task.id,
                DownloadProgress(
                    totalBytes = task.totalBytes,
                    downloadBytes = task.bytesDone.get(),
                    status = DownloadStatus.PAUSED
                )
            )
        )
    }

    private fun fail(task: Task, message: String) {
        Log.e(TAG, "Torrent '${task.title}' failed: $message")
        update(task.id, DownloadState.Failed(task.id, IOException(message)))
        gate.release(task.id.value)
    }

    private fun update(id: DownloadId, newState: DownloadState) {
        val map = _state.value.toMutableMap()
        map[id] = newState
        _state.value = map
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { "torrent_${UUID.randomUUID()}" }

    private fun mimeTypeFor(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        return mime ?: "application/octet-stream"
    }

    private fun hexToBytes(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        return try {
            ByteArray(hex.length / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (_: Exception) {
            null
        }
    }
}
