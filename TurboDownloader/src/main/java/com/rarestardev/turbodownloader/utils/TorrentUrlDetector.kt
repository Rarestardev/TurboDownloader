package com.rarestardev.turbodownloader.utils

import java.util.Locale

/**
 * Detects torrent links (magnet URIs and .torrent file URLs).
 * Pure Kotlin so it can be unit tested on the JVM.
 */
object TorrentUrlDetector {

    fun isTorrentLink(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val trimmed = url.trim()
        if (trimmed.startsWith("magnet:", ignoreCase = true)) return true

        val path = trimmed.substringBefore('?').substringBefore('#')
        val fileName = path.substringAfterLast('/', "")
        return fileName.lowercase(Locale.ROOT).endsWith(".torrent")
    }

    fun isMagnetLink(url: String?): Boolean =
        !url.isNullOrBlank() && url.trim().startsWith("magnet:", ignoreCase = true)
}
