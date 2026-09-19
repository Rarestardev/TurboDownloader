package com.rarestardev.turbodownloader.torrent

import java.security.MessageDigest

/**
 * Parsed metadata of a torrent: name, size, trackers and piece hashes.
 *
 * Created either from raw .torrent file bytes ([fromTorrentFile]) or from the
 * raw info-dict bytes fetched over BEP-9 for magnet links ([fromInfoDictBytes]).
 */
class TorrentMetadata(
    val infoHashBytes: ByteArray,
    /** SHA-1 of the info dict, lowercase hex. */
    val infoHashHex: String,
    val announceUrls: List<String>,
    val displayName: String,
    /** -1 when the info dict has no length/files. */
    val totalBytes: Long,
    val pieceLength: Int,
    val pieceHashes: List<ByteArray>,
    val isMultiFile: Boolean
) {
    val pieceCount: Int get() = pieceHashes.size

    fun pieceLengthAt(index: Int): Int {
        if (index !in pieceHashes.indices) {
            throw IndexOutOfBoundsException("Piece index $index out of range 0..${pieceHashes.lastIndex}")
        }
        if (index == pieceHashes.lastIndex) {
            val last = (totalBytes - (pieceHashes.size - 1).toLong() * pieceLength).toInt()
            return if (last in 1..pieceLength) last else pieceLength
        }
        return pieceLength
    }

    companion object {
        private const val PIECE_HASH_SIZE = 20

        fun fromTorrentFile(torrentBytes: ByteArray): TorrentMetadata? {
            return try {
                val root = BencodeDecoder.decode(torrentBytes) as? Map<*, *> ?: return null
                val infoBytes = BencodeDecoder.extractInfoDictBytes(torrentBytes) ?: return null
                val info = root["info"] as? Map<*, *> ?: return null

                val announces = readAnnounceUrls(root)
                fromInfoMap(info, infoBytes, announces)
            } catch (_: Exception) {
                null
            }
        }

        /** Magnet path: the info dict bytes were fetched from peers (BEP-9). */
        fun fromInfoDictBytes(infoBytes: ByteArray, announceUrls: List<String>): TorrentMetadata? {
            return try {
                val info = BencodeDecoder.decode(infoBytes) as? Map<*, *> ?: return null
                fromInfoMap(info, infoBytes, announceUrls)
            } catch (_: Exception) {
                null
            }
        }

        private fun fromInfoMap(
            info: Map<*, *>,
            infoBytes: ByteArray,
            announces: List<String>
        ): TorrentMetadata? {
            val pieceLength = (info["piece length"] as? Long)?.takeIf { it > 0 } ?: return null
            val piecesRaw = info["pieces"] as? ByteArray ?: return null
            if (piecesRaw.isEmpty() || piecesRaw.size % PIECE_HASH_SIZE != 0) return null

            val files = info["files"] as? List<*>
            val totalBytes = if (files != null) {
                files.sumOf { entry -> (entry as? Map<*, *>)?.get("length") as? Long ?: 0L }
            } else {
                info["length"] as? Long ?: -1L
            }

            val name = (info["name.utf-8"] as? ByteArray)?.decodeToString()
                ?: (info["name"] as? ByteArray)?.decodeToString()
                ?: "torrent"

            return TorrentMetadata(
                infoHashBytes = MessageDigest.getInstance("SHA-1").digest(infoBytes),
                infoHashHex = MessageDigest.getInstance("SHA-1")
                    .digest(infoBytes)
                    .joinToString("") { "%02x".format(it) },
                announceUrls = announces,
                displayName = name,
                totalBytes = totalBytes,
                pieceLength = pieceLength.toInt(),
                pieceHashes = piecesRaw.toList().chunked(PIECE_HASH_SIZE).map { it.toByteArray() },
                isMultiFile = files != null
            )
        }

        private fun readAnnounceUrls(root: Map<*, *>): List<String> {
            val result = mutableListOf<String>()
            (root["announce"] as? ByteArray)?.let { result.add(it.decodeToString()) }
            (root["announce-list"] as? List<*>)?.forEach { tier ->
                (tier as? List<*>)?.forEach { url ->
                    (url as? ByteArray)?.let { result.add(it.decodeToString()) }
                }
            }
            return result.filter { it.isNotBlank() }.distinct()
        }
    }
}
