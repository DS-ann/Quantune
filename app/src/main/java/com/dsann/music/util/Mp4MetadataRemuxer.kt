package com.dsann.music.util

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.security.MessageDigest

/**
 * Writes iTunes-style metadata atoms into MP4/M4A without decoding or re-encoding audio.
 * The moov atom is rebuilt, absolute sample offsets are relocated, and every mdat payload
 * is hashed before and after the rewrite.
 */
internal object Mp4MetadataRemuxer {
    private const val TAG = "Mp4MetadataRemuxer"
    private const val MAX_MOOV_BYTES = 128L * 1024L * 1024L
    private val LATIN1: Charset = Charsets.ISO_8859_1
    private val UTF8: Charset = Charsets.UTF_8

    internal data class Metadata(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val artwork: ByteArray? = null
    )

    private data class Atom(
        val offset: Long,
        val size: Long,
        val headerSize: Int,
        val type: String
    ) {
        val payloadOffset: Long get() = offset + headerSize
        val payloadSize: Long get() = size - headerSize
        val end: Long get() = offset + size
    }

    private val titleType = atomType(0xA9, 0x6E, 0x61, 0x6D) // ©nam
    private val artistType = atomType(0xA9, 0x41, 0x52, 0x54) // ©ART
    private val albumType = atomType(0xA9, 0x61, 0x6C, 0x62) // ©alb
    private val commentType = atomType(0xA9, 0x63, 0x6D, 0x74) // ©cmt
    private val albumArtistType = "aART"
    private val coverType = "covr"
    private val replacedItems = setOf(titleType, artistType, albumType, commentType, albumArtistType, coverType)
    private val containerTypes = setOf(
        "moov", "trak", "mdia", "minf", "stbl", "edts", "dinf",
        "mvex", "moof", "traf", "mfra", "udta", "meta"
    )

    @Throws(IOException::class)
    fun writeTaggedMp4(
        inputFile: File,
        outputFile: File,
        title: String,
        artist: String,
        album: String?,
        comment: String,
        artworkBytes: ByteArray?
    ) {
        if (!inputFile.isFile || inputFile.length() <= 0L) throw IOException("The M4A source file is missing or empty")
        if (inputFile.canonicalFile == outputFile.canonicalFile) {
            throw IOException("MP4 metadata must be written to a separate staging file")
        }
        if (title.isBlank() || artist.isBlank()) throw IOException("M4A title and artist are required")
        outputFile.parentFile?.let {
            if (!it.exists() && !it.mkdirs()) throw IOException("Could not create the M4A metadata staging directory")
        }
        if (outputFile.exists() && !outputFile.delete()) throw IOException("Could not clear the M4A metadata staging file")

        val originalAtoms = RandomAccessFile(inputFile, "r").use { readTopLevelAtoms(it) }
        val moovAtoms = originalAtoms.filter { it.type == "moov" }
        if (moovAtoms.size != 1) {
            throw IOException("Expected exactly one MP4 moov atom; found " + moovAtoms.size)
        }
        val mediaAtoms = originalAtoms.filter { it.type == "mdat" }
        if (mediaAtoms.isEmpty()) throw IOException("The MP4 file has no media-data atom")

        val originalMoov = moovAtoms.single()
        if (originalMoov.size > MAX_MOOV_BYTES) throw IOException("The MP4 moov atom is too large to rewrite safely")
        val originalMediaDigest = digestMediaPayloads(inputFile, originalAtoms)

        var rewrittenMoov = RandomAccessFile(inputFile, "r").use { source ->
            addMetadataToMoov(readAtomBytes(source, originalMoov, MAX_MOOV_BYTES), title, artist, album, comment, artworkBytes)
        }
        val delta = rewrittenMoov.size.toLong() - originalMoov.size
        val moovBeforeMedia = originalMoov.offset < mediaAtoms.first().offset
        if (delta != 0L && moovBeforeMedia) {
            rewrittenMoov = adjustAbsoluteOffsets(rewrittenMoov, originalMoov.end, delta)
        }

        RandomAccessFile(inputFile, "r").use { source ->
            RandomAccessFile(outputFile, "rw").use { target ->
                target.setLength(0L)
                val buffer = ByteArray(64 * 1024)
                originalAtoms.forEach { atom ->
                    when {
                        atom.type == "moov" -> target.write(rewrittenMoov)
                        delta != 0L && moovBeforeMedia && atom.type == "moof" -> {
                            val bytes = readAtomBytes(source, atom, MAX_MOOV_BYTES)
                            target.write(adjustAbsoluteOffsets(bytes, originalMoov.end, delta))
                        }
                        delta != 0L && moovBeforeMedia && atom.type == "sidx" -> {
                            val bytes = readAtomBytes(source, atom, 1024L * 1024L)
                            target.write(adjustSidx(bytes, atom.offset, originalMoov.end, delta))
                        }
                        else -> copyRange(source, atom.offset, atom.size, target, buffer)
                    }
                }
                target.fd.sync()
            }
        }

        val rewrittenAtoms = RandomAccessFile(outputFile, "r").use { readTopLevelAtoms(it) }
        if (!originalMediaDigest.contentEquals(digestMediaPayloads(outputFile, rewrittenAtoms))) {
            outputFile.delete()
            throw IOException("M4A audio payload changed during metadata writing")
        }
        val saved = readMetadata(outputFile)
            ?: throw IOException("The rewritten M4A file has no readable metadata")
        if (!saved.title.equals(title.trim(), ignoreCase = true) ||
            !saved.artist.equals(artist.trim(), ignoreCase = true)
        ) {
            outputFile.delete()
            throw IOException("M4A title or artist atom failed read-back verification")
        }
        if (!album.isNullOrBlank() && !saved.album.equals(album.trim(), ignoreCase = true)) {
            outputFile.delete()
            throw IOException("M4A album atom failed read-back verification")
        }
        if (artworkBytes != null && artworkBytes.isNotEmpty() && saved.artwork?.contentEquals(artworkBytes) != true) {
            outputFile.delete()
            throw IOException("M4A cover artwork atom failed read-back verification")
        }
        Log.d(TAG, "Tagged " + inputFile.name + " without changing its mdat payload")
    }

