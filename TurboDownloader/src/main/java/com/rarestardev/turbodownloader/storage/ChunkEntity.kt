package com.rarestardev.turbodownloader.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chunks")
data class ChunkEntity(
    @PrimaryKey(autoGenerate = true) val chunkId: Long = 0,
    val downloadId: String,
    val index: Int,
    val start: Long,
    val end: Long,
    val downloaded: Long,
    val isCompleted: Boolean
)
