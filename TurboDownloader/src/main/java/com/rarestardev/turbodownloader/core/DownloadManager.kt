package com.rarestardev.turbodownloader.core

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.rarestardev.turbodownloader.engine.ChunkDownloader
import com.rarestardev.turbodownloader.model.DownloadProgress
import com.rarestardev.turbodownloader.model.DownloadRequest
import com.rarestardev.turbodownloader.service.DownloadService
import com.rarestardev.turbodownloader.state.DownloadId
import com.rarestardev.turbodownloader.state.DownloadState
import com.rarestardev.turbodownloader.state.DownloadStatus
import com.rarestardev.turbodownloader.storage.DownloadDao
import com.rarestardev.turbodownloader.storage.DownloadEntity
import com.rarestardev.turbodownloader.utils.TurboConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class DownloadManager(
    private val dao: DownloadDao,
    private val scope: CoroutineScope,
    private val context: Context
) {
    private val downloader = ChunkDownloader(dao, context)
    private val _state = MutableStateFlow<Map<DownloadId, DownloadState>>(emptyMap())
    val state = _state.asStateFlow()
    private val pauseFlags = mutableMapOf<String, Boolean>()
    private val jobs = mutableMapOf<String, Job>()

    fun enqueue(request: DownloadRequest): DownloadId {
        ensureServiceRunning()
        val id = DownloadId(UUID.randomUUID().toString())

        val job = scope.launch(Dispatchers.IO) {
            val totalSize = downloader.getFileSize(request.uri)

            val actualThreadCount = if (request.autoThreading && totalSize > 0) {
                computeAutoThreadCount(totalSize)
            } else {
                request.threadCount
            }

            val entity = DownloadEntity(
                id = id.value,
                url = request.uri,
                fileName = request.fileName,
                totalBytes = totalSize,
                chunkCount = actualThreadCount,
                status = DownloadStatus.QUEUED
            )

            Log.d(
                TurboConstants.TURBO_DOWNLOADER_LOG,
                "Used thread for downloads  = ThreadCount : $actualThreadCount , FileSize : $totalSize"
            )
            dao.insertDownload(entity)
            dao.updateStatus(id.value, DownloadStatus.RUNNING.name)
            update(id, DownloadState.Queued(id))

            executeDownload(entity)
        }

        jobs[id.value] = job
        Log.d(TurboConstants.TURBO_DOWNLOADER_LOG, "queued download...")
        return id
    }

    fun resume(id: DownloadId) {
        ensureServiceRunning()
        val job = scope.launch(Dispatchers.IO) {
            jobs.remove(id.value)

            pauseFlags[id.value] = false
            val entity = dao.getDownload(id.value) ?: return@launch
            dao.updateStatus(id.value, DownloadStatus.RUNNING.name)
            executeDownload(entity)
        }

        jobs[id.value] = job
        Log.i(TurboConstants.TURBO_DOWNLOADER_LOG, "resume downloading...")
    }

    fun pause(id: DownloadId) {
        pauseFlags[id.value] = true
        scope.launch(Dispatchers.IO) {
            dao.updateStatus(id.value, DownloadStatus.PAUSED.name)
            val entity = dao.getDownload(id.value) ?: return@launch
            val downloaded = dao.getChunks(id.value).sumOf { it.downloaded }
            /*update(id, DownloadState.Paused(id, DownloadProgress(entity.totalBytes, downloaded)))*/
            update(
                id,
                DownloadState.Paused(
                    id,
                    DownloadProgress(
                        totalBytes = entity.totalBytes,
                        downloadBytes = downloaded,
                        status = DownloadStatus.PAUSED
                    )
                )
            )
        }

        Log.i(TurboConstants.TURBO_DOWNLOADER_LOG, "pause downloading...")
    }

    fun cancel(id: DownloadId) {
        scope.launch(Dispatchers.IO) {
            jobs[id.value]?.cancel()
            jobs.remove(id.value)
            pauseFlags[id.value] = false

            update(
                id,
                DownloadState.Cancelled(id)
            )

            dao.deleteChunks(id.value)
            dao.deleteDownload(id.value)

            val tempDir = File(context.filesDir, "chunks_${id.value}")
            if (tempDir.exists()) tempDir.deleteRecursively()

            stopServiceIfIdle()
        }

        Log.w(
            TurboConstants.TURBO_DOWNLOADER_LOG,
            "download cancelled and delete file and chunk"
        )
    }

    private fun enqueueInternal(entity: DownloadEntity) {
        val id = DownloadId(entity.id)

        val job = scope.launch(Dispatchers.IO) {
            try {
                var downloaded = dao.getChunks(id.value).sumOf { it.downloaded }
                var lastByte = downloaded
                var lastTime = System.currentTimeMillis()

                update(
                    id,
                    DownloadState.Running(id, DownloadProgress(entity.totalBytes, downloaded))
                )


                val file = downloader.download(entity, onProgress = { chunkBytes ->
                    downloaded += chunkBytes

                    val now = System.currentTimeMillis()
                    val elapsed = now - lastTime

                    if (elapsed >= 1000) {
                        val speed = ((downloaded - lastByte) * 1000L) / elapsed
                        val remainBytes = entity.totalBytes - downloaded
                        val remainTime = if (speed > 0) remainBytes * 1000L / speed else 0L

                        lastByte = downloaded
                        lastTime = now

                        update(
                            id = id,
                            state = DownloadState.Running(
                                id, DownloadProgress(
                                    totalBytes = entity.totalBytes,
                                    downloadBytes = downloaded,
                                    speedBytesPerSec = speed,
                                    remainingTimeMillis = remainTime,
                                    status = DownloadStatus.RUNNING
                                )
                            )
                        )
                    }
                }) {
                    pauseFlags[id.value] == true
                }
                if (pauseFlags[id.value] == true) {
                    update(
                        id,
                        DownloadState.Paused(id, DownloadProgress(entity.totalBytes, downloaded))
                    )
                    return@launch
                }

                dao.updateStatus(id.value, DownloadStatus.COMPLETED.name)
                update(id, DownloadState.Completed(id, file))

                Log.d(TurboConstants.TURBO_DOWNLOADER_LOG, "enqueueInternal")
            } catch (e: Exception) {
                dao.updateStatus(id.value, DownloadStatus.FAILED.name)
                update(id, DownloadState.Failed(id, e))
            } finally {
                jobs.remove(id.value)

                if (pauseFlags[id.value] != true) {
                    val tempDir = File(context.cacheDir, "${id.value}_tmp")
                    if (tempDir.exists()) tempDir.deleteRecursively()
                }
            }
        }

        jobs[id.value] = job
    }

    private fun update(id: DownloadId, state: DownloadState) {
        val map = _state.value.toMutableMap()
        map[id] = state
        _state.value = map
    }

    private fun ensureServiceRunning() {
        val intent = Intent(context, DownloadService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
        Log.i(TurboConstants.TURBO_DOWNLOADER_LOG, "ensureServiceRunning.")
    }

    private fun stopServiceIfIdle() {
        val active =
            _state.value.values.any { it is DownloadState.Running || it is DownloadState.Paused }
        if (!active) {
            val intent = Intent(context, DownloadService::class.java)
            context.stopService(intent)
        }

        Log.e(TurboConstants.TURBO_DOWNLOADER_LOG, "stop service idle.")
    }

    fun allDownloads(): Flow<List<DownloadEntity>> {
        return dao.getAllDownloads()
    }

    fun release() {
        scope.launch(Dispatchers.IO) {
            val downloads = dao.getAllDownloadsOnce()
            downloads.forEach { download ->
                if (download.status == DownloadStatus.COMPLETED || download.status == DownloadStatus.CANCELLED) {
                    val tempDir = File(context.filesDir, "chunks_${download.id}")
                    if (tempDir.exists()) tempDir.deleteRecursively()
                }
            }
        }

        Log.d(
            TurboConstants.TURBO_DOWNLOADER_LOG,
            "release completed"
        )
    }

    private suspend fun executeDownload(entity: DownloadEntity){
        val id = DownloadId(entity.id)
        val maxRetries = 3
        var currentAttempt = 1
        var downloadedBeforeRetry = dao.getChunks(id.value).sumOf { it.downloaded }

        while (currentAttempt <= maxRetries){
            if (pauseFlags[id.value] == true){
                val downloaded = dao.getChunks(id.value).sumOf { it.downloaded }
                update(id, DownloadState.Paused(id, DownloadProgress(entity.totalBytes, downloaded)))
                return
            }

            try {
                dao.updateStatus(id.value, DownloadStatus.RUNNING.name)
                update(id, DownloadState.Running(id, DownloadProgress(entity.totalBytes, downloadedBeforeRetry)))

                var downloaded = downloadedBeforeRetry
                var lastByte = downloaded
                var lastTime = System.currentTimeMillis()

                val resultUri = downloader.download(
                    entity,
                    onProgress = { chunkBytes ->
                        downloaded += chunkBytes
                        val now = System.currentTimeMillis()
                        val elapsed = now - lastTime
                        if (elapsed >= 1000) {
                            val speed = ((downloaded - lastByte) * 1000L) / elapsed
                            val remainBytes = entity.totalBytes - downloaded
                            val remainTime = if (speed > 0) remainBytes * 1000L / speed else 0L
                            lastByte = downloaded
                            lastTime = now
                            update(
                                id, DownloadState.Running(id, DownloadProgress(
                                    totalBytes = entity.totalBytes,
                                    downloadBytes = downloaded,
                                    speedBytesPerSec = speed,
                                    remainingTimeMillis = remainTime,
                                    status = DownloadStatus.RUNNING
                                ))
                            )
                        }
                    },
                    isPaused = { pauseFlags[id.value] == true }
                )

                if (pauseFlags[id.value] == true) {
                    update(id, DownloadState.Paused(id, DownloadProgress(entity.totalBytes, downloaded)))
                    return
                }

                dao.updateStatus(id.value, DownloadStatus.COMPLETED.name)
                update(id, DownloadState.Completed(id, resultUri))
                return

            } catch (e: Exception) {
                if (pauseFlags[id.value] == true) {
                    val downloaded = dao.getChunks(id.value).sumOf { it.downloaded }
                    update(id, DownloadState.Paused(id, DownloadProgress(entity.totalBytes, downloaded)))
                    return
                }
                @Suppress("DEPRECATION")
                if (!isActive) return   // cancellation

                Log.e(TurboConstants.TURBO_DOWNLOADER_LOG, "Download failed (attempt $currentAttempt/$maxRetries): ${e.message}")

                if (currentAttempt < maxRetries) {
                    if (currentAttempt < maxRetries - 1) {
                        Log.i(TurboConstants.TURBO_DOWNLOADER_LOG, "Retrying resume in 3s...")
                        delay(3000)
                        downloadedBeforeRetry = dao.getChunks(id.value).sumOf { it.downloaded }
                    } else {
                        Log.i(TurboConstants.TURBO_DOWNLOADER_LOG, "Retrying full restart in 3s...")
                        delay(3000)
                        dao.deleteChunks(id.value)
                        val tempDir = File(context.filesDir, "chunks_${entity.id}")
                        if (tempDir.exists()) tempDir.deleteRecursively()
                        downloadedBeforeRetry = 0
                    }

                    update(id, DownloadState.Running(id, DownloadProgress(entity.totalBytes, downloadedBeforeRetry)))
                    currentAttempt++
                } else {
                    dao.updateStatus(id.value, DownloadStatus.FAILED.name)
                    update(id, DownloadState.Failed(id, e))
                    val tempDir = File(context.filesDir, "chunks_${entity.id}")
                    if (tempDir.exists()) tempDir.deleteRecursively()
                    return
                }
            }
        }
    }

    private fun computeAutoThreadCount(fileSizeBytes: Long): Int {
        val mb = fileSizeBytes / (1024 * 1024)
        return when {
            mb < 50 -> 1
            mb < 100 -> 2
            mb < 200 -> 4
            mb < 800 -> 6
            mb < 1000 -> 8
            mb < 2000 -> 12
            else -> 16
        }
    }
}