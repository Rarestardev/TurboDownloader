package com.rarestardev.turbodownloader.torrent

/**
 * Minimal bencode decoder (pure Kotlin, JVM unit-testable).
 *
 * Decoded values:
 * - integers  -> Long
 * - strings   -> ByteArray (raw bytes; may be UTF-8 text or binary data)
 * - lists     -> MutableList<Any?>
 * - dicts     -> MutableMap<String, Any?> (keys are ISO-8859-1 decoded,
 *                which is lossless and safe for ASCII bencode keys)
 */
object BencodeDecoder {

    class BencodeException(message: String) : Exception(message)

    /** Decodes a complete bencoded payload. Fails when trailing bytes remain. */
    fun decode(data: ByteArray): Any? {
        val parser = Parser(data)
        val value = parser.parseValue()
        if (parser.pos != data.size) {
            throw BencodeException("Trailing bytes after bencode value at offset ${parser.pos}")
        }
        return value
    }

    /**
     * Decodes the first bencoded value and also returns how many bytes it
     * consumed. Used for BEP-9 metadata messages where raw data follows
     * the bencoded header.
     */
    fun decodeWithLength(data: ByteArray): Pair<Any?, Int> {
        val parser = Parser(data)
        val value = parser.parseValue()
        return value to parser.pos
    }

    /**
     * Returns the RAW bencoded bytes of the top-level "info" dictionary of a
     * .torrent file. The SHA-1 of these exact bytes is the info-hash.
     */
    fun extractInfoDictBytes(torrentBytes: ByteArray): ByteArray? {
        return try {
            val parser = Parser(torrentBytes)
            if (torrentBytes[parser.pos] != 'd'.code.toByte()) return null
            parser.pos++
            while (parser.pos < torrentBytes.size && torrentBytes[parser.pos] != 'e'.code.toByte()) {
                val key = parser.parseString()
                val valueStart = parser.pos
                parser.parseValue()
                if (key.decodeToString() == "info") {
                    return torrentBytes.copyOfRange(valueStart, parser.pos)
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    internal class Parser(private val data: ByteArray) {
        var pos = 0

        private fun readByte(): Int {
            if (pos >= data.size) throw BencodeException("Unexpected end of data")
            return data[pos++].toInt() and 0xFF
        }

        private fun readBytes(count: Int): ByteArray {
            if (count < 0 || pos + count > data.size) {
                throw BencodeException("Unexpected end of data at offset $pos")
            }
            val result = data.copyOfRange(pos, pos + count)
            pos += count
            return result
        }

        private fun currentByte(): Int {
            if (pos >= data.size) throw BencodeException("Unexpected end of data")
            return data[pos].toInt() and 0xFF
        }

        fun parseString(): ByteArray {
            if (currentByte() !in '0'.code..'9'.code) {
                throw BencodeException("Expected string length at offset $pos")
            }
            var length = 0L
            while (true) {
                val c = readByte()
                if (c == ':'.code) break
                if (c !in '0'.code..'9'.code) throw BencodeException("Invalid string length at offset ${pos - 1}")
                length = length * 10 + (c - '0'.code)
                if (length > Int.MAX_VALUE) throw BencodeException("String too long")
            }
            return readBytes(length.toInt())
        }

        private fun parseInt(): Long {
            if (readByte() != 'i'.code) throw BencodeException("Expected integer at offset ${pos - 1}")
            var negative = false
            var value = 0L
            var digits = 0
            var first = true
            var leadingZero = false
            while (true) {
                val c = readByte()
                when {
                    c == 'e'.code -> {
                        if (first) throw BencodeException("Empty integer")
                        if (digits > 1 && leadingZero) {
                            throw BencodeException("Integer leading zero")
                        }
                        if (negative && value == 0L) throw BencodeException("Invalid integer '-0'")
                        return if (negative) -value else value
                    }
                    c == '-'.code -> {
                        if (!first || negative) throw BencodeException("Invalid integer sign")
                        negative = true
                    }
                    c in '0'.code..'9'.code -> {
                        if (first) leadingZero = (c == '0'.code)
                        value = value * 10 + (c - '0'.code)
                        digits++
                        first = false
                    }
                    else -> throw BencodeException("Invalid integer character at offset ${pos - 1}")
                }
            }
        }

        private fun parseList(): MutableList<Any?> {
            if (readByte() != 'l'.code) throw BencodeException("Expected list at offset ${pos - 1}")
            val list = mutableListOf<Any?>()
            while (currentByte() != 'e'.code) {
                list.add(parseValue())
            }
            pos++
            return list
        }

        private fun parseDict(): MutableMap<String, Any?> {
            if (readByte() != 'd'.code) throw BencodeException("Expected dict at offset ${pos - 1}")
            val dict = mutableMapOf<String, Any?>()
            while (currentByte() != 'e'.code) {
                val key = parseString().toString(Charsets.ISO_8859_1)
                dict[key] = parseValue()
            }
            pos++
            return dict
        }

        fun parseValue(): Any? {
            if (pos >= data.size) throw BencodeException("Unexpected end of data")
            return when (val c = currentByte()) {
                'i'.code -> parseInt()
                'l'.code -> parseList()
                'd'.code -> parseDict()
                in '0'.code..'9'.code -> parseString()
                else -> throw BencodeException("Invalid bencode token '${c.toChar()}' at offset $pos")
            }
        }
    }
}