    fun readMetadata(file: File): Metadata? {
        if (!file.isFile || file.length() <= 0L) return null
        return try {
            RandomAccessFile(file, "r").use { input ->
                val moov = readTopLevelAtoms(input).firstOrNull { it.type == "moov" } ?: return null
                if (moov.size > MAX_MOOV_BYTES) return null
                findMetadataInMoov(readAtomBytes(input, moov, MAX_MOOV_BYTES))
            }
        } catch (error: Exception) {
            Log.w(TAG, "Could not read MP4 ilst metadata from " + file.name, error)
            null
        }
    }

    private fun addMetadataToMoov(
        moovBytes: ByteArray,
        title: String,
        artist: String,
        album: String?,
        comment: String,
        artworkBytes: ByteArray?
    ): ByteArray {
        val root = parseSingleAtom(moovBytes)
        if (root.type != "moov") throw IOException("Invalid MP4 moov atom")
        val payload = payloadOf(moovBytes, root)
        val children = parseAtoms(payload, 0, payload.size)
        val rewritten = ArrayList<ByteArray>(children.size + 1)
        var foundUdta = false
        var foundDirectMeta = false

        children.forEach { child ->
            val raw = rawAtom(payload, child)
            when (child.type) {
                "udta" -> {
                    foundUdta = true
                    rewritten += rewriteUdta(raw, title, artist, album, comment, artworkBytes)
                }
                "meta" -> {
                    foundDirectMeta = true
                    rewritten += rewriteMeta(raw, title, artist, album, comment, artworkBytes)
                }
                else -> rewritten += raw
            }
        }
        if (!foundUdta) {
            // Keep the commonly-used iTunes moov/udta/meta path even when a direct meta
            // atom already exists; media libraries vary in which location they inspect.
            rewritten += makeBox(
                "udta",
                rewriteOrCreateMeta(null, title, artist, album, comment, artworkBytes)
            )
        } else if (!foundDirectMeta) {
            // Existing udta was updated above.
        }
        return makeBox("moov", concatenate(rewritten))
    }

