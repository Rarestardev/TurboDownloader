package com.rarestardev.turbodownloader.listener

import com.rarestardev.turbodownloader.state.DownloadId

internal interface DownloadInternalListener {
    fun onDownloadCancelled(id: DownloadId)
}
