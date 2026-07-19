package com.rarestardev.turbodownloader.model

import java.io.File

data class DownloadRequest(
    val uri: String,
    val fileName: String,
    val destinationFile: File,
    val chunkCount: Int = 4
)