    private fun rewriteUdta(
        udtaBytes: ByteArray,
        title: String,
        artist: String,
        album: String?,
        comment: String,
        artworkBytes: ByteArray?
    ): ByteArray {
        val payload = payloadOf(udtaBytes, parseSingleAtom(udtaBytes))
        val children = parseAtoms(payload, 0, payload.size)
        val output = ArrayList<ByteArray>(children.size + 1)
        var metaFound = false
        children.forEach { child ->
            val raw = rawAtom(payload, child)
            if (child.type == "meta") {
                output += rewriteMeta(raw, title, artist, album, comment, artworkBytes)
                metaFound = true
            } else output += raw
        }
        if (!metaFound) output += rewriteOrCreateMeta(null, title, artist, album, comment, artworkBytes)
        return makeBox("udta", concatenate(output))
    }

    private fun rewriteMeta(
        metaBytes: ByteArray,
        title: String,
        artist: String,
        album: String?,
        comment: String,
        artworkBytes: ByteArray?
    ): ByteArray {
        val atom = parseSingleAtom(metaBytes)
        return rewriteOrCreateMeta(payloadOf(metaBytes, atom), title, artist, album, comment, artworkBytes)
    }

    private fun rewriteOrCreateMeta(
        originalPayload: ByteArray?,
        title: String,
        artist: String,
        album: String?,
        comment: String,
        artworkBytes: ByteArray?
    ): ByteArray {
        val versionFlags = if (originalPayload != null && originalPayload.size >= 4) {
            originalPayload.copyOfRange(0, 4)
        } else ByteArray(4)

        val children = if (originalPayload != null && originalPayload.size > 4) {
            parseAtoms(originalPayload, 4, originalPayload.size)
        } else emptyList()

        val output = ArrayList<ByteArray>(children.size + 2)
        val preservedItems = ArrayList<ByteArray>()
        var handlerFound = false
        children.forEach { child ->
            val raw = rawAtom(originalPayload!!, child)
            when (child.type) {
                "hdlr" -> {
                    output += raw
                    handlerFound = true
                }
                "ilst" -> {
                    val ilstPayload = payloadOf(raw, parseSingleAtom(raw))
                    parseAtoms(ilstPayload, 0, ilstPayload.size)
                        .filter { it.type !in replacedItems }
                        .forEach { preservedItems += rawAtom(ilstPayload, it) }
                }
                else -> output += raw
            }
        }
        if (!handlerFound) output.add(0, buildMetaHandler())
        output += buildIlst(preservedItems, title, artist, album, comment, artworkBytes)
        return makeBox("meta", versionFlags + concatenate(output))
    }

    private fun buildMetaHandler(): ByteArray {
        val payload = ByteArrayOutputStream()
        writeUInt32(payload, 0L)
        writeUInt32(payload, 0L)
        payload.write("mdir".toByteArray(LATIN1))
        repeat(3) { writeUInt32(payload, 0L) }
        payload.write("D.S Music".toByteArray(UTF8))
        payload.write(0)
        return makeBox("hdlr", payload.toByteArray())
    }

    private fun buildIlst(
        preservedItems: List<ByteArray>,
        title: String,
        artist: String,
        album: String?,
        comment: String,
        artworkBytes: ByteArray?
    ): ByteArray {
        val items = ArrayList<ByteArray>(preservedItems.size + 6)
        items.addAll(preservedItems)
        items += makeTextItem(titleType, title)
        items += makeTextItem(artistType, artist)
        items += makeTextItem(albumArtistType, artist)
        album?.takeIf { it.isNotBlank() }?.let { items += makeTextItem(albumType, it.trim()) }
        comment.takeIf { it.isNotBlank() }?.let { items += makeTextItem(commentType, it) }
        artworkBytes?.takeIf { it.isNotEmpty() }?.let { items += makeCoverItem(it) }
        return makeBox("ilst", concatenate(items))
    }

    private fun makeTextItem(type: String, text: String): ByteArray {
        val encoded = text.toByteArray(UTF8)
        val dataPayload = ByteArrayOutputStream(encoded.size + 8)
        writeUInt32(dataPayload, 1L)
        writeUInt32(dataPayload, 0L)
        dataPayload.write(encoded)
        return makeBox(type, makeBox("data", dataPayload.toByteArray()))
    }

    private fun makeCoverItem(bytes: ByteArray): ByteArray {
        val imageType = when {
            isJpeg(bytes) -> 13L
            isPng(bytes) -> 14L
            else -> throw IOException("M4A cover art must be JPEG or PNG")
        }
        val dataPayload = ByteArrayOutputStream(bytes.size + 8)
        writeUInt32(dataPayload, imageType)
        writeUInt32(dataPayload, 0L)
        dataPayload.write(bytes)
        return makeBox(coverType, makeBox("data", dataPayload.toByteArray()))
    }

