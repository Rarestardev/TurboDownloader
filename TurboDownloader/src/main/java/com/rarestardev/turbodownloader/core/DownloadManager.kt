package com.rarestardev.turbodownloader.core

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.rarestardev.turbodownloader.listener.TurboDownloadListener
import com.rarestardev.turbodownloader.engine.ChunkDownloader
import com.rarestardev.turbodownloader.model.DownloadProgress
import com.rarestardev.turbodownloader.model.DownloadRequest
import com.rarestardev.turbodownloader.service.DownloadService
import com.rarestardev.turbodownloader.state.DownloadId
import com.rarestardev.turbodownloader.state.DownloadState
import com.rarestardev.turbodownloader.storage.DownloadDao
import com.rarestardev.turbodownloader.storage.DownloadEntity
import com.rarestardev.turbodownloader.utils.TurboConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class DownloadManager(
    private val dao: DownloadDao,
    private val scope: CoroutineScope,
    private val context: Context
) {
    private val downloader = ChunkDownloader(dao)

    private var listener: TurboDownloadListener? = null

    fun setListener(l: TurboDownloadListener) {
        listener = l
    }

    private val _state = MutableStateFlow<Map<DownloadId, DownloadState>>(emptyMap())
    val state = _state.asStateFlow()

    private val pauseFlags = mutableMapOf<String, Boolean>()
    private val jobs = mutableMapOf<String, Job>()

    fun enqueue(request: DownloadRequest): DownloadId {
        ensureServiceRunning()
        val id = DownloadId(UUID.randomUUID().toString())

        val job = scope.launch(Dispatchers.IO) {
            val totalSize = downloader.getFileSize(request.uri)

            val entity = DownloadEntity(
                id = id.value,
                url = request.uri,
                fileName = request.fileName,
                destinationDir = request.destinationDir.absolutePath,
                totalBytes = totalSize,
                chunkCount = request.threadCount,
                status = "queued"
            )

            dao.insertDownload(entity)
            dao.updateStatus(id.value, "running")
            update(id, DownloadState.Queued(id))

            enqueueInternal(entity)
        }

        jobs[id.value] = job
        Log.d(TurboConstants.TURBO_DOWNLOADER_LOG, "queued download...")
        return id
    }

    fun resume(id: DownloadId) {
        ensureServiceRunning()
        val job = scope.launch(Dispatchers.IO) {
            val entity = dao.getDownload(id.value) ?: return@launch
            pauseFlags[id.value] = false
            dao.updateStatus(id.value, "running")
            enqueueInternal(entity)
        }
        jobs[id.value] = job
        Log.w(TurboConstants.TURBO_DOWNLOADER_LOG, "resume downloading...")
    }

    fun pause(id: DownloadId) {
        pauseFlags[id.value] = true
        scope.launch(Dispatchers.IO) {
            dao.updateStatus(id.value, "paused")
            val entity = dao.getDownload(id.value) ?: return@launch
            val downloaded = dao.getChunks(id.value).sumOf { it.downloaded }
            update(id, DownloadState.Paused(id, DownloadProgress(entity.totalBytes, downloaded)))
        }

        Log.i(TurboConstants.TURBO_DOWNLOADER_LOG, "pause downloading...")
    }

    fun cancel(id: DownloadId) {
        scope.launch {
            jobs[id.value]?.cancel()
            pauseFlags[id.value] = false
            dao.updateStatus(id.value, "cancelled")
            update(id, DownloadState.Cancelled(id))
            jobs.remove(id.value)
        }
        stopServiceIfIdle()

        Log.w(TurboConstants.TURBO_DOWNLOADER_LOG, "cancel download running.")
    }

    private fun enqueueInternal(entity: DownloadEntity) {
        val id = DownloadId(entity.id)

        val job = scope.launch(Dispatchers.IO) {
            try {
                var downloaded = dao.getChunks(id.value).sumOf { it.downloaded }

                update(
                    id,
                    DownloadState.Running(id, DownloadProgress(entity.totalBytes, downloaded))
                )

                val file = downloader.download(entity, { chunkBytes ->
                    downloaded += chunkBytes
                    update(
                        id,
                        DownloadState.Running(id, DownloadProgress(entity.totalBytes, downloaded))
                    )
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

                dao.updateStatus(id.value, "completed")
                update(id, DownloadState.Completed(id, file))
            } catch (e: Exception) {
                dao.updateStatus(id.value, "failed")
                update(id, DownloadState.Failed(id, e))
            } finally {
                jobs.remove(id.value)
            }
        }

        jobs[id.value] = job
    }

    private fun update(id: DownloadId, state: DownloadState) {
        val map = _state.value.toMutableMap()
        map[id] = state
        _state.value = map

        when(state) {
            is DownloadState.Queued -> listener?.onQueued(id)
            is DownloadState.Running -> listener?.onRunning(id, state.progress)
            is DownloadState.Paused -> listener?.onPaused(id, state.progress)
            is DownloadState.Completed -> listener?.onCompleted(id, state.file)
            is DownloadState.Failed -> listener?.onFailed(id, state.error?.message ?: "null")
            is DownloadState.Cancelled -> listener?.onCancelled(id)
        }
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
}