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
import java.io.FileOutputStream

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
        val chunks = dao.getChunks(download.id).ifEmpty {
            createChunks(download)
        }

        val tempDir = File(download.destinationDir, "${download.id}_tmp")
        tempDir.mkdirs()


        coroutineScope {
            chunks.map { chunk ->
                async(Dispatchers.IO) {
                    downloadChunk(download.url, chunk, tempDir, onProgress, isPaused)
                }
            }.awaitAll()
        }

        val latestChunks = dao.getChunks(download.id)

        if (latestChunks.any { !it.isCompleted }) {
            return File("")
        }

        val outputFile = File(download.destinationDir, download.fileName)
        mergeChunks(chunks, tempDir, outputFile)
        tempDir.deleteRecursively()

        return outputFile
    }

    private suspend fun createChunks(download: DownloadEntity): List<ChunkEntity> {
        val chunkSize = download.totalBytes / download.chunkCount

        val chunks = (0 until download.chunkCount).map { index ->
            val start = index * chunkSize
            val end =
                if (index == download.chunkCount - 1) download.totalBytes - 1 else (start + chunkSize - 1)

            ChunkEntity(
                downloadId = download.id,
                index = index,
                start = start,
                end = end,
                downloaded = 0,
                isCompleted = false
            )
        }

        chunks.forEach { dao.insertChunk(it) }
        return chunks
    }

    private suspend fun downloadChunk(
        url: String,
        chunk: ChunkEntity,
        outputDir: File,
        onProgress: (Long) -> Unit,
        isPaused: () -> Boolean
    ) {
        if (chunk.isCompleted) return

        var current = chunk
        val chunkFile = File(outputDir, "chunk_${current.index}.part")

//        val startByte = current.start + current.downloaded

        if (chunkFile.exists()) {
            val realDownloaded = chunkFile.length()

            if (realDownloaded != current.downloaded) {
                current = current.copy(downloaded = realDownloaded)
                dao.insertChunk(current)
            }
        }

        val startByte = current.start + current.downloaded
        val request = Request.Builder()
            .url(url)
//            .addHeader("Range", "bytes=$startByte-${current.end}")
            .addHeader("Range", "bytes=$startByte-${current.end}")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body
            FileOutputStream(chunkFile, true).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {

                        if (isPaused()) {
                            dao.insertChunk(current)
                            return
                        }

                        output.write(buffer, 0, read)
                        current = current.copy(downloaded = current.downloaded + read)
                        dao.insertChunk(current)
                        onProgress(read.toLong())
                    }
                }
            }
        }

        dao.insertChunk(current.copy(isCompleted = true))
    }

    private fun mergeChunks(chunks: List<ChunkEntity>, tempDir: File, outputFile: File) {
        outputFile.outputStream().use { output ->
            chunks.sortedBy { it.index }.forEach { chunk ->
                val chunkFile = File(tempDir, "chunk_${chunk.index}.part")
                chunkFile.inputStream().use { input ->
                    input.copyTo(output)
                }
                chunkFile.delete()
            }
        }
        tempDir.deleteRecursively()
    }
}