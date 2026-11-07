package com.dsann.music.util

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.security.SecureRandom

/**
 * Converts YouTube's WebM/Opus stream into a standards-compliant Ogg Opus file.
 *
 * Audio packets are copied unchanged: this is a container remux, not an audio transcode.
 * Ogg Opus comments and METADATA_BLOCK_PICTURE are written as the first two Ogg packets.
 */
internal object OggOpusRemuxer {
    private const val EBML_MAGIC = 0x1A45DFA3L
    private const val SEGMENT_ID = 0x18538067L
    private const val INFO_ID = 0x1549A966L
    private const val TRACKS_ID = 0x1654AE6BL
    private const val TRACK_ENTRY_ID = 0xAEL
    private const val TRACK_NUMBER_ID = 0xD7L
    private const val TRACK_TYPE_ID = 0x83L
    private const val CODEC_ID_ID = 0x86L
    private const val CODEC_PRIVATE_ID = 0x63A2L
    private const val CLUSTER_ID = 0x1F43B675L
    private const val CLUSTER_TIMESTAMP_ID = 0xE7L
    private const val SIMPLE_BLOCK_ID = 0xA3L
    private const val BLOCK_GROUP_ID = 0xA0L
    private const val BLOCK_ID = 0xA1L

    private val clusterChildIds = setOf(
        CLUSTER_TIMESTAMP_ID, 0xA7L, 0xABL, SIMPLE_BLOCK_ID,
        BLOCK_GROUP_ID, 0x5854L, 0xAFL, 0xBFL, 0xECL
    )

    private val utf8: Charset = Charsets.UTF_8

    @Throws(IOException::class)
    fun writeTaggedOpus(
        inputFile: File,
        outputFile: File,
        title: String,
        artist: String,
        album: String?,
        comment: String,
        artworkBytes: ByteArray?
    ) {
        if (!inputFile.isFile || inputFile.length() <= 0L) {
            throw IOException("The Opus source file is missing or empty")
        }
        outputFile.parentFile?.let {
            if (!it.exists() && !it.mkdirs()) throw IOException("Could not create the Opus staging directory")
        }

        val magic = RandomAccessFile(inputFile, "r").use { input ->
            if (input.length() < 4L) throw IOException("The Opus source file is too small")
            input.readInt().toLong() and 0xFFFFFFFFL
        }

        when (magic) {
            EBML_MAGIC -> remuxWebm(
                inputFile, outputFile, title, artist, album, comment, artworkBytes
            )
            0x4F676753L -> retagOgg(
                inputFile, outputFile, title, artist, album, comment, artworkBytes
            )
            else -> throw IOException("The selected Opus stream is neither WebM nor Ogg")
        }

        verifyTaggedFile(outputFile, title, artist, album, artworkBytes != null && artworkBytes.isNotEmpty())
    }

