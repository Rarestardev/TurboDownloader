package com.rarestardev.turbodownloader.model

data class DownloadRequest(
    val uri: String,
    val fileName: String,
    val threadCount: Int = 4,
    val autoThreading: Boolean = false
)