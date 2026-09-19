package com.rarestardev.turbodownloader.torrent

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class TorrentMetadataTest {

    private val fakePieceHash1 = ByteArray(20) { 0x11 }
    private val fakePieceHash2 = ByteArray(20) { 0x22 }

    private val infoDict = mapOf<String, Any?>(
        "name" to "test.iso",
        "length" to 60_000L,
        "piece length" to 32_768L,
        "pieces" to fakePieceHash1 + fakePieceHash2
    )

    private val torrentBytes: ByteArray by lazy {
        BencodeEncoder.encode(
            mapOf<String, Any?>(
                "announce" to "http://tracker.example/announce",
                "announce-list" to listOf(
                    listOf("http://tracker.example/announce"),
                    listOf("udp://ignored:1337")
                ),
                "info" to infoDict
            )
        )
    }

    @Test
    fun `parses a single file torrent`() {
        val metadata = TorrentMetadata.fromTorrentFile(torrentBytes)

        assertNotNull(metadata)
        metadata!!
        assertEquals("test.iso", metadata.displayName)
        assertEquals(60_000L, metadata.totalBytes)
        assertEquals(32_768, metadata.pieceLength)
        assertEquals(2, metadata.pieceCount)
        assertFalse(metadata.isMultiFile)
        assertTrue(metadata.announceUrls.contains("http://tracker.example/announce"))
    }

    @Test
    fun `info hash equals sha1 of the raw info dict`() {
        val metadata = TorrentMetadata.fromTorrentFile(torrentBytes)!!
        val infoBytes = BencodeEncoder.encode(infoDict)
        val expected = MessageDigest.getInstance("SHA-1").digest(infoBytes)

        assertArrayEquals(expected, metadata.infoHashBytes)
        assertEquals(
            expected.joinToString("") { "%02x".format(it) },
            metadata.infoHashHex
        )
    }
    @Test
    fun `last piece length is shortened`() {
        val metadata = TorrentMetadata.fromTorrentFile(torrentBytes)!!
        assertEquals(32_768, metadata.pieceLengthAt(0))
        assertEquals(60_000 - 32_768, metadata.pieceLengthAt(1))
    }

    @Test
    fun `parses metadata from raw info dict bytes (magnet path)`() {
        val infoBytes = BencodeEncoder.encode(infoDict)
        val metadata = TorrentMetadata.fromInfoDictBytes(
            infoBytes,
            listOf("http://from-magnet/announce")
        )!!

        assertEquals("test.iso", metadata.displayName)
        assertEquals(
            TorrentMetadata.fromTorrentFile(torrentBytes)!!.infoHashHex,
            metadata.infoHashHex
        )
        assertEquals(listOf("http://from-magnet/announce"), metadata.announceUrls)
    }

    @Test
    fun `detects multi file torrents`() {
        val multiFileInfo = mapOf<String, Any?>(
            "name" to "bundle",
            "files" to listOf(
                mapOf<String, Any?>("path" to listOf("a.txt"), "length" to 10L),
                mapOf<String, Any?>("path" to listOf("b", "c.txt"), "length" to 32L)
            ),
            "piece length" to 16_384L,
            "pieces" to fakePieceHash1
        )
        val bytes = BencodeEncoder.encode(mapOf<String, Any?>("info" to multiFileInfo))
        val metadata = TorrentMetadata.fromTorrentFile(bytes)!!

        assertTrue(metadata.isMultiFile)
        assertEquals(42L, metadata.totalBytes)
    }

    @Test
    fun `returns null for malformed input`() {
        assertNull(TorrentMetadata.fromTorrentFile("this is not a torrent".toByteArray()))
        assertNull(TorrentMetadata.fromTorrentFile(ByteArray(0)))
        // pieces length not a multiple of 20
        val badPieces = BencodeEncoder.encode(
            mapOf<String, Any?>(
                "info" to mapOf<String, Any?>(
                    "name" to "x", "length" to 1L,
                    "piece length" to 16L, "pieces" to ByteArray(10)
                )
            )
        )
        assertNull(TorrentMetadata.fromTorrentFile(badPieces))
    }

    @Test
    fun `pieceLengthAt rejects out of range indices`() {
        val metadata = TorrentMetadata.fromTorrentFile(torrentBytes)!!
        var thrown = false
        try {
            metadata.pieceLengthAt(2)
        } catch (e: IndexOutOfBoundsException) {
            thrown = true
        }
        assertTrue(thrown)
    }
}
