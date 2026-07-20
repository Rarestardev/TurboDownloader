package com.rarestardev.turbodownloader.model

import com.rarestardev.turbodownloader.state.DownloadId

data class Running(
    val id: DownloadId,
    val progress: DownloadProgress,
    val speedBytesPerSec: Long,
    val etaSeconds: Long
) : DownloadState()