    private fun findMetadataInMoov(moovBytes: ByteArray): Metadata? {
        val root = parseSingleAtom(moovBytes)
        if (root.type != "moov") return null
        val payload = payloadOf(moovBytes, root)
        val metaAtoms = ArrayList<ByteArray>()
        parseAtoms(payload, 0, payload.size).forEach { child ->
            when (child.type) {
                "meta" -> metaAtoms += rawAtom(payload, child)
                "udta" -> {
                    val udta = rawAtom(payload, child)
                    val udtaPayload = payloadOf(udta, parseSingleAtom(udta))
                    parseAtoms(udtaPayload, 0, udtaPayload.size)
                        .filter { it.type == "meta" }
                        .forEach { metaAtoms += rawAtom(udtaPayload, it) }
                }
            }
        }

        for (meta in metaAtoms) {
            val metaPayload = payloadOf(meta, parseSingleAtom(meta))
            if (metaPayload.size <= 4) continue
            val ilst = parseAtoms(metaPayload, 4, metaPayload.size).firstOrNull { it.type == "ilst" } ?: continue
            val ilstRaw = rawAtom(metaPayload, ilst)
            val ilstPayload = payloadOf(ilstRaw, parseSingleAtom(ilstRaw))
            var title: String? = null
            var artist: String? = null
            var album: String? = null
            var artwork: ByteArray? = null
            parseAtoms(ilstPayload, 0, ilstPayload.size).forEach { item ->
                val itemRaw = rawAtom(ilstPayload, item)
                val itemPayload = payloadOf(itemRaw, parseSingleAtom(itemRaw))
                val data = parseAtoms(itemPayload, 0, itemPayload.size).firstOrNull { it.type == "data" }
                    ?: return@forEach
                val dataRaw = rawAtom(itemPayload, data)
                val dataPayload = payloadOf(dataRaw, parseSingleAtom(dataRaw))
                if (dataPayload.size < 8) return@forEach
                val dataType = readUInt32(dataPayload, 0).toInt()
                val value = dataPayload.copyOfRange(8, dataPayload.size)
                when (item.type) {
                    titleType -> if (title == null) title = String(value, UTF8).trim().takeIf { it.isNotEmpty() }
                    artistType -> if (artist == null) artist = String(value, UTF8).trim().takeIf { it.isNotEmpty() }
                    albumType -> if (album == null) album = String(value, UTF8).trim().takeIf { it.isNotEmpty() }
                    coverType -> if (artwork == null && dataType in 13..14) {
                        artwork = value.takeIf { isJpeg(it) || isPng(it) }
                    }
                }
            }
            if (title != null || artist != null || album != null || artwork != null) {
                return Metadata(title, artist, album, artwork)
            }
        }
        return null
    }

    private fun adjustAbsoluteOffsets(atomBytes: ByteArray, oldMoovEnd: Long, delta: Long): ByteArray {
        val atom = parseSingleAtom(atomBytes)
        val payload = payloadOf(atomBytes, atom)
        val adjusted = when (atom.type) {
            "stco" -> adjustChunkOffsets(payload, oldMoovEnd, delta, false)
            "co64" -> adjustChunkOffsets(payload, oldMoovEnd, delta, true)
            "tfhd" -> adjustTrackFragmentHeader(payload, oldMoovEnd, delta)
            "tfra" -> adjustTrackFragmentRandomAccess(payload, oldMoovEnd, delta)
            in containerTypes -> {
                val prefixSize = if (atom.type == "meta") 4 else 0
                if (payload.size < prefixSize) throw IOException("Truncated MP4 " + atom.type + " container")
                val prefix = payload.copyOfRange(0, prefixSize)
                val children = parseAtoms(payload, prefixSize, payload.size)
                prefix + concatenate(children.map { adjustAbsoluteOffsets(rawAtom(payload, it), oldMoovEnd, delta) })
            }
            else -> return atomBytes
        }
        return makeBox(atom.type, adjusted)
    }