    private fun remuxWebm(
        inputFile: File,
        outputFile: File,
        title: String,
        artist: String,
        album: String?,
        comment: String,
        artworkBytes: ByteArray?
    ) {
        RandomAccessFile(inputFile, "r").use { input ->
            val (segmentStart, segmentEnd) = findSegment(input)
            input.seek(segmentStart)

            var opusTrack: OpusTrack? = null
            var firstClusterOffset = -1L

            // YouTube's WebM layout places Info and Tracks before the media Clusters.
            // Read those small metadata elements first, then process each Cluster in order.
            while (input.filePointer < segmentEnd) {
                val element = readElement(input, segmentEnd)
                    ?: break
                when (element.id) {
                    INFO_ID -> input.seek(element.end)
                    TRACKS_ID -> {
                        opusTrack = parseTracks(input, element)
                        input.seek(element.end)
                    }
                    CLUSTER_ID -> {
                        firstClusterOffset = element.start
                        input.seek(element.start)
                        break
                    }
                    else -> {
                        if (element.unknownSize) {
                            throw IOException("Unexpected unknown-sized WebM element before the audio Clusters")
                        }
                        input.seek(element.end)
                    }
                }
            }

            val track = opusTrack ?: throw IOException("The WebM stream does not contain an Opus audio track")
            validateOpusHead(track.codecPrivate)
            if (firstClusterOffset < 0L) throw IOException("The WebM stream contains no audio Cluster")

            RandomAccessFile(outputFile, "rw").use { output ->
                output.setLength(0L)
                val writer = OggPageWriter(output)
                writer.writePacket(track.codecPrivate, 0L, bos = true)
                writer.writePacket(
                    buildOpusTags(title, artist, album, comment, artworkBytes),
                    0L
                )

                var granulePosition = opusPreSkip(track.codecPrivate).toLong()
                var audioPacketCount = 0

                input.seek(firstClusterOffset)
                while (input.filePointer < segmentEnd) {
                    val element = readElement(input, segmentEnd) ?: break
                    if (element.id != CLUSTER_ID) {
                        if (element.unknownSize) break
                        input.seek(element.end)
                        continue
                    }

                    val blocks = parseCluster(input, element, segmentEnd)
                    for (block in blocks) {
                        val packets = parseBlockPackets(block, track.number)
                        for (packet in packets) {
                            val duration = opusPacketSamples(packet)
                            granulePosition += duration.toLong()
                            writer.writePacket(packet, granulePosition)
                            audioPacketCount++
                        }
                    }
                }

                if (audioPacketCount == 0) {
                    throw IOException("The WebM stream did not contain any Opus audio packets")
                }
                writer.finish()
            }
        }
    }

    private fun retagOgg(
        inputFile: File,
        outputFile: File,
        title: String,
        artist: String,
        album: String?,
        comment: String,
        artworkBytes: ByteArray?
    ) {
        RandomAccessFile(inputFile, "r").use { input ->
            val reader = OggPacketReader(input)
            val opusHead = reader.nextPacket()
                ?: throw IOException("The Ogg file is missing its OpusHead packet")
            validateOpusHead(opusHead)
            val oldTags = reader.nextPacket()
                ?: throw IOException("The Ogg file is missing its OpusTags packet")
            if (!startsWithAscii(oldTags, "OpusTags")) {
                throw IOException("The Ogg file is not an Ogg Opus stream")
            }

            RandomAccessFile(outputFile, "rw").use { output ->
                output.setLength(0L)
                val writer = OggPageWriter(output)
                writer.writePacket(opusHead, 0L, bos = true)
                writer.writePacket(
                    buildOpusTags(title, artist, album, comment, artworkBytes),
                    0L
                )

                var granulePosition = opusPreSkip(opusHead).toLong()
                var audioPacketCount = 0
                while (true) {
                    val packet = reader.nextPacket() ?: break
                    if (packet.isEmpty()) continue
                    granulePosition += opusPacketSamples(packet).toLong()
                    writer.writePacket(packet, granulePosition)
                    audioPacketCount++
                }
                if (audioPacketCount == 0) {
                    throw IOException("The Ogg Opus file contains no audio packets")
                }
                writer.finish()
            }
        }
    }

    private data class OpusTrack(
        val number: Long,
        val codecPrivate: ByteArray
    )

    private data class EbmlElement(
        val id: Long,
        val start: Long,
        val dataStart: Long,
        val size: Long,
        val end: Long,
        val unknownSize: Boolean
    )

    private data class Vint(
        val value: Long,
        val width: Int,
        val unknownSize: Boolean
    )

    private fun findSegment(input: RandomAccessFile): Pair<Long, Long> {
        input.seek(0L)
        val fileEnd = input.length()
        while (input.filePointer < fileEnd) {
            val element = readElement(input, fileEnd)
                ?: break
            if (element.id == SEGMENT_ID) {
                return element.dataStart to element.end
            }
            if (element.unknownSize) {
                throw IOException("Unexpected unknown-sized EBML element before Segment")
            }
            input.seek(element.end)
        }
        throw IOException("The WebM file does not contain an EBML Segment")
    }

