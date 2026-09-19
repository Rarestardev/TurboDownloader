package com.rarestardev.turbodownloader.torrent

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BencodeDecoderTest {

    @Test
    fun `decodes integers`() {
        assertEquals(42L, BencodeDecoder.decode("i42e".toByteArray()))
        assertEquals(-7L, BencodeDecoder.decode("i-7e".toByteArray()))
        assertEquals(0L, BencodeDecoder.decode("i0e".toByteArray()))
    }

    @Test
    fun `rejects malformed integers`() {
        assertThrows(BencodeDecoder.BencodeException::class.java) {
            BencodeDecoder.decode("ie".toByteArray())
        }
        assertThrows(BencodeDecoder.BencodeException::class.java) {
            BencodeDecoder.decode("i-0e".toByteArray())
        }
        assertThrows(BencodeDecoder.BencodeException::class.java) {
            BencodeDecoder.decode("i01e".toByteArray())
        }
        assertThrows(BencodeDecoder.BencodeException::class.java) {
            BencodeDecoder.decode("ix1e".toByteArray())
        }
    }

    @Test
    fun `decodes strings`() {
        assertArrayEquals("spam".toByteArray(), BencodeDecoder.decode("4:spam".toByteArray()) as ByteArray)
        assertArrayEquals(ByteArray(0), BencodeDecoder.decode("0:".toByteArray()) as ByteArray)
        // binary data must survive untouched
        val binary = byteArrayOf(0, -1, 127, 5)
        assertArrayEquals(binary, BencodeDecoder.decode("4:".toByteArray() + binary) as ByteArray)
    }

    @Test
    fun `decodes lists`() {
        val list = BencodeDecoder.decode("l4:spami42eli1eee".toByteArray()) as List<*>
        assertEquals(3, list.size)
        assertArrayEquals("spam".toByteArray(), list[0] as ByteArray)
        assertEquals(42L, list[1])
        assertEquals(listOf(1L), list[2])
    }

    @Test
    fun `decodes dictionaries with string keys`() {
        val dict = BencodeDecoder.decode("d3:bar4:spam3:fooi42ee".toByteArray()) as Map<*, *>
        assertEquals(2, dict.size)
        assertArrayEquals("spam".toByteArray(), dict["bar"] as ByteArray)
        assertEquals(42L, dict["foo"])
    }

    @Test
    fun `decodes nested structures`() {
        val payload = "d4:infod6:lengthi1000e4:name8:test.iso6:pieces20:AAAAAAAAAAAAAAAAAAAAee"
        val dict = BencodeDecoder.decode(payload.toByteArray()) as Map<*, *>
        val info = dict["info"] as Map<*, *>
        assertEquals(1000L, info["length"])
        assertArrayEquals("test.iso".toByteArray(), info["name"] as ByteArray)
    }

    @Test
    fun `rejects trailing garbage and bad tokens`() {
        assertThrows(BencodeDecoder.BencodeException::class.java) {
            BencodeDecoder.decode("i42eXX".toByteArray())
        }
        assertThrows(BencodeDecoder.BencodeException::class.java) {
            BencodeDecoder.decode("x".toByteArray())
        }
        assertThrows(BencodeDecoder.BencodeException::class.java) {
            BencodeDecoder.decode(ByteArray(0))
        }
    }

    @Test
    fun `decodeWithLength returns the consumed byte count`() {
        val (value, consumed) = BencodeDecoder.decodeWithLength("i5eTRAILING".toByteArray())
        assertEquals(5L, value)
        assertEquals(3, consumed)
    }

    @Test
    fun `extractInfoDictBytes returns the exact info dict bytes`() {
        val infoBytes = "d6:lengthi1000e4:name8:test.isoe".toByteArray()
        val torrent = "d8:announce23:http://tracker/announce4:info".toByteArray() +
                infoBytes + "e".toByteArray()

        val extracted = BencodeDecoder.extractInfoDictBytes(torrent)
        assertArrayEquals(infoBytes, extracted!!)
    }

    @Test
    fun `extractInfoDictBytes returns null for non torrents`() {
        assertNull(BencodeDecoder.extractInfoDictBytes("i42e".toByteArray()))
        assertNull(BencodeDecoder.extractInfoDictBytes("d8:announce4:teste".toByteArray()))
        assertTrue(BencodeDecoder.extractInfoDictBytes("garbage!".toByteArray()) == null)
    }
}
