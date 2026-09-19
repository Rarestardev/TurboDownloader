package com.rarestardev.turbodownloader.torrent

import android.util.Log
import com.rarestardev.turbodownloader.utils.TurboConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * A single BitTorrent peer TCP session implementing the peer wire protocol
 * (BEP-3) with the extension handshake (BEP-10) and the metadata exchange
 * (BEP-9) used to resolve magnet links.
 *
 * All methods must be called from a coroutine; blocking socket IO runs on
 * [Dispatchers.IO]. Not thread safe: one session is driven by one coroutine.
 */
internal class TorrentPeerConnection(
    private val peer: PeerAddress,
    private val infoHash: ByteArray,
    private val peerId: ByteArray
) {
    companion object {
        private val TAG = TurboConstants.TURBO_DOWNLOADER_LOG

        const val PROTOCOL = "BitTorrent protocol"
        const val BLOCK_SIZE = 16 * 1024

        const val MSG_CHOKE = 0
        const val MSG_UNCHOKE = 1
        const val MSG_INTERESTED = 2
        const val MSG_NOT_INTERESTED = 3
        const val MSG_HAVE = 4
        const val MSG_BITFIELD = 5
        const val MSG_REQUEST = 6
        const val MSG_PIECE = 7
        const val MSG_CANCEL = 8
        const val MSG_EXTENDED = 20
    }

    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null

    var peerSupportsExtensions = false
        private set
    var remoteMetadataMessageId = 0
        private set
    var remoteMetadataSize = 0
        private set

    val isConnected: Boolean
        get() = socket?.isConnected == true && !socket!!.isClosed

    /** Connects and performs the BitTorrent handshake. */
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            val s = Socket()
            s.connect(InetSocketAddress(peer.host, peer.port), 10_000)
            s.soTimeout = 30_000
            s.tcpNoDelay = true
            socket = s
            output = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
            input = DataInputStream(BufferedInputStream(s.getInputStream()))

            sendHandshake()
            readHandshake()
        } catch (e: Exception) {
            Log.d(TAG, "Peer $peer handshake failed: ${e.message}")
            close()
            false
        }
    }

    private fun sendHandshake() {
        val reserved = ByteArray(8)
        reserved[5] = 0x10 // BEP-10 extension support

        val out = output ?: throw IOException("Not connected")
        out.writeByte(PROTOCOL.length)
        out.write(PROTOCOL.toByteArray(Charsets.ISO_8859_1))
        out.write(reserved)
        out.write(infoHash)
        out.write(peerId)
        out.flush()
    }

    private fun readHandshake(): Boolean {
        val inp = input ?: throw IOException("Not connected")
        val nameLength = inp.readUnsignedByte()
        if (nameLength != PROTOCOL.length) throw IOException("Not a BitTorrent handshake")
        val protocol = ByteArray(nameLength).also { inp.readFully(it) }
            .toString(Charsets.ISO_8859_1)
        if (protocol != PROTOCOL) throw IOException("Unknown protocol '$protocol'")

        val reserved = ByteArray(8).also { inp.readFully(it) }
        peerSupportsExtensions = (reserved[5].toInt() and 0x10) != 0

        val remoteInfoHash = ByteArray(20).also { inp.readFully(it) }
        if (!remoteInfoHash.contentEquals(infoHash)) {
            throw IOException("Info hash mismatch")
        }
        ByteArray(20).also { inp.readFully(it) } // remote peer id, unused
        return true
    }

    fun sendMessage(messageId: Int, payload: ByteArray = ByteArray(0)) {
        val out = output ?: throw IOException("Not connected")
        out.writeInt(payload.size + 1)
        out.writeByte(messageId)
        out.write(payload)
        out.flush()
    }

    fun sendRequest(index: Int, begin: Int, length: Int) {
        val payload = ByteArray(12)
        writeInt(payload, 0, index)
        writeInt(payload, 4, begin)
        writeInt(payload, 8, length)
        sendMessage(MSG_REQUEST, payload)
    }

    /**
     * Reads one peer message. Returns null for keep-alive messages.
     * Throws IOException on timeout / closed connection.
     */
    fun readMessage(): Pair<Int, ByteArray>? {
        val inp = input ?: throw IOException("Not connected")
        val length = inp.readInt()
        if (length == 0) return null // keep-alive
        if (length < 0 || length > 1_000_000) throw IOException("Invalid message length $length")
        val id = inp.readUnsignedByte()
        val payload = ByteArray(length - 1).also { inp.readFully(it) }
        return id to payload
    }

    /**
     * Sends "interested" and waits until the peer unchokes us.
     * Returns false when the connection drops while waiting.
     */
    fun sendInterestedAndWaitUnchoke(): Boolean {
        sendMessage(MSG_INTERESTED)
        while (true) {
            when (readMessage()?.first) {
                MSG_UNCHOKE -> return true
                MSG_BITFIELD, MSG_HAVE, MSG_CHOKE, MSG_EXTENDED, null -> Unit
                else -> Unit
            }
        }
    }

    private fun writeInt(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value ushr 24).toByte()
        target[offset + 1] = (value ushr 16).toByte()
        target[offset + 2] = (value ushr 8).toByte()
        target[offset + 3] = value.toByte()
    }

    fun close() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        input = null
        output = null
    }

    // ------------------------------------------------------------------
    // BEP-9 metadata exchange (resolves the info dict for magnet links)
    // ------------------------------------------------------------------

    /**
     * Performs the extension handshake and fetches the full info dict from
     * this peer. Returns the RAW info dict bytes (whose SHA-1 must equal the
     * magnet info hash) or null when the peer cannot serve metadata.
     */
    suspend fun fetchMetadata(): ByteArray? = withContext(Dispatchers.IO) {
        if (!peerSupportsExtensions) {
            Log.d(TAG, "Peer $peer does not support BEP-10 extensions")
            return@withContext null
        }

        val ourMetadataId: Byte = 2
        val handshakePayload = BencodeEncoder.encode(
            mapOf("m" to mapOf("ut_metadata" to ourMetadataId.toInt()))
        )
        sendMessage(MSG_EXTENDED, ByteArray(1) { 0 } + handshakePayload)

        // 1. wait for the remote extension handshake
        var deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            val (id, payload) = readMessage() ?: continue
            if (id != MSG_EXTENDED || payload.isEmpty() || payload[0].toInt() != 0) continue

            val dict = BencodeDecoder.decode(payload.copyOfRange(1, payload.size))
                as? Map<*, *> ?: return@withContext null
            val theirM = dict["m"] as? Map<*, *> ?: return@withContext null
            remoteMetadataMessageId = (theirM["ut_metadata"] as? Long)?.toInt() ?: 0
            remoteMetadataSize = (dict["metadata_size"] as? Long)?.toInt() ?: 0
            if (remoteMetadataMessageId == 0 || remoteMetadataSize <= 0) {
                Log.d(TAG, "Peer $peer has no metadata (ut_metadata)")
                return@withContext null
            }
            break
        }
        if (remoteMetadataSize <= 0) return@withContext null

        // 2. request all metadata pieces
        val pieceCount = (remoteMetadataSize + BLOCK_SIZE - 1) / BLOCK_SIZE
        val metadata = ByteArray(remoteMetadataSize)
        var filled = 0
        for (piece in 0 until pieceCount) {
            val requestPayload = BencodeEncoder.encode(
                mapOf("msg_type" to 0, "piece" to piece)
            )
            sendMessage(MSG_EXTENDED, ByteArray(1) { remoteMetadataMessageId.toByte() } + requestPayload)

            var dataPiece: ByteArray? = null
            deadline = System.currentTimeMillis() + 30_000
            while (System.currentTimeMillis() < deadline) {
                val (id, payload) = readMessage() ?: continue
                if (id != MSG_EXTENDED || payload.isEmpty()) continue
                if (payload[0].toInt() != remoteMetadataMessageId) continue

                val (decoded, headerLength) = BencodeDecoder.decodeWithLength(
                    payload.copyOfRange(1, payload.size)
                )
                val dict = decoded as? Map<*, *>
                val msgType = (dict?.get("msg_type") as? Long)?.toInt()
                val pieceIndex = (dict?.get("piece") as? Long)?.toInt()
                if (dict == null || msgType == null || pieceIndex != piece) continue

                when (msgType) {
                    1 -> { // data
                        val raw = payload.copyOfRange(1, payload.size)
                        dataPiece = raw.copyOfRange(headerLength, raw.size)
                    }
                    2 -> { // reject
                        Log.d(TAG, "Peer $peer rejected metadata piece $piece")
                        return@withContext null
                    }
                }
                break
            }

            dataPiece ?: return@withContext null
            val offset = piece * BLOCK_SIZE
            val copyLength = minOf(dataPiece.size, metadata.size - offset)
            if (copyLength <= 0) return@withContext null
            System.arraycopy(dataPiece, 0, metadata, offset, copyLength)
            filled += copyLength
        }

        if (filled != remoteMetadataSize) {
            Log.d(TAG, "Peer $peer delivered incomplete metadata ($filled/$remoteMetadataSize)")
            return@withContext null
        }
        metadata
    }
}

/** Tiny bencode encoder, only used for BEP-9 request payloads. */
internal object BencodeEncoder {
    fun encode(value: Any?): ByteArray = when (value) {
        is Int -> "i${value}e".toByteArray()
        is Long -> "i${value}e".toByteArray()
        is String -> "${value.toByteArray(Charsets.UTF_8).size}:${value}".toByteArray(Charsets.UTF_8)
        is ByteArray -> "${value.size}:".toByteArray() + value
        is Map<*, *> -> {
            val body = value.entries
                .sortedBy { (it.key as? String) ?: "" }
                .map { (k, v) -> encode(k.toString()) + encode(v) }
                .reduceOrNull { a, b -> a + b } ?: ByteArray(0)
            "d".toByteArray() + body + "e".toByteArray()
        }
        is List<*> -> {
            val body = value.map { encode(it) }
                .reduceOrNull { a, b -> a + b } ?: ByteArray(0)
            "l".toByteArray() + body + "e".toByteArray()
        }
        else -> throw IllegalArgumentException("Cannot bencode ${value?.javaClass}")
    }
}