    private fun readElement(input: RandomAccessFile, limit: Long): EbmlElement? {
        if (input.filePointer >= limit) return null
        val start = input.filePointer
        val id = readVint(input, keepMarker = true, maxWidth = 4).value
        val sizeVint = readVint(input, keepMarker = false, maxWidth = 8)
        val dataStart = input.filePointer
        val unknown = sizeVint.unknownSize
        val size = if (unknown) -1L else sizeVint.value
        val end = if (unknown) limit else {
            if (size < 0L || dataStart + size < dataStart || dataStart + size > limit) {
                throw IOException("EBML element extends beyond its parent")
            }
            dataStart + size
        }
        return EbmlElement(id, start, dataStart, size, end, unknown)
    }

    private fun readVint(
        input: RandomAccessFile,
        keepMarker: Boolean,
        maxWidth: Int
    ): Vint {
        val first = input.readUnsignedByte()
        var width = 1
        var marker = 0x80
        while (width <= maxWidth && (first and marker) == 0) {
            marker = marker ushr 1
            width++
        }
        if (width > maxWidth || marker == 0) throw IOException("Invalid EBML variable-length integer")

        var value = if (keepMarker) first.toLong() else (first and (marker - 1)).toLong()
        repeat(width - 1) {
            value = (value shl 8) or input.readUnsignedByte().toLong()
        }
        val unknown = !keepMarker && value == ((1L shl (7 * width)) - 1L)
        return Vint(value, width, unknown)
    }

    private fun readUnsigned(input: RandomAccessFile, size: Long): Long {
        if (size !in 1L..8L) throw IOException("Invalid EBML integer size: $size")
        var value = 0L
        repeat(size.toInt()) {
            value = (value shl 8) or input.readUnsignedByte().toLong()
        }
        return value
    }

    private fun readElementBytes(input: RandomAccessFile, element: EbmlElement, maxBytes: Long = 16L * 1024L * 1024L): ByteArray {
        if (element.unknownSize || element.size < 0L || element.size > maxBytes || element.size > Int.MAX_VALUE) {
            throw IOException("EBML element is too large to read safely")
        }
        val data = ByteArray(element.size.toInt())
        input.seek(element.dataStart)
        input.readFully(data)
        return data
    }

    private fun parseTracks(input: RandomAccessFile, tracksElement: EbmlElement): OpusTrack? {
        input.seek(tracksElement.dataStart)
        while (input.filePointer < tracksElement.end) {
            val child = readElement(input, tracksElement.end) ?: break
            if (child.id == TRACK_ENTRY_ID) {
                val track = parseTrackEntry(input, child)
                if (track != null) return track
            }
            if (child.unknownSize) throw IOException("Unexpected unknown-sized Track element")
            input.seek(child.end)
        }
        return null
    }

    private fun parseTrackEntry(input: RandomAccessFile, entry: EbmlElement): OpusTrack? {
        var number = -1L
        var type = -1L
        var codecId: String? = null
        var codecPrivate: ByteArray? = null

        input.seek(entry.dataStart)
        while (input.filePointer < entry.end) {
            val child = readElement(input, entry.end) ?: break
            when (child.id) {
                TRACK_NUMBER_ID -> number = readUnsigned(input, child.size)
                TRACK_TYPE_ID -> type = readUnsigned(input, child.size)
                CODEC_ID_ID -> codecId = String(readElementBytes(input, child, 256L), utf8)
                CODEC_PRIVATE_ID -> codecPrivate = readElementBytes(input, child, 64L * 1024L)
                else -> Unit
            }
            if (child.unknownSize) throw IOException("Unexpected unknown-sized TrackEntry field")
            input.seek(child.end)
        }

        if (type == 2L && codecId == "A_OPUS") {
            val trackNumber = number.takeIf { it > 0L }
                ?: throw IOException("The Opus track has no valid track number")
            val privateData = codecPrivate
                ?: throw IOException("The WebM Opus track is missing CodecPrivate")
            return OpusTrack(trackNumber, privateData)
        }
        return null
    }

