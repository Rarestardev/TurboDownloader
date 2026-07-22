package com.rarestardev.turbodownloader.listener

import com.rarestardev.turbodownloader.state.DownloadId

interface DownloadNotificationListener {
    fun onNotificationClick(downloadId: DownloadId)
}