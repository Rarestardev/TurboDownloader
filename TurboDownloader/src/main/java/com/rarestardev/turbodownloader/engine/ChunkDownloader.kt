package com.rarestardev.turbodownloader.engine

import com.rarestardev.turbodownloader.storage.ChunkEntity
import com.rarestardev.turbodownloader.storage.DownloadDao
import com.rarestardev.turbodownloader.storage.DownloadEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class ChunkDownloader(
    private val dao: DownloadDao,
    private val client: OkHttpClient = OkHttpClient()
) {

    suspend fun getFileSize(url: String): Long {
        val request = Request.Builder().url(url).head().build()
        client.newCall(request).execute().use { response ->
            return response.header("Content-Length")?.toLong() ?: -1
        }
    }

    suspend fun download(
        download: DownloadEntity,
        onProgress: (Long) -> Unit,
        isPaused: () -> Boolean
    ): File {
        val chunks = dao.getChunk(download.id).ifEmpty {
            createChunks(download)
        }

        val tempDir = File(download.destinationDir,"${download.fileName}_chunk").apply {
            if (!exists()) mkdirs()
        }

        coroutineScope {
            chunks.map { chunk ->
                async(Dispatchers.IO) {
                    downloadChunk(download.url,chunk,tempDir,onProgress,isPaused)
                }
            }.awaitAll()
        }

        val outputFile = File(download.destinationDir,download.fileName)
        mergeChunks(chunks,tempDir,outputFile)
        tempDir.deleteRecursively()

        return outputFile
    }

    private fun createChunks(download: DownloadEntity): List<ChunkEntity> {
        TODO("Not yet implemented")
    }

    private fun downloadChunk(
        url: String,
        chunk: ChunkEntity,
        tempDir: File,
        onProgress: (Long) -> Unit,
        paused: () -> Boolean
    ) {
        TODO("Not yet implemented")
    }

    private fun mergeChunks(
        chunks: List<ChunkEntity>,
        tempDir: File,
        outputFile: File
    ) {
        TODO("Not yet implemented")
    }
}