    private fun adjustChunkOffsets(payload: ByteArray, oldMoovEnd: Long, delta: Long, wide: Boolean): ByteArray {
        if (payload.size < 8) throw IOException("Truncated MP4 chunk-offset table")
        val count = readUInt32(payload, 4)
        val width = if (wide) 8 else 4
        if (count > 2_000_000L || 8L + count * width > payload.size.toLong()) {
            throw IOException("Invalid MP4 chunk-offset table length")
        }
        val output = payload.copyOf()
        repeat(count.toInt()) { index ->
            val offset = 8 + index * width
            val current = if (wide) readUInt64(output, offset) else readUInt32(output, offset)
            if (current >= oldMoovEnd) {
                val relocated = current + delta
                if (relocated < 0L) throw IOException("MP4 chunk offset became negative")
                if (wide) writeUInt64(output, offset, relocated)
                else {
                    if (relocated > 0xFFFFFFFFL) throw IOException("M4A stco overflow; refusing to alter audio")
                    writeUInt32(output, offset, relocated)
                }
            }
        }
        return output
    }

    private fun adjustTrackFragmentHeader(payload: ByteArray, oldMoovEnd: Long, delta: Long): ByteArray {
        if (payload.size < 8) return payload
        val flags = ((payload[1].toInt() and 0xFF) shl 16) or
            ((payload[2].toInt() and 0xFF) shl 8) or
            (payload[3].toInt() and 0xFF)
        if ((flags and 0x000001) == 0) return payload
        if (payload.size < 16) throw IOException("Truncated tfhd base-data-offset")
        val output = payload.copyOf()
        val current = readUInt64(output, 8)
        if (current >= oldMoovEnd) {
            val relocated = current + delta
            if (relocated < 0L) throw IOException("tfhd base-data-offset became negative")
            writeUInt64(output, 8, relocated)
        }
        return output
    }

    /**
     * Relocate absolute moof offsets in a Track Fragment Random Access atom.
     * This is nested in mfra for fragmented MP4/M4A files.
     */
    private fun adjustTrackFragmentRandomAccess(
        payload: ByteArray,
        oldMoovEnd: Long,
        delta: Long
    ): ByteArray {
        if (payload.size < 16) throw IOException("Truncated tfra atom")
        val version = payload[0].toInt() and 0xFF
        val timeWidth = if (version == 1) 8 else 4
        val offsetWidth = timeWidth
        val lengthFlags = payload[11].toInt() and 0xFF
        val trafWidth = ((lengthFlags ushr 4) and 0x03) + 1
        val trunWidth = ((lengthFlags ushr 2) and 0x03) + 1
        val sampleWidth = (lengthFlags and 0x03) + 1
        val entryCount = readUInt32(payload, 12)
        val entryWidth = timeWidth + offsetWidth + trafWidth + trunWidth + sampleWidth
        if (entryCount > 2_000_000L ||
            16L + entryCount * entryWidth > payload.size.toLong()
        ) {
            throw IOException("Invalid tfra entry table")
        }

        val output = payload.copyOf()
        repeat(entryCount.toInt()) { index ->
            val entryStart = 16 + index * entryWidth
            val moofOffsetPosition = entryStart + timeWidth
            val current = if (offsetWidth == 8) {
                readUInt64(output, moofOffsetPosition)
            } else {
                readUInt32(output, moofOffsetPosition)
            }
            if (current >= oldMoovEnd) {
                val relocated = current + delta
                if (relocated < 0L) throw IOException("tfra moof offset became negative")
                if (offsetWidth == 8) {
                    writeUInt64(output, moofOffsetPosition, relocated)
                } else {
                    if (relocated > 0xFFFFFFFFL) {
                        throw IOException("tfra moof offset overflow")
                    }
                    writeUInt32(output, moofOffsetPosition, relocated)
                }
            }
        }
        return output
    }

    private fun adjustSidx(atomBytes: ByteArray, atomOffset: Long, oldMoovEnd: Long, delta: Long): ByteArray {
        val atom = parseSingleAtom(atomBytes)
        if (atom.type != "sidx") return atomBytes
        val payload = payloadOf(atomBytes, atom)
        if (payload.size < 24) return atomBytes
        val version = payload[0].toInt() and 0xFF
        val position = if (version == 0) 16 else 20
        val width = if (version == 0) 4 else 8
        if (payload.size < position + width) return atomBytes
        val current = if (width == 4) readUInt32(payload, position) else readUInt64(payload, position)
        // When sidx itself is after moov, both the index and the referenced
        // media move by delta, so its relative first_offset must remain unchanged.
        if (atomOffset >= oldMoovEnd) return atomBytes
        val target = atomOffset + atom.size + current
        if (target < oldMoovEnd) return atomBytes
        val relocated = current + delta
        if (relocated < 0L || (width == 4 && relocated > 0xFFFFFFFFL)) {
            throw IOException("sidx first_offset overflow after moov relocation")
        }
        val output = payload.copyOf()
        if (width == 4) writeUInt32(output, position, relocated) else writeUInt64(output, position, relocated)
        return makeBox("sidx", output)
    }

