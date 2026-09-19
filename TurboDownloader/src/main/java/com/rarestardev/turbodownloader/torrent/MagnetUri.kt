package com.rarestardev.turbodownloader.torrent

import java.net.URLDecoder

/**
 * Parsed magnet: URI (pure Kotlin, JVM unit-testable).
 *
 * Supported fields: xt (info hash, hex or base32), dn (display name),
 * tr (tracker URLs, repeated). x.pe / DHT parameters are ignored.
 */
data class MagnetUri(
    val infoHashHex: String,
    val displayName: String?,
    val trackerUrls: List<String>
) {
    companion object {
        private val HEX_40 = Regex("^[0-9a-fA-F]{40}$")
        private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

        fun parse(uri: String): MagnetUri? {
            if (!uri.startsWith("magnet:?", ignoreCase = true)) return null

            var hash: String? = null
            val names = mutableListOf<String>()
            val trackers = mutableListOf<String>()

            uri.substringAfter('?').split('&').forEach { param ->
                val eq = param.indexOf('=')
                if (eq <= 0) return@forEach
                val key = param.substring(0, eq).lowercase()
                val value = param.substring(eq + 1)
                when (key) {
                    "xt" -> {
                        if (hash == null && value.startsWith("urn:btih:", ignoreCase = true)) {
                            hash = normalizeInfoHash(value.substring("urn:btih:".length))
                        }
                    }
                    "dn" -> URLDecoder.decode(value, "UTF-8")
                        .takeIf { it.isNotBlank() }
                        ?.let { names.add(it) }
                    "tr" -> URLDecoder.decode(value, "UTF-8")
                        .takeIf { it.isNotBlank() }
                        ?.let { trackers.add(it) }
                }
            }

            val infoHash = hash ?: return null
            return MagnetUri(
                infoHashHex = infoHash,
                displayName = names.firstOrNull(),
                trackerUrls = trackers.distinct()
            )
        }

        private fun normalizeInfoHash(raw: String): String? = when {
            HEX_40.matches(raw) -> raw.lowercase()
            raw.length == 32 && raw.all { it.uppercaseChar() in BASE32_ALPHABET } ->
                base32DecodeToHex(raw)
            else -> null
        }

        private fun base32DecodeToHex(input: String): String? {
            var buffer = 0
            var bits = 0
            val bytes = ArrayList<Byte>(20)
            for (c in input.uppercase()) {
                val value = BASE32_ALPHABET.indexOf(c)
                if (value < 0) return null
                buffer = (buffer shl 5) or value
                bits += 5
                if (bits >= 8) {
                    bytes.add(((buffer shr (bits - 8)) and 0xFF).toByte())
                    bits -= 8
                }
            }
            if (bytes.size != 20) return null
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
