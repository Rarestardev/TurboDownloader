package com.rarestardev.turbodownloader.model

data class DownloadProgress(
    val totalBytes: Long,
    val downloadBytes: Long,
    val speedBytesPerSec: Long = 0L,
    val remainingTimeMillis: Long = 0L,
    val status: String = "running"
){
    val percent : Int
        get() = if (totalBytes <= 0) 0 else ((downloadBytes * 100) / totalBytes).toInt()
}