    private fun digestMediaPayloads(file: File, atoms: List<Atom>): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        RandomAccessFile(file, "r").use { input ->
            atoms.filter { it.type == "mdat" }.forEach { atom ->
                updateDigestWithLong(digest, atom.payloadSize)
                input.seek(atom.payloadOffset)
                var remaining = atom.payloadSize
                while (remaining > 0L) {
                    val count = minOf(buffer.size.toLong(), remaining).toInt()
                    input.readFully(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    remaining -= count
                }
            }
        }
        return digest.digest()
    }

    private fun readTopLevelAtoms(input: RandomAccessFile): List<Atom> {
        val result = ArrayList<Atom>()
        var offset = 0L
        val length = input.length()
        while (offset < length) {
            if (length - offset < 8L) throw IOException("Trailing bytes after MP4 atoms")
            input.seek(offset)
            val size32 = readUInt32(input)
            val typeBytes = ByteArray(4)
            input.readFully(typeBytes)
            val type = String(typeBytes, LATIN1)
            var headerSize = 8
            val size = when (size32) {
                0L -> length - offset
                1L -> {
                    headerSize = 16
                    readUInt64(input)
                }
                else -> size32
            }
            if (type == "uuid") headerSize += 16
            if (size < headerSize || offset + size > length) throw IOException("Invalid MP4 atom " + type)
            result += Atom(offset, size, headerSize, type)
            offset += size
        }
        return result
    }

    private fun readAtomBytes(input: RandomAccessFile, atom: Atom, maxBytes: Long): ByteArray {
        if (atom.size > maxBytes || atom.size > Int.MAX_VALUE.toLong()) {
            throw IOException("MP4 atom " + atom.type + " exceeds the safe rewrite size")
        }
        val bytes = ByteArray(atom.size.toInt())
        input.seek(atom.offset)
        input.readFully(bytes)
        return bytes
    }

    private fun copyRange(
        source: RandomAccessFile,
        sourceOffset: Long,
        byteCount: Long,
        target: RandomAccessFile,
        buffer: ByteArray
    ) {
        source.seek(sourceOffset)
        var remaining = byteCount
        while (remaining > 0L) {
            val count = minOf(buffer.size.toLong(), remaining).toInt()
            source.readFully(buffer, 0, count)
            target.write(buffer, 0, count)
            remaining -= count
        }
    }

    private fun parseSingleAtom(bytes: ByteArray): Atom {
        val atoms = parseAtoms(bytes, 0, bytes.size)
        if (atoms.size != 1 || atoms[0].offset != 0L || atoms[0].size != bytes.size.toLong()) {
            throw IOException("Expected one complete MP4 atom")
        }
        return atoms.single()
    }

    private fun parseAtoms(bytes: ByteArray, start: Int, end: Int): List<Atom> {
        if (start < 0 || end < start || end > bytes.size) throw IOException("Invalid MP4 atom range")
        val result = ArrayList<Atom>()
        var offset = start
        while (offset < end) {
            if (end - offset < 8) throw IOException("Truncated MP4 child atom")
            val size32 = readUInt32(bytes, offset)
            val type = String(bytes, offset + 4, 4, LATIN1)
            var headerSize = 8
            val size = when (size32) {
                0L -> (end - offset).toLong()
                1L -> {
                    if (end - offset < 16) throw IOException("Truncated extended MP4 atom")
                    headerSize = 16
                    readUInt64(bytes, offset + 8)
                }
                else -> size32
            }
            if (type == "uuid") headerSize += 16
            if (size < headerSize || size > Int.MAX_VALUE || offset.toLong() + size > end.toLong()) {
                throw IOException("Invalid MP4 child atom " + type)
            }
            result += Atom(offset.toLong(), size, headerSize, type)
            offset += size.toInt()
        }
        return result
    }