    private fun parseCluster(
        input: RandomAccessFile,
        cluster: EbmlElement,
        segmentEnd: Long
    ): List<ByteArray> {
        val blocks = ArrayList<ByteArray>()
        val clusterEnd = if (cluster.unknownSize) segmentEnd else cluster.end
        input.seek(cluster.dataStart)

        while (input.filePointer < clusterEnd) {
            val childStart = input.filePointer
            val child = readElement(input, clusterEnd) ?: break
            if (cluster.unknownSize && child.id !in clusterChildIds) {
                input.seek(childStart)
                break
            }

            when (child.id) {
                SIMPLE_BLOCK_ID -> blocks.add(readElementBytes(input, child, 4L * 1024L * 1024L))
                BLOCK_GROUP_ID -> {
                    val block = parseBlockGroup(input, child)
                    if (block != null) blocks.add(block)
                }
                else -> Unit
            }

            if (child.unknownSize) {
                if (cluster.unknownSize) {
                    input.seek(childStart)
                    break
                }
                throw IOException("Unexpected unknown-sized WebM Cluster child")
            }
            input.seek(child.end)
        }
        return blocks
    }

    private fun parseBlockGroup(input: RandomAccessFile, group: EbmlElement): ByteArray? {
        if (group.unknownSize) throw IOException("Unexpected unknown-sized BlockGroup")
        var block: ByteArray? = null
        input.seek(group.dataStart)
        while (input.filePointer < group.end) {
            val child = readElement(input, group.end) ?: break
            if (child.id == BLOCK_ID) {
                block = readElementBytes(input, child, 4L * 1024L * 1024L)
            }
            if (child.unknownSize) throw IOException("Unexpected unknown-sized BlockGroup field")
            input.seek(child.end)
        }
        return block
    }

    private fun parseBlockPackets(block: ByteArray, wantedTrackNumber: Long): List<ByteArray> {
        val (trackNumber, trackWidth) = readByteVint(block, 0, keepMarker = false)
        if (trackNumber != wantedTrackNumber) return emptyList()
        var offset = trackWidth
        if (block.size - offset < 3) throw IOException("Truncated WebM audio Block")
        offset += 2 // signed relative timecode; packet ordering is preserved by Block order
        val flags = block[offset++].toInt() and 0xFF
        val lacing = (flags ushr 1) and 0x03

        if (offset >= block.size) throw IOException("WebM audio Block contains no Opus packet")
        if (lacing == 0) return listOf(block.copyOfRange(offset, block.size))

        val frameCount = (block[offset++].toInt() and 0xFF) + 1
        if (frameCount < 1 || frameCount > 256) throw IOException("Invalid WebM lacing frame count")
        val frameSizes = ArrayList<Int>(frameCount)

        when (lacing) {
            1 -> { // Xiph lacing
                var declared = 0L
                repeat(frameCount - 1) {
                    var frameSize = 0L
                    var value: Int
                    do {
                        if (offset >= block.size) throw IOException("Truncated Xiph lacing data")
                        value = block[offset++].toInt() and 0xFF
                        frameSize += value.toLong()
                        if (frameSize > Int.MAX_VALUE) throw IOException("Xiph lacing frame is too large")
                    } while (value == 255)
                    frameSizes.add(frameSize.toInt())
                    declared += frameSize
                }
                val remaining = block.size - offset
                val finalSize = remaining.toLong() - declared
                if (finalSize < 0L || finalSize > Int.MAX_VALUE) throw IOException("Invalid Xiph lacing sizes")
                frameSizes.add(finalSize.toInt())
            }
            2 -> { // Fixed-size lacing
                val remaining = block.size - offset
                if (remaining % frameCount != 0) throw IOException("Invalid fixed-size WebM lacing")
                repeat(frameCount) { frameSizes.add(remaining / frameCount) }
            }
            3 -> { // EBML lacing
                if (frameCount == 1) {
                    frameSizes.add(block.size - offset)
                } else {
                    val (firstSize, firstWidth) = readByteVint(block, offset, keepMarker = false)
                    offset += firstWidth
                    if (firstSize > Int.MAX_VALUE) throw IOException("EBML lacing frame is too large")
                    frameSizes.add(firstSize.toInt())
                    var previous = firstSize
                    repeat(frameCount - 2) {
                        val (encodedDelta, deltaWidth) = readByteVint(block, offset, keepMarker = false)
                        offset += deltaWidth
                        val bias = (1L shl (7 * deltaWidth - 1)) - 1L
                        val size = previous + encodedDelta - bias
                        if (size < 0L || size > Int.MAX_VALUE) throw IOException("Invalid EBML lacing delta")
                        frameSizes.add(size.toInt())
                        previous = size
                    }
                    val declared = frameSizes.sumOf { it.toLong() }
                    val finalSize = block.size.toLong() - offset.toLong() - declared
                    if (finalSize < 0L || finalSize > Int.MAX_VALUE) throw IOException("Invalid EBML lacing sizes")
                    frameSizes.add(finalSize.toInt())
                }
            }
        }

        if (frameSizes.size != frameCount) throw IOException("Invalid WebM lacing table")
        val packets = ArrayList<ByteArray>(frameCount)
        var packetOffset = offset
        for (frameSize in frameSizes) {
            if (frameSize < 0 || packetOffset + frameSize > block.size) {
                throw IOException("WebM lacing frame extends beyond its Block")
            }
            if (frameSize == 0) throw IOException("WebM audio Block contains an empty Opus packet")
            packets.add(block.copyOfRange(packetOffset, packetOffset + frameSize))
            packetOffset += frameSize
        }
        if (packetOffset != block.size) throw IOException("WebM lacing did not consume the complete Block")
        return packets
    }

