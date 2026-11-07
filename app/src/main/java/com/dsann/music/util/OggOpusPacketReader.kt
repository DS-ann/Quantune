package com.dsann.music.util

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/**
 * Small streaming Ogg page/packet reader for Ogg Opus sources.
 *
 * It deliberately handles packet continuation across page boundaries. No whole-song buffering
 * is used, so it remains suitable for older, low-memory Android devices.
 */
internal class OggOpusPacketReader(input: InputStream) : Closeable {
    data class Page(
        val packets: List<ByteArray>,
        val granulePosition: Long,
        val isEndOfStream: Boolean
    )

    private val stream = BufferedInputStream(input, 64 * 1024)
    private val partialPacket = ByteArrayOutputStream()
    private var streamSerial: Long? = null
    private var nextPageSequence: Long? = null

    @Throws(IOException::class)
    fun nextPage(): Page? {
        val header = readExactlyOrEnd(27) ?: return null
        if (!startsWith(header, "OggS") || (header[4].toInt() and 0xFF) != 0) {
            throw IOException("The Opus file contains an invalid Ogg page header")
        }

        val headerType = header[5].toInt() and 0xFF
        val granulePosition = readLittleEndianLong(header, 6)
        val serial = readLittleEndianUnsignedInt(header, 14)
        val sequence = readLittleEndianUnsignedInt(header, 18)
        val expectedSerial = streamSerial
        if (expectedSerial == null) {
            streamSerial = serial
        } else if (expectedSerial != serial) {
            throw IOException("Chained or multiplexed Ogg streams are not supported")
        }
        val expectedSequence = nextPageSequence
        if (expectedSequence != null && expectedSequence != sequence) {
            throw IOException("The Ogg Opus stream is missing a page")
        }
        nextPageSequence = (sequence + 1L) and 0xFFFFFFFFL

        val segmentCount = header[26].toInt() and 0xFF
        val lacing = readExactly(segmentCount)
        val bodyLength = lacing.sumOf { it.toInt() and 0xFF }
        val body = readExactly(bodyLength)
        val completedPackets = ArrayList<ByteArray>()
        var bodyOffset = 0

        for (segment in lacing) {
            val segmentLength = segment.toInt() and 0xFF
            if (partialPacket.size().toLong() + segmentLength > MAX_PACKET_BYTES) {
                throw IOException("An Ogg Opus packet exceeds the safe size limit")
            }
            if (segmentLength > 0) {
                partialPacket.write(body, bodyOffset, segmentLength)
            }
            bodyOffset += segmentLength

            if (segmentLength < 255) {
                completedPackets += partialPacket.toByteArray()
                partialPacket.reset()
            }
        }

        val isEndOfStream = (headerType and 0x04) != 0
        if (isEndOfStream && partialPacket.size() > 0) {
            throw IOException("The final Ogg Opus page ends in an incomplete packet")
        }

        return Page(
            packets = completedPackets,
            granulePosition = granulePosition,
            isEndOfStream = isEndOfStream
        )
    }

    override fun close() {
        stream.close()
    }

    private fun readExactlyOrEnd(size: Int): ByteArray? {
        if (size == 0) return ByteArray(0)
        val first = stream.read()
        if (first < 0) return null
        val result = ByteArray(size)
        result[0] = first.toByte()
        readInto(result, 1, size - 1)
        return result
    }

    private fun readExactly(size: Int): ByteArray {
        if (size == 0) return ByteArray(0)
        val result = ByteArray(size)
        readInto(result, 0, size)
        return result
    }

    private fun readInto(target: ByteArray, initialOffset: Int, count: Int) {
        var offset = initialOffset
        val end = initialOffset + count
        while (offset < end) {
            val read = stream.read(target, offset, end - offset)
            if (read < 0) throw EOFException("The Ogg Opus file is truncated")
            if (read == 0) continue
            offset += read
        }
    }

    private fun startsWith(bytes: ByteArray, value: String): Boolean {
        val expected = value.toByteArray(Charsets.US_ASCII)
        if (bytes.size < expected.size) return false
        return expected.indices.all { bytes[it] == expected[it] }
    }

    private fun readLittleEndianUnsignedInt(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xFFL) or
            ((bytes[offset + 1].toLong() and 0xFFL) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFFL) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFFL) shl 24)

    private fun readLittleEndianLong(bytes: ByteArray, offset: Int): Long {
        var value = 0L
        for (index in 0 until 8) {
            value = value or ((bytes[offset + index].toLong() and 0xFFL) shl (index * 8))
        }
        return value
    }

    companion object {
        private const val MAX_PACKET_BYTES = 16 * 1024 * 1024
    }
}
