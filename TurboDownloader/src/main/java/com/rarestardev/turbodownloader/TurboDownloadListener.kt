package com.rarestardev.turbodownloader

import com.rarestardev.turbodownloader.model.DownloadProgress
import com.rarestardev.turbodownloader.state.DownloadId
import java.io.File

interface TurboDownloadListener {
    fun onQueued(id: DownloadId)
    fun onRunning(id: DownloadId, progress: DownloadProgress)
    fun onPaused(id: DownloadId, progress: DownloadProgress)
    fun onCompleted(id: DownloadId, file: File)
    fun onFailed(id: DownloadId, error: Throwable)
    fun onCancelled(id: DownloadId)
}