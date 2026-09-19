package com.rarestardev.turbodownloader.torrent

import com.rarestardev.turbodownloader.utils.TorrentUrlDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentUrlDetectorTest {

    @Test
    fun `detects magnet links`() {
        assertTrue(TorrentUrlDetector.isTorrentLink("magnet:?xt=urn:btih:${"a".repeat(40)}"))
        assertTrue(TorrentUrlDetector.isTorrentLink("MAGNET:?xt=urn:btih:${"a".repeat(40)}"))
        assertTrue(TorrentUrlDetector.isMagnetLink("magnet:?xt=urn:btih:${"a".repeat(40)}"))
    }

    @Test
    fun `detects torrent file urls`() {
        assertTrue(TorrentUrlDetector.isTorrentLink("https://example.com/linux.iso.torrent"))
        assertTrue(TorrentUrlDetector.isTorrentLink("https://example.com/file.TORRENT?token=1"))
        assertTrue(TorrentUrlDetector.isTorrentLink("HTTPS://EXAMPLE.COM/FILE.TORRENT"))
        assertFalse(TorrentUrlDetector.isMagnetLink("https://example.com/file.torrent"))
    }

    @Test
    fun `rejects normal links and empty input`() {
        assertFalse(TorrentUrlDetector.isTorrentLink("https://example.com/file.zip"))
        assertFalse(TorrentUrlDetector.isTorrentLink("https://example.com/torrentdownloader"))
        assertFalse(TorrentUrlDetector.isTorrentLink("torrentfile"))
        assertFalse(TorrentUrlDetector.isTorrentLink(""))
        assertFalse(TorrentUrlDetector.isTorrentLink(null))
        assertFalse(TorrentUrlDetector.isTorrentLink("   "))
    }
}

class PiecePoolTest {

    @Test
    fun `claims pieces in order and reports done at the end`() {
        val pool = PiecePool(3)

        assertEquals(PiecePool.Claim.Piece(0), pool.claim())
        assertEquals(PiecePool.Claim.Piece(1), pool.claim())
        assertEquals(PiecePool.Claim.Piece(2), pool.claim())
        // everything claimed by "peers" but not finished -> wait
        assertEquals(PiecePool.Claim.Wait, pool.claim())

        pool.complete(0)
        pool.complete(1)
        pool.complete(2)

        assertEquals(PiecePool.Claim.Done, pool.claim())
        assertTrue(pool.isComplete())
        assertEquals(3, pool.completedCount())
    }

    @Test
    fun `released pieces can be claimed again`() {
        val pool = PiecePool(1)
        assertEquals(PiecePool.Claim.Piece(0), pool.claim())
        pool.release(0)
        assertEquals(PiecePool.Claim.Piece(0), pool.claim())
    }

    @Test
    fun `pieces with too many attempts are never re-claimed`() {
        val pool = PiecePool(1, maxAttemptsPerPiece = 2)

        pool.claim(); pool.release(0)
        pool.claim(); pool.release(0)
        // attempts exhausted, no other pieces left
        assertEquals(PiecePool.Claim.Exhausted, pool.claim())
        assertTrue(pool.exhausted)
        assertFalse(pool.isComplete())
    }

    @Test
    fun `retried pieces are claimed only after fresh ones`() {
        val pool = PiecePool(2)
        assertEquals(PiecePool.Claim.Piece(0), pool.claim())
        pool.release(0) // piece 0 now has 1 attempt

        assertEquals(PiecePool.Claim.Piece(1), pool.claim())
        pool.complete(1)
        // only the retried piece remains
        assertEquals(PiecePool.Claim.Piece(0), pool.claim())
    }
}
