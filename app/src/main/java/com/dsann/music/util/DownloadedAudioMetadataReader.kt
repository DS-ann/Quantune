package com.dsann.music.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Reads the metadata inside downloaded files, not merely MediaStore's database columns.
 *
 * Android's MediaStore/MediaMetadataRetriever does not expose Ogg Opus Vorbis comments
 * consistently across versions. M4A metadata is read from the MP4 atoms and Opus metadata
 * is read directly from the Ogg OpusTags packet. A small cached image URI is returned so
 * D.S Music can render embedded covers even when MediaStore has no ALBUM_ID/ALBUM_ART row.
 */
internal data class EmbeddedDownloadedAudioMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val artworkUri: String?
)

internal object DownloadedAudioMetadataReader {
    private const val TAG = "DownloadedAudioMetadata"
    private const val MAX_COVER_BYTES = 8 * 1024 * 1024
    private const val MAX_OGG_TAG_PACKET_BYTES = 16 * 1024 * 1024

    private data class ParsedTags(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val artwork: ByteArray? = null
    )

    fun read(
        context: Context,
        filePath: String?,
        mediaUri: Uri,
        cacheKey: String
    ): EmbeddedDownloadedAudioMetadata? {
        var file = filePath?.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.isFile }
        var temporaryFile: File? = null
        try {
            if (file == null) {
                val extension = queryExtension(context, mediaUri)
                if (extension == "m4a" || extension == "mp4" || extension == "opus" || extension == "ogg") {
                    temporaryFile = materializeUri(context, mediaUri, extension)
                    file = temporaryFile
                }
            }

            val signature = file?.let { "${it.length()}-${it.lastModified()}" } ?: mediaUri.toString()
            val extension = file?.extension?.lowercase()
                ?: queryExtension(context, mediaUri)

            // Parse container-native tags first. This is essential on Android 6, where the
            // framework extractor may return no Opus tags/artwork even for a valid .opus file.
            val nativeTags = when (extension) {
                "m4a", "mp4", "m4b", "m4p" -> file?.let { readMp4Tags(it) }
                "opus", "ogg" -> file?.let { readOggOpusTags(it) }
                else -> null
            }

            // The platform extractor is a fallback only. Do not let it override a valid
            // embedded tag or make an otherwise valid file appear to have no metadata.
            val platformTags = if (
                nativeTags?.title.isNullOrBlank() ||
                nativeTags?.artist.isNullOrBlank() ||
                nativeTags?.album.isNullOrBlank() ||
                nativeTags?.artwork == null
            ) {
                readPlatformMetadata(context, file, mediaUri)
            } else null

            val title = usable(nativeTags?.title) ?: usable(platformTags?.title)
            val artist = usable(nativeTags?.artist) ?: usable(platformTags?.artist)
            val album = usable(nativeTags?.album) ?: usable(platformTags?.album)
            val artworkBytes = nativeTags?.artwork
                ?.takeIf { it.isNotEmpty() }
                ?: platformTags?.artwork?.takeIf { it.isNotEmpty() }
            val artworkUri = artworkBytes?.let {
                cacheArtwork(context, it, cacheKey, signature)
            }

            if (title == null && artist == null && album == null && artworkUri == null) return null
            return EmbeddedDownloadedAudioMetadata(title, artist, album, artworkUri)
        } catch (error: Exception) {
            Log.w(TAG, "Could not read embedded metadata for $cacheKey", error)
            return null
        } finally {
            temporaryFile?.delete()
        }
    }

    private fun readMp4Tags(file: File): ParsedTags? {
        // Prefer the same native atom reader used to verify M4A writes. Jaudiotagger
        // remains a compatibility fallback for older files tagged by previous builds.
        Mp4MetadataRemuxer.readMetadata(file)?.let { metadata ->
            return ParsedTags(
                usable(metadata.title),
                usable(metadata.artist),
                usable(metadata.album),
                metadata.artwork?.takeIf { it.isNotEmpty() }
            )
        }
        return try {
            val audioFile = org.jaudiotagger.audio.AudioFileIO.read(file)
            val tag = audioFile.tag ?: return null
            val mp4Tag = tag as? org.jaudiotagger.tag.mp4.Mp4Tag
            val title = usable(
                mp4Tag?.getFirst(org.jaudiotagger.tag.mp4.Mp4FieldKey.TITLE)
                    ?: tag.getFirst(org.jaudiotagger.tag.FieldKey.TITLE)
            )
            val artist = usable(
                mp4Tag?.getFirst(org.jaudiotagger.tag.mp4.Mp4FieldKey.ARTIST)
                    ?: tag.getFirst(org.jaudiotagger.tag.FieldKey.ARTIST)
            )
            val album = usable(
                mp4Tag?.getFirst(org.jaudiotagger.tag.mp4.Mp4FieldKey.ALBUM)
                    ?: tag.getFirst(org.jaudiotagger.tag.FieldKey.ALBUM)
            )
            val artwork = runCatching {
                tag.artworkList.firstOrNull()?.binaryData
            }.getOrNull()?.takeIf { it.isNotEmpty() }
            ParsedTags(title, artist, album, artwork)
        } catch (error: Exception) {
            Log.w(TAG, "jaudiotagger could not read MP4 atoms from ${file.name}", error)
            null
        }
    }

    private fun readOggOpusTags(file: File): ParsedTags? {
        return try {
            val packets = readFirstOggPackets(file, wantedPackets = 2)
            if (packets.size < 2 || !startsWithAscii(packets[0], "OpusHead") ||
                !startsWithAscii(packets[1], "OpusTags")
            ) {
                Log.w(TAG, "${file.name} is not an Ogg Opus file with OpusTags")
                return null
            }
            parseOpusTagsPacket(packets[1])
        } catch (error: Exception) {
            Log.w(TAG, "Could not parse OpusTags from ${file.name}", error)
            null
        }
    }

    private fun readFirstOggPackets(file: File, wantedPackets: Int): List<ByteArray> {
        val result = ArrayList<ByteArray>(wantedPackets)
        val partial = ByteArrayOutputStream()
        var logicalStreamSerial: Long? = null

        RandomAccessFile(file, "r").use { input ->
            while (input.filePointer < input.length() && result.size < wantedPackets) {
                val pageHeader = ByteArray(27)
                try {
                    input.readFully(pageHeader)
                } catch (error: Exception) {
                    throw IOException("Truncated Ogg page header", error)
                }
                if (!startsWithAscii(pageHeader, "OggS") || pageHeader[4].toInt() != 0) {
                    throw IOException("Invalid Ogg page header")
                }
                val serial = readLittleEndian32(pageHeader, 14).toLong() and 0xFFFFFFFFL
                if (logicalStreamSerial == null) logicalStreamSerial = serial
                if (logicalStreamSerial != serial) {
                    throw IOException("Chained or multiplexed Ogg streams are not supported")
                }

                val segmentCount = pageHeader[26].toInt() and 0xFF
                val lacing = ByteArray(segmentCount)
                input.readFully(lacing)
                val bodyLength = lacing.sumOf { it.toInt() and 0xFF }
                val body = ByteArray(bodyLength)
                input.readFully(body)

                var bodyOffset = 0
                for (lace in lacing) {
                    val length = lace.toInt() and 0xFF
                    if (partial.size().toLong() + length.toLong() > MAX_OGG_TAG_PACKET_BYTES) {
                        throw IOException("Ogg metadata packet exceeds the safe size limit")
                    }
                    if (length > 0) partial.write(body, bodyOffset, length)
                    bodyOffset += length

                    if (length < 255) {
                        result.add(partial.toByteArray())
                        partial.reset()
                        if (result.size >= wantedPackets) break
                    }
                }
            }
        }

        if (result.size < wantedPackets) {
            throw IOException("Ogg file ended before OpusTags could be read")
        }
        return result
    }

    private fun parseOpusTagsPacket(packet: ByteArray): ParsedTags {
        if (!startsWithAscii(packet, "OpusTags") || packet.size < 16) {
            throw IOException("Invalid OpusTags packet")
        }

        var offset = 8
        val vendorLength = readLittleEndianInt(packet, offset)
        offset += 4
        if (vendorLength < 0 || offset.toLong() + vendorLength.toLong() > packet.size.toLong()) {
            throw IOException("Invalid OpusTags vendor string length")
        }
        offset += vendorLength

        val commentCount = readLittleEndianInt(packet, offset)
        offset += 4
        if (commentCount < 0 || commentCount > 10000) {
            throw IOException("Invalid OpusTags comment count")
        }

        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var picture: ByteArray? = null

        repeat(commentCount) {
            val length = readLittleEndianInt(packet, offset)
            offset += 4
            if (length < 0 || offset.toLong() + length.toLong() > packet.size.toLong()) {
                throw IOException("Invalid OpusTags comment length")
            }
            val text = String(packet, offset, length, Charsets.UTF_8)
            offset += length

            val separator = text.indexOf('=')
            if (separator <= 0) return@repeat
            val key = text.substring(0, separator).trim().uppercase()
            val value = text.substring(separator + 1).trim()
            when (key) {
                "TITLE" -> if (title == null) title = value
                "ARTIST" -> if (artist == null) artist = value
                "ALBUM" -> if (album == null) album = value
                "METADATA_BLOCK_PICTURE" -> if (picture == null) {
                    picture = decodeFlacPicture(value)
                }
                "COVERART" -> if (picture == null) {
                    picture = decodeRawCoverArt(value)
                }
            }
        }

        if (offset != packet.size) {
            throw IOException("Unexpected trailing data in OpusTags packet")
        }
        return ParsedTags(usable(title), usable(artist), usable(album), picture)
    }

    private fun decodeFlacPicture(encoded: String): ByteArray? {
        if (encoded.isBlank() || encoded.length > (MAX_COVER_BYTES * 2)) return null
        return try {
            val block = Base64.decode(encoded, Base64.DEFAULT)
            var offset = 0
            readBigEndianInt(block, offset) // picture type
            offset += 4

            val mimeLength = readBigEndianInt(block, offset)
            offset += 4
            if (mimeLength < 0 || offset.toLong() + mimeLength.toLong() > block.size.toLong()) return null
            offset += mimeLength

            val descriptionLength = readBigEndianInt(block, offset)
            offset += 4
            if (descriptionLength < 0 || offset.toLong() + descriptionLength.toLong() > block.size.toLong()) return null
            offset += descriptionLength

            // width, height, color depth and indexed colors
            repeat(4) {
                readBigEndianInt(block, offset)
                offset += 4
            }

            val imageLength = readBigEndianInt(block, offset)
            offset += 4
            if (imageLength <= 0 || imageLength > MAX_COVER_BYTES ||
                offset.toLong() + imageLength.toLong() > block.size.toLong()
            ) return null
            block.copyOfRange(offset, offset + imageLength).takeIf(::isSupportedImage)
        } catch (error: Exception) {
            Log.w(TAG, "Could not decode METADATA_BLOCK_PICTURE", error)
            null
        }
    }

    private fun decodeRawCoverArt(encoded: String): ByteArray? {
        if (encoded.isBlank() || encoded.length > (MAX_COVER_BYTES * 2)) return null
        return runCatching {
            Base64.decode(encoded, Base64.DEFAULT).takeIf {
                it.size in 1..MAX_COVER_BYTES && isSupportedImage(it)
            }
        }.getOrNull()
    }

    private fun readPlatformMetadata(
        context: Context,
        file: File?,
        mediaUri: Uri
    ): ParsedTags? {
        val retriever = MediaMetadataRetriever()
        var configured = false
        try {
            if (file?.isFile == true) {
                configured = runCatching {
                    retriever.setDataSource(file.absolutePath)
                    true
                }.getOrDefault(false)
            }
            if (!configured) {
                configured = runCatching {
                    retriever.setDataSource(context.applicationContext, mediaUri)
                    true
                }.getOrDefault(false)
            }
            if (!configured) return null

            val title = usable(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE))
            val artist = usable(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST))
            val album = usable(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM))
            val artwork = runCatching { retriever.embeddedPicture }
                .getOrNull()?.takeIf { it.isNotEmpty() && isSupportedImage(it) }
            return ParsedTags(title, artist, album, artwork)
        } catch (error: Exception) {
            Log.d(TAG, "Platform metadata extractor could not read $mediaUri", error)
            return null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // Metadata already extracted remains usable.
            }
        }
    }

    private fun cacheArtwork(
        context: Context,
        bytes: ByteArray,
        cacheKey: String,
        signature: String
    ): String? {
        if (bytes.isEmpty() || bytes.size > MAX_COVER_BYTES || !isSupportedImage(bytes)) return null
        val extension = if (isJpeg(bytes)) "jpg" else "png"
        val safeKey = cacheKey.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val safeSignature = signature.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
        val directory = File(context.cacheDir, "downloaded-embedded-art")
        if (!directory.exists() && !directory.mkdirs()) return null

        val baseName = "$safeKey-$safeSignature"
        val target = File(directory, "$baseName.$extension")
        if (target.isFile && target.length() == bytes.size.toLong()) {
            return Uri.fromFile(target).toString()
        }

        val temporary = File(directory, "$baseName.$extension.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            if (target.exists() && !target.delete()) {
                throw IOException("Could not replace cached embedded cover")
            }
            if (!temporary.renameTo(target)) {
                throw IOException("Could not install cached embedded cover")
            }
            return Uri.fromFile(target).toString()
        } catch (error: Exception) {
            temporary.delete()
            Log.w(TAG, "Could not cache embedded cover for $cacheKey", error)
            return null
        }
    }

    private fun materializeUri(
        context: Context,
        uri: Uri,
        extension: String
    ): File? {
        val temp = try {
            File.createTempFile("ds-audio-tags-", ".$extension", context.cacheDir)
        } catch (error: Exception) {
            Log.w(TAG, "Could not create a temporary metadata input for $uri", error)
            return null
        }
        return try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Could not open downloaded audio URI")
            input.use { source ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        copied += read.toLong()
                        if (copied > 512L * 1024L * 1024L) {
                            throw IOException("Downloaded audio is too large to inspect safely")
                        }
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            }
            if (temp.length() <= 0L) throw IOException("Downloaded audio URI is empty")
            temp
        } catch (error: Exception) {
            temp.delete()
            Log.w(TAG, "Could not materialize $uri for tag reading", error)
            null
        }
    }

    private fun queryExtension(context: Context, uri: Uri): String {
        val name = try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        } catch (_: Exception) {
            null
        }.orEmpty()
        return name.substringAfterLast('.', "").lowercase()
    }

    private fun usable(value: String?): String? {
        val normalized = value?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
        if (normalized.isBlank()) return null
        if (normalized.equals("<unknown>", ignoreCase = true) ||
            normalized.equals("unknown", ignoreCase = true) ||
            normalized.equals("unknown title", ignoreCase = true) ||
            normalized.equals("unknown artist", ignoreCase = true) ||
            normalized.equals("unknown album", ignoreCase = true)
        ) return null
        return normalized
    }

    private fun isSupportedImage(bytes: ByteArray): Boolean = isJpeg(bytes) || (
        bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()
        )

    private fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()

    private fun startsWithAscii(bytes: ByteArray, value: String): Boolean {
        val expected = value.toByteArray(Charsets.US_ASCII)
        if (bytes.size < expected.size) return false
        return expected.indices.all { bytes[it] == expected[it] }
    }

    private fun readLittleEndian32(bytes: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 4 > bytes.size) throw IOException("Truncated Ogg integer")
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readLittleEndianInt(bytes: ByteArray, offset: Int): Int =
        readLittleEndian32(bytes, offset)

    private fun readBigEndianInt(bytes: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 4 > bytes.size) throw IOException("Truncated FLAC picture block")
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }
}
