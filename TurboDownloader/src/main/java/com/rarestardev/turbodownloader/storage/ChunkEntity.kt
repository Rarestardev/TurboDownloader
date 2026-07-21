package com.rarestardev.turbodownloader.storage

import androidx.room.Entity

@Entity(tableName = "chunks", primaryKeys = ["downloadId", "index"])
data class ChunkEntity(
    val downloadId: String,
    val index: Int,
    val start: Long,
    val end: Long,
    val downloaded: Long,
    val isCompleted: Boolean
)
