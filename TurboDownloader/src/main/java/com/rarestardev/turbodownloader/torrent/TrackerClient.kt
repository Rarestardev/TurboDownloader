package com.rarestardev.turbodownloader.torrent

import android.util.Log
import com.rarestardev.turbodownloader.utils.TurboConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** One torrent peer (IP + port) as returned by a tracker. */
data class PeerAddress(val host: String, val port: Int) {
    override fun toString(): String = "$host:$port"
}

/**
 * Announces to HTTP/HTTPS trackers and parses the compact peer list.
 * UDP trackers are logged as unsupported and skipped.
 */
class TrackerClient(private val client: OkHttpClient = defaultClient()) {

    companion object {
        private val TAG = TurboConstants.TURBO_DOWNLOADER_LOG

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

        internal fun parseCompactPeers(data: ByteArray): List<PeerAddress> {
            val peers = mutableListOf<PeerAddress>()
            var offset = 0
            while (offset + 6 <= data.size) {
                val ip = "${data[offset].toInt() and 0xFF}.${data[offset + 1].toInt() and 0xFF}." +
                        "${data[offset + 2].toInt() and 0xFF}.${data[offset + 3].toInt() and 0xFF}"
                val port = ((data[offset + 4].toInt() and 0xFF) shl 8) or (data[offset + 5].toInt() and 0xFF)
                if (port > 0) peers.add(PeerAddress(ip, port))
                offset += 6
            }
            return peers
        }

        internal fun pctEncode(bytes: ByteArray): String =
            bytes.joinToString("") { "%%%02X".format(it) }
    }

    suspend fun announce(
        announceUrl: String,
        infoHash: ByteArray,
        peerId: ByteArray,
        left: Long
    ): List<PeerAddress> = withContext(Dispatchers.IO) {
        if (!announceUrl.startsWith("http", ignoreCase = true)) {
            Log.w(TAG, "Tracker skipped (unsupported scheme): $announceUrl")
            return@withContext emptyList()
        }

        val separator = if (announceUrl.contains('?')) "&" else "?"
        val url = announceUrl + separator +
                "info_hash=" + pctEncode(infoHash) +
                "&peer_id=" + pctEncode(peerId) +
                "&port=6881&uploaded=0&downloaded=0&left=$left" +
                "&compact=1&numwant=50&event=started"

        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Tracker announce failed (${response.code}): $announceUrl")
                    return@withContext emptyList()
                }
                val decoded = BencodeDecoder.decode(response.body.bytes())
                val dict = decoded as? Map<*, *>
                if (dict == null) {
                    Log.w(TAG, "Tracker returned invalid bencode: $announceUrl")
                    return@withContext emptyList()
                }
                (dict["failure reason"] as? ByteArray)?.let { reason ->
                    Log.w(TAG, "Tracker failure '${reason.decodeToString()}' from $announceUrl")
                    return@withContext emptyList()
                }
                val peers = when (val raw = dict["peers"]) {
                    is ByteArray -> parseCompactPeers(raw)
                    is List<*> -> raw.mapNotNull { entry ->
                        val peer = entry as? Map<*, *> ?: return@mapNotNull null
                        val host = (peer["ip"] as? ByteArray)?.decodeToString() ?: return@mapNotNull null
                        val port = (peer["port"] as? Long)?.toInt() ?: return@mapNotNull null
                        if (port > 0) PeerAddress(host, port) else null
                    }
                    else -> {
                        Log.w(TAG, "Tracker response has no peers field: $announceUrl")
                        emptyList()
                    }
                }
                Log.d(TAG, "Tracker $announceUrl returned ${peers.size} peers")
                peers
            }
        } catch (e: Exception) {
            Log.w(TAG, "Tracker announce error for $announceUrl : ${e.message}")
            emptyList()
        }
    }
}
