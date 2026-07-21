package com.rarestardev.turbodownloader.listener

import com.rarestardev.turbodownloader.state.DownloadId

interface DownloadNotificationListener {
    fun onNotificationClick(downloadId: DownloadId)

    fun onPauseClick(downloadId: DownloadId)

    fun onResumeClick(downloadId: DownloadId)

    fun onCancelClick(downloadId: DownloadId)
}