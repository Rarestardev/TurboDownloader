package com.rarestardev.turbodownloader.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val url: String,
    val fileName: String,
    val destinationDir: String,
    val totalBytes: Long,
    val chunkCount: Int,
    val status: String
)
