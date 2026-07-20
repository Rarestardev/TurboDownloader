package com.rarestardev.turbodownloader.model

import com.rarestardev.turbodownloader.state.DownloadId
import java.io.File

sealed class DownloadState {
    object Idle : DownloadState()
    data class Queued(val id: DownloadId) : DownloadState()
    data class Running(
        val id: DownloadId,
        val progress: DownloadProgress,
        val speedBytesPerSec: Long,
        val etaSeconds: Long
    ) : DownloadState()

    data class Paused(val id: DownloadId, val progress: DownloadProgress) : DownloadState()
    data class Completed(val id: DownloadId, val file: File) : DownloadState()
    data class Failed(val id: DownloadId, val error: Throwable?) : DownloadState()
    data class Cancelled(val id: DownloadId) : DownloadState()
}