    private fun readByteVint(
        bytes: ByteArray,
        offset: Int,
        keepMarker: Boolean
    ): Pair<Long, Int> {
        if (offset !in bytes.indices) throw IOException("Missing EBML lacing integer")
        val first = bytes[offset].toInt() and 0xFF
        var width = 1
        var marker = 0x80
        while (width <= 8 && (first and marker) == 0) {
            marker = marker ushr 1
            width++
        }
        if (width > 8 || marker == 0 || offset + width > bytes.size) {
            throw IOException("Invalid EBML lacing integer")
        }
        var value = if (keepMarker) first.toLong() else (first and (marker - 1)).toLong()
        for (index in 1 until width) {
            value = (value shl 8) or (bytes[offset + index].toInt() and 0xFF).toLong()
        }
        return value to width
    }

    private fun validateOpusHead(packet: ByteArray) {
        if (packet.size < 19 || !startsWithAscii(packet, "OpusHead")) {
            throw IOException("The WebM audio track does not contain a valid OpusHead")
        }
        val channels = packet[9].toInt() and 0xFF
        if (channels == 0) throw IOException("The Opus stream reports zero audio channels")
    }

    private fun opusPreSkip(opusHead: ByteArray): Int =
        (opusHead[10].toInt() and 0xFF) or ((opusHead[11].toInt() and 0xFF) shl 8)

    /** Returns the total decoded sample count at Opus' 48 kHz granule clock. */
    private fun opusPacketSamples(packet: ByteArray): Int {
        if (packet.isEmpty()) throw IOException("Empty Opus audio packet")
        val toc = packet[0].toInt() and 0xFF
        val config = toc ushr 3
        val frameSamples = when {
            config < 12 -> when (config and 0x03) {
                0 -> 480
                1 -> 960
                2 -> 1920
                else -> 2880
            } // SILK: 10, 20, 40 or 60 ms
            config < 16 -> if ((config and 0x01) == 0) 480 else 960 // Hybrid: 10 or 20 ms
            else -> when (config and 0x03) {
                0 -> 120
                1 -> 240
                2 -> 480
                else -> 960
            } // CELT: 2.5, 5, 10 or 20 ms
        }
        val frameCount = when (toc and 0x03) {
            0 -> 1
            1, 2 -> 2
            else -> {
                if (packet.size < 2) throw IOException("Truncated Opus frame-count code")
                packet[1].toInt() and 0x3F
            }
        }
        val total = frameSamples * frameCount
        if (frameCount !in 1..48 || total > 5760) {
            throw IOException("Invalid Opus packet duration")
        }
        return total
    }

