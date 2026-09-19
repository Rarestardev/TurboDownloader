package com.rarestardev.turbodownloader.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MagnetUriTest {

    @Test
    fun `parses hex info hash, name and trackers`() {
        val hash = "a".repeat(40)
        val uri = "magnet:?xt=urn:btih:$hash&dn=My%20File.iso" +
                "&tr=http%3A%2F%2Ftracker1%2Fannounce&tr=http%3A%2F%2Ftracker2%2Fannounce"

        val magnet = MagnetUri.parse(uri)

        assertNotNull(magnet)
        magnet!!
        assertEquals(hash, magnet.infoHashHex)
        assertEquals("My File.iso", magnet.displayName)
        assertEquals(
            listOf("http://tracker1/announce", "http://tracker2/announce"),
            magnet.trackerUrls
        )
    }

    @Test
    fun `normalizes hash to lowercase`() {
        val magnet = MagnetUri.parse("magnet:?xt=urn:btih:${"AB".repeat(20)}")
        assertNotNull(magnet)
        assertEquals("ab".repeat(20), magnet!!.infoHashHex)
    }

    @Test
    fun `converts base32 info hash to hex`() {
        // 20 zero bytes encode to 32 'A' characters in base32
        val magnet = MagnetUri.parse("magnet:?xt=urn:btih:${"A".repeat(32)}")

        assertNotNull(magnet)
        assertEquals("0".repeat(40), magnet!!.infoHashHex)
    }

    @Test
    fun `keeps only the first xt parameter`() {
        val uri = "magnet:?xt=urn:btih:${"1".repeat(40)}&xt=urn:btih:${"2".repeat(40)}"
        val magnet = MagnetUri.parse(uri)
        assertEquals("1".repeat(40), magnet!!.infoHashHex)
    }

    @Test
    fun `rejects invalid inputs`() {
        assertNull(MagnetUri.parse("https://example.com/file.torrent"))
        assertNull(MagnetUri.parse("magnet:?dn=only-a-name"))
        assertNull(MagnetUri.parse("magnet:?xt=urn:btih:tooshort"))
        assertNull(MagnetUri.parse("magnet:?xt=urn:sha1:${"a".repeat(40)}"))
    }

    @Test
    fun `handles magnet without display name or trackers`() {
        val magnet = MagnetUri.parse("magnet:?xt=urn:btih:${"f".repeat(40)}")
        assertNotNull(magnet)
        assertNull(magnet!!.displayName)
        assertEquals(0, magnet.trackerUrls.size)
    }
}
