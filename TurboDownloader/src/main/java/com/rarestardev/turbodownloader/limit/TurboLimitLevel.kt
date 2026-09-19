package com.rarestardev.turbodownloader.limit

/**
 * Three-level download speed limit.
 *
 * The level describes the STRENGTH of the limitation:
 * - [OFF]    -> no speed limit (default)
 * - [LOW]    -> light limitation, fastest allowed speed
 * - [MEDIUM] -> medium limitation
 * - [HIGH]   -> strongest limitation, lowest allowed speed
 *
 * The limit is shared across every active download (HTTP + torrent)
 * and can be changed at runtime with
 * [com.rarestardev.turbodownloader.core.TurboDownloader.setSpeedLimit].
 */
enum class TurboLimitLevel(val bytesPerSecond: Long) {
    OFF(0L),
    LOW(2L * 1024 * 1024),      // 2 MB/s
    MEDIUM(512L * 1024),        // 512 KB/s
    HIGH(128L * 1024);          // 128 KB/s

    val isLimited: Boolean
        get() = bytesPerSecond > 0
}