    private fun buildOpusTags(
        title: String,
        artist: String,
        album: String?,
        comment: String,
        artworkBytes: ByteArray?
    ): ByteArray {
        val comments = ArrayList<String>()
        if (title.isNotBlank()) comments.add("TITLE=$title")
        if (artist.isNotBlank()) comments.add("ARTIST=$artist")
        album?.takeIf { it.isNotBlank() }?.let { comments.add("ALBUM=$it") }
        if (comment.isNotBlank()) comments.add("COMMENT=$comment")
        artworkBytes?.takeIf { it.isNotEmpty() }?.let {
            comments.add("METADATA_BLOCK_PICTURE=${Base64.encodeToString(buildFlacPictureBlock(it), Base64.NO_WRAP)}")
        }

        val vendor = "D.S Music".toByteArray(utf8)
        val output = ByteArrayOutputStream()
        output.write("OpusTags".toByteArray(Charsets.US_ASCII))
        writeLittleEndian32(output, vendor.size)
        output.write(vendor)
        writeLittleEndian32(output, comments.size)
        comments.forEach { value ->
            val encoded = value.toByteArray(utf8)
            writeLittleEndian32(output, encoded.size)
            output.write(encoded)
        }
        return output.toByteArray()
    }

    /** Vorbis-comment METADATA_BLOCK_PICTURE uses the FLAC picture-block binary layout. */
    private fun buildFlacPictureBlock(imageBytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream(imageBytes.size + 64)
        writeBigEndian32(output, 3) // front cover
        writeLengthPrefixed(output, "image/jpeg".toByteArray(Charsets.US_ASCII))
        writeLengthPrefixed(output, "Front cover".toByteArray(utf8))
        writeBigEndian32(output, 0) // width: not required for a valid picture block
        writeBigEndian32(output, 0) // height
        writeBigEndian32(output, 0) // color depth
        writeBigEndian32(output, 0) // indexed colors
        writeLengthPrefixed(output, imageBytes)
        return output.toByteArray()
    }

    private fun writeLengthPrefixed(output: ByteArrayOutputStream, bytes: ByteArray) {
        writeBigEndian32(output, bytes.size)
        output.write(bytes)
    }

    private fun writeLittleEndian32(output: ByteArrayOutputStream, value: Int) {
        output.write(value and 0xFF)
        output.write((value ushr 8) and 0xFF)
        output.write((value ushr 16) and 0xFF)
        output.write((value ushr 24) and 0xFF)
    }

    private fun writeBigEndian32(output: ByteArrayOutputStream, value: Int) {
        output.write((value ushr 24) and 0xFF)
        output.write((value ushr 16) and 0xFF)
        output.write((value ushr 8) and 0xFF)
        output.write(value and 0xFF)
    }

    private fun verifyTaggedFile(
        file: File,
        title: String,
        artist: String,
        album: String?,
        expectPicture: Boolean
    ) {
        RandomAccessFile(file, "r").use { input ->
            val reader = OggPacketReader(input)
            val head = reader.nextPacket() ?: throw IOException("Tagged Opus file has no OpusHead")
            validateOpusHead(head)
            val tags = reader.nextPacket() ?: throw IOException("Tagged Opus file has no OpusTags")
            val fields = parseOpusTags(tags)
            if (fields["TITLE"] != title) throw IOException("The Opus TITLE tag was not persisted")
            if (fields["ARTIST"] != artist) throw IOException("The Opus ARTIST tag was not persisted")
            if (!album.isNullOrBlank() && fields["ALBUM"] != album) {
                throw IOException("The Opus ALBUM tag was not persisted")
            }
            if (expectPicture && fields["METADATA_BLOCK_PICTURE"].isNullOrBlank()) {
                throw IOException("The Opus cover artwork was not persisted")
            }
            var audioPackets = 0
            while (true) {
                val packet = reader.nextPacket() ?: break
                if (packet.isNotEmpty()) {
                    opusPacketSamples(packet)
                    audioPackets++
                }
            }
            if (audioPackets == 0) throw IOException("Tagged Opus file has no audio packets")
        }
    }

