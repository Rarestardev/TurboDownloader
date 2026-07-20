package com.rarestardev.turbodownloader.utils

import android.annotation.SuppressLint

object FormatUtils {

    @SuppressLint("DefaultLocale")
    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0 KB/s"

        val kb = bytesPerSec / 1024.0
        val mb = kb / 1024.0

        return when {
            mb >= 1 -> String.format("%.1f MB/s", mb)
            kb >= 1 -> String.format("%.0f KB/s", kb)
            else -> "$bytesPerSec B/s"
        }
    }

    fun formatEta(seconds: Long): String {
        if (seconds < 0) return "--"
        if (seconds < 60) return "${seconds}s"

        val minutes = seconds / 60
        val sec = seconds % 60

        if (minutes < 60) {
            return "${minutes}m ${sec}s"
        }

        val hours = minutes / 60
        val min = minutes % 60

        return "${hours}h ${min}m"
    }
}