package com.rarestardev.turbodownloader.service

import android.annotation.SuppressLint
import com.rarestardev.turbodownloader.core.DownloadManager

@SuppressLint("StaticFieldLeak")
object DownloadManagerHolder {
    var manager: DownloadManager? = null
}