    private fun parseOpusTags(packet: ByteArray): Map<String, String> {
        if (!startsWithAscii(packet, "OpusTags") || packet.size < 16) {
            throw IOException("Invalid OpusTags packet")
        }
        var offset = 8
        val vendorLength = readLittleEndian32(packet, offset)
        offset += 4
        if (vendorLength < 0 || offset + vendorLength > packet.size) {
            throw IOException("Invalid OpusTags vendor string")
        }
        offset += vendorLength
        val count = readLittleEndian32(packet, offset)
        offset += 4
        if (count < 0 || count > 10000) throw IOException("Invalid OpusTags comment count")

        val result = LinkedHashMap<String, String>()
        repeat(count) {
            val length = readLittleEndian32(packet, offset)
            offset += 4
            if (length < 0 || offset + length > packet.size) {
                throw IOException("Invalid OpusTags comment length")
            }
            val comment = String(packet, offset, length, utf8)
            offset += length
            val separator = comment.indexOf('=')
            if (separator > 0) {
                result[comment.substring(0, separator).uppercase()] = comment.substring(separator + 1)
            }
        }
        if (offset != packet.size) throw IOException("Unexpected trailing bytes in OpusTags")
        return result
    }

    private fun readLittleEndian32(bytes: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 4 > bytes.size) throw IOException("Truncated OpusTags packet")
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun startsWithAscii(bytes: ByteArray, value: String): Boolean {
        val expected = value.toByteArray(Charsets.US_ASCII)
        if (bytes.size < expected.size) return false
        return expected.indices.all { index -> bytes[index] == expected[index] }
    }

    /**
     * Reads complete Ogg packets from a single logical bitstream. Continuation segments
     * spanning multiple pages are joined before a packet is returned.
     */
    private class OggPacketReader(private val input: RandomAccessFile) {
        private var lacing = ByteArray(0)
        private var body = ByteArray(0)
        private var segmentIndex = 0
        private var bodyOffset = 0
        private var serialNumber: Long? = null
        private val partialPacket = ByteArrayOutputStream()

        fun nextPacket(): ByteArray? {
            while (true) {
                if (segmentIndex >= lacing.size) {
                    if (!readPage()) {
                        if (partialPacket.size() > 0) throw IOException("Truncated Ogg packet at end of file")
                        return null
                    }
                }

                val length = lacing[segmentIndex++].toInt() and 0xFF
                if (bodyOffset + length > body.size) throw IOException("Invalid Ogg page lacing table")
                if (length > 0) partialPacket.write(body, bodyOffset, length)
                bodyOffset += length

                if (length < 255) {
                    val packet = partialPacket.toByteArray()
                    partialPacket.reset()
                    return packet
                }
            }
        }

        private fun readPage(): Boolean {
            if (input.filePointer >= input.length()) return false
            val header = ByteArray(27)
            try {
                input.readFully(header)
            } catch (error: Exception) {
                throw IOException("Truncated Ogg page header", error)
            }
            if (!startsWithAscii(header, "OggS") || header[4].toInt() != 0) {
                throw IOException("Invalid Ogg page capture pattern or version")
            }
            val pageSerial = readLittleEndian32(header, 14).toLong() and 0xFFFFFFFFL
            val previousSerial = serialNumber
            if (previousSerial != null && previousSerial != pageSerial) {
                throw IOException("Chained or multiplexed Ogg streams are not supported")
            }
            serialNumber = pageSerial

            val segmentCount = header[26].toInt() and 0xFF
            lacing = ByteArray(segmentCount)
            try {
                input.readFully(lacing)
            } catch (error: Exception) {
                throw IOException("Truncated Ogg lacing table", error)
            }
            val bodyLength = lacing.sumOf { it.toInt() and 0xFF }
            body = ByteArray(bodyLength)
            try {
                input.readFully(body)
            } catch (error: Exception) {
                throw IOException("Truncated Ogg page body", error)
            }
            segmentIndex = 0
            bodyOffset = 0
            return true
        }
    }

    /** Writes Ogg pages and adds EOS to the final page once all packets are known. */
    private class OggPageWriter(private val output: RandomAccessFile) {
        private val serial = SecureRandom().nextInt()
        private var sequence = 0
        private var lastPageOffset = -1L

