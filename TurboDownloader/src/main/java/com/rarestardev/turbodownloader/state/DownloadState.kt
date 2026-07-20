package com.rarestardev.turbodownloader.state

import com.rarestardev.turbodownloader.model.DownloadProgress
import java.io.File

@JvmInline
value class DownloadId(val value: String)

sealed class DownloadState {
    object Idle : DownloadState()
    data class Queued(val id: DownloadId) : DownloadState()
    data class Running(
        val id: DownloadId,
        val progress: DownloadProgress
    ) : DownloadState()

    data class Paused(val id: DownloadId, val progress: DownloadProgress) : DownloadState()
    data class Completed(val id: DownloadId, val file: File) : DownloadState()
    data class Failed(val id: DownloadId, val error: Throwable?) : DownloadState()
    data class Cancelled(val id: DownloadId) : DownloadState()
}