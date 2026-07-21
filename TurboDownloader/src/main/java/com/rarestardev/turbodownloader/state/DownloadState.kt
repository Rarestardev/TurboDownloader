package com.rarestardev.turbodownloader.state

import com.rarestardev.turbodownloader.model.DownloadProgress
import java.io.File

@JvmInline
value class DownloadId(val value: String)

sealed class DownloadState {
    data class Idle(val status: DownloadStatus = DownloadStatus.IDLE) : DownloadState()
    data class Queued(val id: DownloadId, val status: DownloadStatus = DownloadStatus.QUEUED) :
        DownloadState()

    data class Running(
        val id: DownloadId,
        val progress: DownloadProgress,
        val status: DownloadStatus = DownloadStatus.RUNNING
    ) : DownloadState()

    data class Paused(
        val id: DownloadId,
        val progress: DownloadProgress,
        val status: DownloadStatus = DownloadStatus.PAUSED
    ) : DownloadState()

    data class Completed(
        val id: DownloadId,
        val file: File,
        val status: DownloadStatus = DownloadStatus.COMPLETED
    ) : DownloadState()

    data class Failed(
        val id: DownloadId,
        val error: Throwable?,
        val status: DownloadStatus = DownloadStatus.FAILED
    ) : DownloadState()

    data class Cancelled(
        val id: DownloadId,
        val status: DownloadStatus = DownloadStatus.CANCELLED
    ) : DownloadState()
}