    private fun rawAtom(parent: ByteArray, atom: Atom): ByteArray =
        parent.copyOfRange(atom.offset.toInt(), (atom.offset + atom.size).toInt())

    private fun payloadOf(atomBytes: ByteArray, atom: Atom): ByteArray =
        atomBytes.copyOfRange(atom.headerSize, atomBytes.size)

    private fun makeBox(type: String, payload: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(LATIN1)
        if (typeBytes.size != 4) throw IOException("MP4 atom type must contain four bytes")
        val size = payload.size.toLong() + 8L
        if (size > 0xFFFFFFFFL) throw IOException("MP4 atom is too large for a 32-bit size")
        val output = ByteArrayOutputStream(payload.size + 8)
        writeUInt32(output, size)
        output.write(typeBytes)
        output.write(payload)
        return output.toByteArray()
    }

    private fun concatenate(parts: List<ByteArray>): ByteArray {
        val size = parts.sumOf { it.size.toLong() }
        if (size > Int.MAX_VALUE) throw IOException("MP4 atom payload is too large")
        val output = ByteArrayOutputStream(size.toInt())
        parts.forEach { output.write(it) }
        return output.toByteArray()
    }

    private fun readUInt32(input: RandomAccessFile): Long =
        ((input.readUnsignedByte().toLong() shl 24) or
            (input.readUnsignedByte().toLong() shl 16) or
            (input.readUnsignedByte().toLong() shl 8) or
            input.readUnsignedByte().toLong())

    private fun readUInt64(input: RandomAccessFile): Long {
        val high = readUInt32(input)
        val low = readUInt32(input)
        if (high > 0x7FFFFFFFL) throw IOException("MP4 atom size exceeds supported signed range")
        return high * 4294967296L + low
    }

    private fun readUInt32(bytes: ByteArray, offset: Int): Long {
        if (offset < 0 || offset + 4 > bytes.size) throw IOException("Truncated MP4 integer")
        return ((bytes[offset].toLong() and 0xFFL) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFFL) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFFL) shl 8) or
            (bytes[offset + 3].toLong() and 0xFFL)
    }

    private fun readUInt64(bytes: ByteArray, offset: Int): Long {
        val high = readUInt32(bytes, offset)
        val low = readUInt32(bytes, offset + 4)
        if (high > 0x7FFFFFFFL) throw IOException("MP4 offset exceeds supported signed range")
        return high * 4294967296L + low
    }

    private fun writeUInt32(output: ByteArrayOutputStream, value: Long) {
        if (value !in 0L..0xFFFFFFFFL) throw IOException("MP4 32-bit value is out of range")
        output.write(((value ushr 24) and 0xFF).toInt())
        output.write(((value ushr 16) and 0xFF).toInt())
        output.write(((value ushr 8) and 0xFF).toInt())
        output.write((value and 0xFF).toInt())
    }

    private fun writeUInt32(bytes: ByteArray, offset: Int, value: Long) {
        if (value !in 0L..0xFFFFFFFFL || offset < 0 || offset + 4 > bytes.size) {
            throw IOException("MP4 32-bit field is out of range")
        }
        bytes[offset] = (value ushr 24).toByte()
        bytes[offset + 1] = (value ushr 16).toByte()
        bytes[offset + 2] = (value ushr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }

    private fun writeUInt64(bytes: ByteArray, offset: Int, value: Long) {
        if (value < 0L || offset < 0 || offset + 8 > bytes.size) throw IOException("MP4 64-bit field is out of range")
        writeUInt32(bytes, offset, value / 4294967296L)
        writeUInt32(bytes, offset + 4, value % 4294967296L)
    }

    private fun updateDigestWithLong(digest: MessageDigest, value: Long) {
        for (shift in 56 downTo 0 step 8) digest.update((value ushr shift).toByte())
    }

    private fun atomType(a: Int, b: Int, c: Int, d: Int): String =
        String(byteArrayOf(a.toByte(), b.toByte(), c.toByte(), d.toByte()), LATIN1)

    private fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()

    private fun isPng(bytes: ByteArray): Boolean =
        bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() &&
            bytes[4] == 0x0D.toByte() && bytes[5] == 0x0A.toByte() &&
            bytes[6] == 0x1A.toByte() && bytes[7] == 0x0A.toByte()
}