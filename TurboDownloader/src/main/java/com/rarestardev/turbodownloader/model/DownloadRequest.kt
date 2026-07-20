package com.rarestardev.turbodownloader.model

import java.io.File

data class DownloadRequest(
    val uri: String,
    val fileName: String,
    val destinationDir: File,
    val threadCount: Int = 4
)