package com.rarestardev.turbodownloader.torrent

import android.util.Log
import com.rarestardev.turbodownloader.limit.SpeedLimiter
import com.rarestardev.turbodownloader.utils.TurboConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Downloads pieces from ONE peer until the pool is complete, the pool is
 * exhausted, or the peer session dies. Multiple instances run in parallel
 * (one per peer) sharing the same [PiecePool], output file and speed limiter.
 */
internal class TorrentPieceDownloader(
    private val peer: PeerAddress,
    private val metadata: TorrentMetadata,
    private val infoHash: ByteArray,
    private val peerId: ByteArray,
    private val pool: PiecePool,
    private val outputFile: RandomAccessFile,
    private val fileLock: Any,
    private val limiter: SpeedLimiter,
    private val onBytesDownloaded: (Int) -> Unit,
    private val onPieceVerified: (Int) -> Unit,
    private val shouldStop: () -> Boolean,
    private val pipelineDepth: Int = PIPELINE_DEPTH
) {
    companion object {
        private val TAG = TurboConstants.TURBO_DOWNLOADER_LOG
        const val PIPELINE_DEPTH = 5
    }

    /** Runs the session. Returns true when it ended for a "normal" reason. */
    suspend fun run(): Boolean = withContext(Dispatchers.IO) {
        val connection = TorrentPeerConnection(peer, infoHash, peerId)
        try {
            if (shouldStop()) return@withContext true
            if (!connection.connect()) return@withContext false
            if (!connection.sendInterestedAndWaitUnchoke()) {
                Log.d(TAG, "Peer $peer closed before unchoke")
                return@withContext false
            }
            Log.d(TAG, "Peer $peer unchoked, piece loop started")

            while (true) {
                if (shouldStop()) return@withContext true
                when (val claim = pool.claim()) {
                    is PiecePool.Claim.Piece -> {
                        val ok = downloadPiece(connection, claim.index)
                        if (!ok) return@withContext false
                    }
                    PiecePool.Claim.Wait -> delay(1_500)
                    PiecePool.Claim.Done -> return@withContext true
                    PiecePool.Claim.Exhausted -> return@withContext false
                }
            }
            @Suppress("UNREACHABLE_CODE")
            true
        } catch (e: IOException) {
            Log.d(TAG, "Peer $peer session ended: ${e.message}")
            false
        } finally {
            connection.close()
        }
    }

    /**
     * Downloads one full piece with a small request pipeline.
     * Returns false when the session must end (choke, disconnect, stop flag).
     * On a hash mismatch the piece is only returned to the pool.
     */
    private suspend fun downloadPiece(connection: TorrentPeerConnection, index: Int): Boolean {
        val pieceLength = metadata.pieceLengthAt(index)
        val buffer = ByteArray(pieceLength)
        val blockCount = (pieceLength + TorrentPeerConnection.BLOCK_SIZE - 1) /
                TorrentPeerConnection.BLOCK_SIZE
        val received = BooleanArray(blockCount)
        var receivedCount = 0
        var requested = 0

        while (receivedCount < blockCount) {
            if (shouldStop()) {
                pool.release(index)
                return false
            }

            // keep the pipeline filled
            while (requested < blockCount && requested - receivedCount < pipelineDepth) {
                val begin = requested * TorrentPeerConnection.BLOCK_SIZE
                val length = minOf(TorrentPeerConnection.BLOCK_SIZE, pieceLength - begin)
                connection.sendRequest(index, begin, length)
                requested++
            }

            val message = try {
                connection.readMessage()
            } catch (e: IOException) {
                pool.release(index)
                throw e
            } ?: continue // keep-alive

            val (id, payload) = message
            when (id) {
                TorrentPeerConnection.MSG_PIECE -> {
                    if (payload.size < 8) throw IOException("Piece message too short")
                    val pieceIndex = readInt(payload, 0)
                    val begin = readInt(payload, 4)
                    if (pieceIndex != index) continue // stale block from an old claim

                    val blockIndex = begin / TorrentPeerConnection.BLOCK_SIZE
                    if (blockIndex !in received.indices || received[blockIndex]) continue

                    System.arraycopy(
                        payload, 8, buffer, begin,
                        minOf(payload.size - 8, pieceLength - begin)
                    )
                    received[blockIndex] = true
                    receivedCount++
                    onBytesDownloaded(payload.size - 8)
                    limiter.acquire(payload.size - 8)
                }
                TorrentPeerConnection.MSG_CHOKE -> {
                    pool.release(index)
                    throw IOException("Peer $peer choked mid-piece")
                }
                TorrentPeerConnection.MSG_EXTENDED, TorrentPeerConnection.MSG_HAVE,
                TorrentPeerConnection.MSG_BITFIELD, TorrentPeerConnection.MSG_UNCHOKE,
                TorrentPeerConnection.MSG_CANCEL -> Unit
                else -> Unit
            }
        }

        // verify + persist
        val expected = metadata.pieceHashes.getOrNull(index)
        val actual = MessageDigest.getInstance("SHA-1").digest(buffer)
        if (expected == null || !actual.contentEquals(expected)) {
            Log.w(TAG, "Piece $index from $peer failed SHA-1 verification, retrying elsewhere")
            pool.release(index)
            return true
        }

        synchronized(fileLock) {
            outputFile.seek(index.toLong() * metadata.pieceLength)
            outputFile.write(buffer, 0, pieceLength)
        }
        pool.complete(index)
        onPieceVerified(index)
        return true
    }

    private fun readInt(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 24) or
                ((data[offset + 1].toInt() and 0xFF) shl 16) or
                ((data[offset + 2].toInt() and 0xFF) shl 8) or
                (data[offset + 3].toInt() and 0xFF)
}