        fun writePacket(packet: ByteArray, granulePosition: Long, bos: Boolean = false) {
            if (packet.isEmpty()) throw IOException("Cannot write an empty Ogg packet")
            var offset = 0
            var continued = false
            var firstPage = true

            while (true) {
                val laces = ArrayList<Int>(255)
                val pageBody = ByteArrayOutputStream()
                var packetComplete = false

                while (offset < packet.size && laces.size < 255) {
                    val length = minOf(255, packet.size - offset)
                    laces.add(length)
                    pageBody.write(packet, offset, length)
                    offset += length
                    if (length < 255) {
                        packetComplete = true
                        break
                    }
                }

                // A packet whose byte length is divisible by 255 needs a terminating
                // zero-length lacing segment. If this page is already full, that zero
                // segment is emitted as a tiny continued page on the next iteration.
                if (offset == packet.size && !packetComplete && laces.size < 255) {
                    laces.add(0)
                    packetComplete = true
                } else if (offset == packet.size && !packetComplete && laces.isEmpty()) {
                    laces.add(0)
                    packetComplete = true
                }

                val flags = (if (continued) 0x01 else 0) or
                    (if (bos && firstPage) 0x02 else 0)
                val pageGranule = if (packetComplete) granulePosition else -1L
                writePage(laces, pageBody.toByteArray(), flags, pageGranule)
                if (packetComplete) return
                continued = true
                firstPage = false
            }
        }

        fun finish() {
            if (lastPageOffset < 0L) throw IOException("Ogg stream has no pages")
            val savedPosition = output.filePointer
            output.seek(lastPageOffset)
            val header = ByteArray(27)
            output.readFully(header)
            if (!startsWithAscii(header, "OggS")) throw IOException("Final Ogg page could not be located")
            val segmentCount = header[26].toInt() and 0xFF
            val lacing = ByteArray(segmentCount)
            output.readFully(lacing)
            val bodyLength = lacing.sumOf { it.toInt() and 0xFF }
            val page = ByteArray(27 + segmentCount + bodyLength)
            output.seek(lastPageOffset)
            output.readFully(page)
            page[5] = (page[5].toInt() or 0x04).toByte() // EOS
            for (index in 22..25) page[index] = 0
            writeLittleEndian32(page, 22, oggCrc(page))
            output.seek(lastPageOffset)
            output.write(page)
            output.seek(maxOf(savedPosition, output.length()))
        }

        private fun writePage(
            laces: List<Int>,
            body: ByteArray,
            flags: Int,
            granulePosition: Long
        ) {
            if (laces.size > 255) throw IOException("Ogg page exceeds the lacing limit")
            val page = ByteArray(27 + laces.size + body.size)
            page[0] = 'O'.code.toByte()
            page[1] = 'g'.code.toByte()
            page[2] = 'g'.code.toByte()
            page[3] = 'S'.code.toByte()
            page[4] = 0
            page[5] = flags.toByte()
            writeLittleEndian64(page, 6, granulePosition)
            writeLittleEndian32(page, 14, serial)
            writeLittleEndian32(page, 18, sequence++)
            page[26] = laces.size.toByte()
            laces.forEachIndexed { index, value -> page[27 + index] = value.toByte() }
            System.arraycopy(body, 0, page, 27 + laces.size, body.size)
            writeLittleEndian32(page, 22, oggCrc(page))
            lastPageOffset = output.filePointer
            output.write(page)
        }
    }

    private fun oggCrc(page: ByteArray): Int {
        var crc = 0
        for (byte in page) {
            val index = ((crc ushr 24) xor (byte.toInt() and 0xFF)) and 0xFF
            crc = (crc shl 8) xor oggCrcTable[index]
        }
        return crc
    }

    private val oggCrcTable: IntArray by lazy {
        IntArray(256) { index ->
            var value = index shl 24
            repeat(8) {
                value = if ((value and 0x80000000.toInt()) != 0) {
                    (value shl 1) xor 0x04C11DB7
                } else {
                    value shl 1
                }
            }
            value
        }
    }

    private fun writeLittleEndian32(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
        bytes[offset + 2] = (value ushr 16).toByte()
        bytes[offset + 3] = (value ushr 24).toByte()
    }

    private fun writeLittleEndian64(bytes: ByteArray, offset: Int, value: Long) {
        for (index in 0 until 8) {
            bytes[offset + index] = (value ushr (8 * index)).toByte()
        }
    }
}
