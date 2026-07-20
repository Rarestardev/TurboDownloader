package com.rarestardev.turbodownloader.core

import android.content.Context
import com.rarestardev.turbodownloader.api.ChunkDownloadApi
import com.rarestardev.turbodownloader.model.DownloadRequest
import com.rarestardev.turbodownloader.state.DownloadId
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

    class Builder(private val context: Context) {
        private var threadCount: Int = 4
        private var destinationDir: File? = null

        fun setThread(count: Int) = apply {
            threadCount = count
        }

        fun setDir(dir: File) = apply {
            destinationDir = dir
        }

        fun build(): TurboDownloader {
            val dir = destinationDir
                ?: throw IllegalStateException("Destination directory is required")

            return TurboDownloader(
                context = context.applicationContext,
                threadCount = threadCount,
                destinationDir = dir
            )
        }
    }
}