package com.rarestardev.turbodownloader.utils

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.rarestardev.turbodownloader.storage.ChunkEntity
import okio.use
import java.io.File

object MergeChunksHelper {
    fun mergeChunkToDownloads(
        context: Context,
        chunks: List<ChunkEntity>,
        tempDir: File,
        finalFileName: String,
        mimeType: String
    ): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // use android 10 above
            val contentValue = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, finalFileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)

                val nowSeconds = System.currentTimeMillis() / 1000
                put(MediaStore.Downloads.DATE_ADDED, nowSeconds)
                put(MediaStore.Downloads.DATE_MODIFIED, nowSeconds)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValue)

            uri?.let {
                try {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        chunks.sortedBy { sort -> sort.index }.forEach { chunk ->
                            val chunkFile = File(tempDir, "chunk_${chunk.index}.part")
                            chunkFile.inputStream().use { input ->
                                input.copyTo(outputStream)
                            }
                            chunkFile.delete() // delete when file is merged
                        }
                    }

                    contentValue.clear()
                    contentValue.put(MediaStore.Downloads.IS_PENDING, 0)
                    val finalNowSeconds = System.currentTimeMillis() / 1000
                    contentValue.put(MediaStore.Downloads.DATE_MODIFIED, finalNowSeconds)

                    resolver.update(uri, contentValue, null, null)
                    return uri
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    Log.e(TurboConstants.TURBO_DOWNLOADER_LOG, e.message ?: "")
                    return null
                }
            }
        } else {
            // use android 10  below
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(
                    TurboConstants.TURBO_DOWNLOADER_LOG,
                    "Android 10 below need permission write on external storage!"
                )
                throw SecurityException("Android 10 below need permission write on external storage!")
            }

            val downloadsDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val outputFile = File(downloadsDir, finalFileName)
            try {
                outputFile.outputStream().use { outputStream ->
                    chunks.sortedBy { sort -> sort.index }.forEach { chunk ->
                        val chunkFile = File(tempDir, "chunk_${chunk.index}.part")
                        chunkFile.inputStream().use { input ->
                            input.copyTo(outputStream)
                        }
                        chunkFile.delete()
                    }
                }

                val values = ContentValues().apply {
                    put(MediaStore.Files.FileColumns.DATA, outputFile.absolutePath)
                    put(MediaStore.Files.FileColumns.MIME_TYPE, mimeType)
                    put(MediaStore.Files.FileColumns.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Files.getContentUri("external"), values
                )
                return uri
            } catch (e: Exception) {
                outputFile.delete()
                Log.e(TurboConstants.TURBO_DOWNLOADER_LOG, e.message ?: "")
                return null
            }
        }

        return null
    }
}