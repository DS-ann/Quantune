package com.dsann.music.util

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import com.dsann.music.data.innertube.NewPipeStreamExtractor
import com.dsann.music.model.SongItem
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class Mp3ConversionResult(
    val success: Boolean,
    val message: String
)

/**
 * Offline Ogg Opus -> MP3 converter.
 * MediaCodec decodes Opus on-device; LAME encodes the PCM locally. The MP3 is tagged and
 * verified before it is copied into the D.S Music library.
 */
internal object OpusToMp3Converter {
    private const val TAG = "OpusToMp3"
    private const val MAX_COVER_BYTES = 8 * 1024 * 1024

    private val _convertingKeys = MutableStateFlow<Set<String>>(emptySet())
    val convertingKeys: StateFlow<Set<String>> = _convertingKeys.asStateFlow()

    /** Stable keys let every song-menu variant display the same conversion state. */
    fun conversionKeys(song: SongItem): Set<String> = buildSet {
        add("song:${song.id}")
        song.videoId?.takeIf { it.isNotBlank() }?.let { add("video:$it") }
    }

    suspend fun convert(context: Context, song: SongItem): Mp3ConversionResult {
        val keys = conversionKeys(song)
        if (keys.any { it in _convertingKeys.value }) {
            return Mp3ConversionResult(false, "MP3 conversion is already running")
        }
        _convertingKeys.update { it + keys }
        return try {
            convertInternal(context, song)
        } finally {
            _convertingKeys.update { it - keys }
        }
    }

    private suspend fun convertInternal(context: Context, song: SongItem): Mp3ConversionResult {
        val appContext = context.applicationContext
        // Use the exact download-quality preference that selected the source Opus stream.
        NewPipeStreamExtractor.init(appContext)
        val targetBitrateKbps = NewPipeStreamExtractor.getDownloadTargetBitrateKbps()
        var outputFile: File? = null
        var published = false
        try {
            val sourceUri = SongDownloader.findDownloadedOpusUri(appContext, song)
                ?: throw IllegalStateException("Download the Opus version first")

            val sourceMetadata = DownloadedAudioMetadataReader.read(
                appContext,
                sourceUri.path?.takeIf { sourceUri.scheme == "file" },
                sourceUri,
                song.videoId ?: song.id.toString()
            )
            val title = usable(sourceMetadata?.title) ?: usable(song.title) ?: "Unknown Title"
            val artist = usable(sourceMetadata?.artist) ?: usable(song.artist) ?: "Unknown Artist"
            val album = usable(sourceMetadata?.album) ?: usable(song.albumName)
            val coverBytes = sourceMetadata?.artworkUri?.let { rawUri ->
                readCoverBytes(appContext, Uri.parse(rawUri))
            }

            val baseName = "${SongDownloader.cleanTitle(title)} - ${SongDownloader.cleanArtist(artist)}"
                .replace(Regex("[^a-zA-Z0-9._\\s()\\[\\]-]"), "_")
                .trim()
                .ifBlank { "D.S Music export" }
            val outputName = "$baseName.mp3"
            if (SongDownloader.findDownloadedExportUri(appContext, outputName) != null) {
                return if (SongDownloader.deleteDownloadedOpusSource(appContext, song, sourceUri)) {
                    Mp3ConversionResult(true, "MP3 already exists; removed the redundant Opus original: $outputName")
                } else {
                    Mp3ConversionResult(
                        false,
                        "MP3 already exists, but the Opus original could not be deleted. Check storage access and retry."
                    )
                }
            }

            outputFile = AudioFileManager.createOutputFile(appContext, outputName)
            if (outputFile.exists() && !outputFile.delete()) {
                throw IllegalStateException("Could not replace the unfinished MP3 output")
            }

            decodeOpusAndEncodeMp3(appContext, sourceUri, outputFile, targetBitrateKbps)
            currentCoroutineContext().ensureActive()
            writeAndVerifyId3(
                file = outputFile,
                title = title,
                artist = artist,
                album = album,
                comment = "Converted from D.S Music Opus",
                artworkBytes = coverBytes
            )
            currentCoroutineContext().ensureActive()

            if (!AudioFileManager.scanFile(appContext, outputFile, title, artist, album)) {
                throw IllegalStateException("MP3 was encoded but could not be published to the music library")
            }
            if (SongDownloader.findDownloadedExportUri(appContext, outputName) == null) {
                throw IllegalStateException("MP3 was encoded but the music library could not verify it")
            }
            SongDownloader.rememberDownloadedExport(appContext, song, outputFile)
            // The MP3 is now fully written, tagged, published and verified. Only at this
            // point is it safe to remove the original Opus download.
            published = true
            if (!SongDownloader.deleteDownloadedOpusSource(appContext, song, sourceUri)) {
                throw IllegalStateException(
                    "MP3 was saved, but the original Opus file could not be deleted. " +
                        "Check storage access and retry from the three-dot menu."
                )
            }
            return Mp3ConversionResult(
                true,
                "Saved ${targetBitrateKbps} kbps MP3 with embedded metadata; deleted the Opus original: $outputName"
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Opus to MP3 conversion failed for ${song.title}", error)
            return Mp3ConversionResult(
                success = false,
                message = error.message?.takeIf { it.isNotBlank() } ?: "MP3 conversion failed"
            )
        } finally {
            val stagedFile = outputFile
            if (stagedFile != null && (!published ||
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
            ) {
                runCatching { stagedFile.delete() }
            }
        }
    }

    private suspend fun decodeOpusAndEncodeMp3(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        targetBitrateKbps: Int
    ) {
        try {
            decodeWithMediaExtractor(context, sourceUri, outputFile, targetBitrateKbps)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (platformError: Exception) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                throw platformError
            }
            Log.w(
                TAG,
                "Platform Ogg Opus extraction failed on this legacy device; trying packet-level fallback",
                platformError
            )
            if (outputFile.exists() && !outputFile.delete()) {
                throw IllegalStateException("Could not clear the incomplete MP3 before retrying the Opus decoder")
            }
            decodeLegacyOggOpusAndEncodeMp3(context, sourceUri, outputFile, targetBitrateKbps)
        }
    }

    /** Prefer Android's native Ogg Opus extractor and decoder on every supported API level. */
    private suspend fun decodeWithMediaExtractor(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        targetBitrateKbps: Int
    ) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: NativeMp3Encoder? = null
        var encoderRate = 0
        var encoderChannels = 0
        var audioSamplesWritten = 0L

        try {
            if (sourceUri.scheme == "file") {
                extractor.setDataSource(sourceUri.path ?: throw IllegalStateException("Opus file path is empty"))
            } else {
                extractor.setDataSource(context, sourceUri, null)
            }

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (trackIndex in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(trackIndex)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.contains("opus", ignoreCase = true)) {
                    audioTrackIndex = trackIndex
                    audioFormat = format
                    break
                }
            }
            val selectedFormat = audioFormat
                ?: throw IllegalStateException("The downloaded file is not a decodable Opus audio stream")
            val mime = selectedFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalStateException("Opus decoder MIME type is missing")
            extractor.selectTrack(audioTrackIndex)
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(selectedFormat, null, null, 0)
            decoder.start()

            val fallbackRate = selectedFormat.integerOr(MediaFormat.KEY_SAMPLE_RATE, 48000)
            val fallbackChannels = selectedFormat.integerOr(MediaFormat.KEY_CHANNEL_COUNT, 2)
            if (fallbackChannels !in 1..2) {
                throw IllegalStateException("Only mono and stereo Opus tracks can be converted")
            }

            FileOutputStream(outputFile).buffered().use { output ->
                fun configureEncoder(format: MediaFormat) {
                    val sampleRate = format.integerOr(MediaFormat.KEY_SAMPLE_RATE, fallbackRate)
                    val channels = format.integerOr(MediaFormat.KEY_CHANNEL_COUNT, fallbackChannels)
                    if (channels !in 1..2) {
                        throw IllegalStateException("The Opus decoder returned more than two audio channels")
                    }
                    if (encoder == null || sampleRate != encoderRate || channels != encoderChannels) {
                        encoder?.let { old ->
                            old.flush().takeIf { it.isNotEmpty() }?.let(output::write)
                            old.close()
                        }
                        encoder = NativeMp3Encoder(sampleRate, channels, targetBitrateKbps)
                        encoderRate = sampleRate
                        encoderChannels = channels
                    }
                }

                val bufferInfo = MediaCodec.BufferInfo()
                var inputEnded = false
                var outputEnded = false
                while (!outputEnded) {
                    currentCoroutineContext().ensureActive()

                    if (!inputEnded) {
                        val inputIndex = decoder.dequeueInputBuffer(10_000L)
                        if (inputIndex >= 0) {
                            val inputBuffer = decoder.getInputBuffer(inputIndex)
                                ?: throw IllegalStateException("Opus decoder input buffer is unavailable")
                            inputBuffer.clear()
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(
                                    inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputEnded = true
                            } else {
                                val presentationTimeUs = extractor.sampleTime.coerceAtLeast(0L)
                                decoder.queueInputBuffer(inputIndex, 0, sampleSize, presentationTimeUs, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                    when {
                        outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            configureEncoder(decoder.outputFormat)
                        }
                        outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED ||
                            outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                        outputIndex >= 0 -> {
                            if (bufferInfo.size > 0) {
                                if (encoder == null) configureEncoder(decoder.outputFormat)
                                val pcmBuffer = decoder.getOutputBuffer(outputIndex)
                                    ?: throw IllegalStateException("Opus decoder output buffer is unavailable")
                                val view = pcmBuffer.duplicate().order(ByteOrder.nativeOrder())
                                view.position(bufferInfo.offset)
                                view.limit(bufferInfo.offset + bufferInfo.size)
                                val samplesPerChannel = view.remaining() / (2 * encoderChannels)
                                if (samplesPerChannel > 0) {
                                    val pcm = ShortArray(samplesPerChannel * encoderChannels)
                                    view.asShortBuffer().get(pcm)
                                    val encoded = encoder?.encode(pcm)
                                        ?: throw IllegalStateException("MP3 encoder was not initialized")
                                    if (encoded.isNotEmpty()) output.write(encoded)
                                    audioSamplesWritten += samplesPerChannel.toLong()
                                }
                            }

                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                outputEnded = true
                            }
                            decoder.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                }

                if (encoder == null || audioSamplesWritten <= 0L) {
                    throw IllegalStateException("The Opus decoder returned no audio samples")
                }
                encoder.flush().takeIf { it.isNotEmpty() }?.let(output::write)
                output.flush()
            }

            if (!outputFile.isFile || outputFile.length() <= 1024L) {
                throw IllegalStateException("The MP3 encoder produced an incomplete file")
            }
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { extractor.release() }
            runCatching { encoder?.close() }
        }
    }

    private suspend fun decodeLegacyOggOpusAndEncodeMp3(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        targetBitrateKbps: Int
    ) {
        val input = when (sourceUri.scheme) {
            "file" -> FileInputStream(
                sourceUri.path ?: throw IllegalStateException("Opus file path is empty")
            )
            "content" -> context.contentResolver.openInputStream(sourceUri)
                ?: throw IllegalStateException("Could not open the downloaded Opus file")
            else -> throw IllegalStateException("Unsupported Opus source URI")
        }

        var decoder: MediaCodec? = null
        var encoder: NativeMp3Encoder? = null
        var decodedFrames = 0L
        var queuedSamples = 0L
        var writtenSamples = 0L
        var preSkipSamples = 0L
        var finalGranulePosition = -1L
        var configuredChannels = 0
        var configuredSampleRate = 0
        var outputEnded = false

        try {
            OggOpusPacketReader(input).use { reader ->
                FileOutputStream(outputFile).buffered().use { output ->
                    fun configurePcmEncoder(format: MediaFormat) {
                        val sampleRate = format.integerOr(MediaFormat.KEY_SAMPLE_RATE, 48000)
                        val channels = format.integerOr(MediaFormat.KEY_CHANNEL_COUNT, configuredChannels)
                        val pcmEncoding = runCatching {
                            if (format.containsKey("pcm-encoding")) format.getInteger("pcm-encoding") else 2
                        }.getOrDefault(2)
                        if (pcmEncoding != 2) {
                            throw IllegalStateException("The Opus decoder did not output 16-bit PCM")
                        }
                        if (sampleRate != 48000) {
                            throw IllegalStateException("The Opus decoder returned an unsupported sample rate: $sampleRate Hz")
                        }
                        if (channels !in 1..2) {
                            throw IllegalStateException("Only mono and stereo Opus tracks can be converted")
                        }
                        if (encoder == null || sampleRate != configuredSampleRate || channels != configuredChannels) {
                            encoder?.let { old ->
                                old.flush().takeIf { it.isNotEmpty() }?.let(output::write)
                                old.close()
                            }
                            encoder = NativeMp3Encoder(sampleRate, channels, targetBitrateKbps)
                            configuredSampleRate = sampleRate
                            configuredChannels = channels
                        }
                    }

                    fun drainDecoder(waitUs: Long): Boolean {
                        val activeDecoder = decoder
                            ?: throw IllegalStateException("The Opus decoder was not initialized")
                        val info = MediaCodec.BufferInfo()
                        while (true) {
                            val outputIndex = activeDecoder.dequeueOutputBuffer(info, waitUs)
                            when {
                                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                    configurePcmEncoder(activeDecoder.outputFormat)
                                }
                                outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> return false
                                outputIndex >= 0 -> {
                                    try {
                                        if (info.size > 0 &&
                                            (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                                        ) {
                                            val outputFormat = activeDecoder.outputFormat
                                            if (encoder == null) configurePcmEncoder(outputFormat)
                                            val pcmEncoder = encoder
                                                ?: throw IllegalStateException("The MP3 encoder was not initialized")
                                            val channels = configuredChannels
                                            val pcmBuffer = activeDecoder.getOutputBuffer(outputIndex)
                                                ?: throw IllegalStateException("The Opus decoder output buffer is unavailable")
                                            val frameBytes = channels * 2
                                            if (info.size % frameBytes != 0) {
                                                throw IllegalStateException("The Opus decoder returned a misaligned PCM buffer")
                                            }
                                            val frameCount = info.size / frameBytes
                                            val absoluteStart = decodedFrames
                                            val absoluteEnd = absoluteStart + frameCount
                                            val keepStart = maxOf(absoluteStart, preSkipSamples)
                                            val keepEnd = if (finalGranulePosition >= 0L) {
                                                minOf(absoluteEnd, finalGranulePosition)
                                            } else {
                                                absoluteEnd
                                            }

                                            if (keepEnd > keepStart) {
                                                val startByte = info.offset +
                                                    ((keepStart - absoluteStart) * frameBytes).toInt()
                                                val byteCount = ((keepEnd - keepStart) * frameBytes).toInt()
                                                val view = pcmBuffer.duplicate().order(ByteOrder.nativeOrder())
                                                view.position(startByte)
                                                view.limit(startByte + byteCount)
                                                val pcm = ShortArray(
                                                    ((keepEnd - keepStart) * channels).toInt()
                                                )
                                                view.slice().order(ByteOrder.nativeOrder()).asShortBuffer().get(pcm)
                                                val encoded = pcmEncoder.encode(pcm)
                                                if (encoded.isNotEmpty()) output.write(encoded)
                                                writtenSamples += (keepEnd - keepStart)
                                            }
                                            decodedFrames = absoluteEnd
                                        }
                                    } finally {
                                        activeDecoder.releaseOutputBuffer(outputIndex, false)
                                    }
                                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                        return true
                                    }
                                }
                            }
                        }
                    }

                    suspend fun queueOpusPacket(packet: ByteArray) {
                        val activeDecoder = decoder
                            ?: throw IllegalStateException("The Opus decoder was not initialized")
                        val durationSamples = opusPacketDurationSamples(packet)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val inputIndex = activeDecoder.dequeueInputBuffer(10_000L)
                            if (inputIndex >= 0) {
                                val inputBuffer = activeDecoder.getInputBuffer(inputIndex)
                                    ?: throw IllegalStateException("The Opus decoder input buffer is unavailable")
                                if (packet.size > inputBuffer.capacity()) {
                                    throw IllegalStateException("An Opus audio packet exceeds the decoder input buffer")
                                }
                                inputBuffer.clear()
                                inputBuffer.put(packet)
                                val presentationTimeUs = queuedSamples * 1_000_000L / 48000L
                                activeDecoder.queueInputBuffer(
                                    inputIndex, 0, packet.size, presentationTimeUs, 0
                                )
                                queuedSamples += durationSamples
                                break
                            }
                            drainDecoder(0L)
                        }
                        drainDecoder(0L)
                    }

                    var opusHead: ByteArray? = null
                    var tagsSeen = false
                    var audioPacketsSeen = 0
                    var eosSeen = false

                    while (!eosSeen) {
                        currentCoroutineContext().ensureActive()
                        val page = reader.nextPage()
                            ?: throw IllegalStateException("The Ogg Opus file ended before its final page")
                        if (page.isEndOfStream) {
                            if (page.granulePosition < 0L) {
                                throw IllegalStateException("The final Ogg Opus page has no valid audio duration")
                            }
                            finalGranulePosition = page.granulePosition
                        }

                        for (packet in page.packets) {
                            currentCoroutineContext().ensureActive()
                            when {
                                opusHead == null -> {
                                    if (packet.size < 19 ||
                                        !packet.copyOfRange(0, 8).contentEquals("OpusHead".toByteArray(Charsets.US_ASCII))
                                    ) {
                                        throw IllegalStateException("The downloaded file does not contain an Opus identification header")
                                    }
                                    val version = packet[8].toInt() and 0xFF
                                    val channels = packet[9].toInt() and 0xFF
                                    if (version > 15 || channels !in 1..2) {
                                        throw IllegalStateException("This Opus channel layout is not supported for MP3 conversion")
                                    }
                                    val mappingFamily = packet[18].toInt() and 0xFF
                                    if (mappingFamily != 0 && mappingFamily != 1) {
                                        throw IllegalStateException("This Opus channel mapping is not supported for MP3 conversion")
                                    }
                                    if (mappingFamily == 1 && packet.size < 21 + channels) {
                                        throw IllegalStateException("The Opus channel mapping header is incomplete")
                                    }

                                    preSkipSamples =
                                        (packet[10].toLong() and 0xFFL) or
                                            ((packet[11].toLong() and 0xFFL) shl 8)
                                    val decoderHead = packet.copyOf()
                                    // We trim the pre-skip against decoded PCM below, so override the
                                    // header's pre-skip with zero to avoid dropping the samples twice.
                                    decoderHead[10] = 0
                                    decoderHead[11] = 0
                                    val decoderFormat = MediaFormat.createAudioFormat(
                                        "audio/opus", 48000, channels
                                    )
                                    decoderFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
                                    decoderFormat.setByteBuffer("csd-0", java.nio.ByteBuffer.wrap(decoderHead))
                                    decoderFormat.setByteBuffer("csd-1", longCodecData(0L))
                                    decoderFormat.setByteBuffer("csd-2", longCodecData(80_000_000L))
                                    decoder = MediaCodec.createDecoderByType("audio/opus").also { codec ->
                                        codec.configure(decoderFormat, null, null, 0)
                                        codec.start()
                                    }
                                    configuredChannels = channels
                                    opusHead = packet
                                }
                                !tagsSeen -> {
                                    if (packet.size < 8 ||
                                        !packet.copyOfRange(0, 8).contentEquals("OpusTags".toByteArray(Charsets.US_ASCII))
                                    ) {
                                        throw IllegalStateException("The Opus metadata header is missing or invalid")
                                    }
                                    tagsSeen = true
                                }
                                else -> {
                                    queueOpusPacket(packet)
                                    audioPacketsSeen++
                                }
                            }
                        }

                        eosSeen = page.isEndOfStream
                    }

                    if (decoder == null || !tagsSeen || audioPacketsSeen == 0) {
                        throw IllegalStateException("The Ogg Opus file contains no decodable audio packets")
                    }
                    if (finalGranulePosition <= preSkipSamples) {
                        throw IllegalStateException("The Ogg Opus file has an invalid final audio duration")
                    }

                    val activeDecoder = decoder
                        ?: throw IllegalStateException("The Opus decoder was not initialized")
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val inputIndex = activeDecoder.dequeueInputBuffer(10_000L)
                        if (inputIndex >= 0) {
                            activeDecoder.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                queuedSamples * 1_000_000L / 48000L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            break
                        }
                        drainDecoder(0L)
                    }

                    var drainWaits = 0
                    while (!outputEnded) {
                        currentCoroutineContext().ensureActive()
                        outputEnded = drainDecoder(10_000L)
                        if (!outputEnded) {
                            drainWaits++
                            if (drainWaits > 3000) {
                                throw IllegalStateException("The Opus decoder timed out before finishing the track")
                            }
                        }
                    }

                    val finalEncoder = encoder
                        ?: throw IllegalStateException("The MP3 encoder received no decoded audio")
                    finalEncoder.flush().takeIf { it.isNotEmpty() }?.let(output::write)
                    output.flush()
                }
            }

            if (writtenSamples <= 0L || !outputFile.isFile || outputFile.length() <= 1024L) {
                throw IllegalStateException("The Opus decoder returned no usable audio samples")
            }
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { encoder?.close() }
        }
    }

    private fun opusPacketDurationSamples(packet: ByteArray): Long {
        if (packet.isEmpty()) throw IllegalStateException("The Ogg Opus stream contains an empty audio packet")
        val toc = packet[0].toInt() and 0xFF
        val config = toc ushr 3
        val samplesPerFrame = when {
            config >= 16 -> 120 shl (config and 0x03)
            config >= 12 -> 480 shl (config and 0x01)
            else -> 480 shl (config and 0x03)
        }
        val frameCount = when (toc and 0x03) {
            0 -> 1
            1, 2 -> 2
            else -> {
                if (packet.size < 2) throw IllegalStateException("The Opus packet duration header is truncated")
                packet[1].toInt() and 0x3F
            }
        }
        val totalSamples = samplesPerFrame.toLong() * frameCount.toLong()
        if (frameCount <= 0 || totalSamples > 5760L) {
            throw IllegalStateException("The Opus packet has an invalid duration")
        }
        return totalSamples
    }

    private fun longCodecData(value: Long): java.nio.ByteBuffer =
        java.nio.ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).apply {
            putLong(value)
            flip()
        }

    private fun writeAndVerifyId3(
        file: File,
        title: String,
        artist: String,
        album: String?,
        comment: String,
        artworkBytes: ByteArray?
    ) {
        val audioFile = org.jaudiotagger.audio.AudioFileIO.read(file)
        val tag = org.jaudiotagger.tag.id3.ID3v23Tag()
        audioFile.setTag(tag)
        tag.setField(org.jaudiotagger.tag.FieldKey.TITLE, title)
        tag.setField(org.jaudiotagger.tag.FieldKey.ARTIST, artist)
        runCatching { tag.setField(org.jaudiotagger.tag.FieldKey.ALBUM_ARTIST, artist) }
        album?.let { tag.setField(org.jaudiotagger.tag.FieldKey.ALBUM, it) }
        tag.setField(org.jaudiotagger.tag.FieldKey.COMMENT, comment)

        artworkBytes?.let { bytes ->
            val artwork = org.jaudiotagger.tag.images.StandardArtwork()
            artwork.binaryData = bytes
            artwork.mimeType = if (isJpeg(bytes)) "image/jpeg" else "image/png"
            artwork.description = "Front cover"
            artwork.pictureType = 3
            tag.addField(artwork)
        }

        audioFile.commit()
        if (!file.isFile || file.length() <= 1024L) {
            throw IllegalStateException("ID3 metadata writing produced an invalid MP3 file")
        }

        val writtenTag = org.jaudiotagger.audio.AudioFileIO.read(file).tag
            ?: throw IllegalStateException("The MP3 has no readable ID3 tag after writing")
        if (!writtenTag.getFirst(org.jaudiotagger.tag.FieldKey.TITLE).equals(title, ignoreCase = true) ||
            !writtenTag.getFirst(org.jaudiotagger.tag.FieldKey.ARTIST).equals(artist, ignoreCase = true)
        ) {
            throw IllegalStateException("The MP3 title or artist tag could not be verified")
        }
        if (!album.isNullOrBlank() &&
            !writtenTag.getFirst(org.jaudiotagger.tag.FieldKey.ALBUM).equals(album, ignoreCase = true)
        ) {
            throw IllegalStateException("The MP3 album tag could not be verified")
        }
        val expectedArtwork = artworkBytes
        if (expectedArtwork != null && expectedArtwork.isNotEmpty()) {
            val persisted = writtenTag.artworkList.any { artwork ->
                runCatching { artwork.binaryData?.contentEquals(expectedArtwork) == true }.getOrDefault(false)
            }
            if (!persisted) throw IllegalStateException("The MP3 cover artwork could not be verified")
        }
    }

    private fun readCoverBytes(context: Context, uri: Uri): ByteArray {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_COVER_BYTES) {
                    throw IllegalStateException("Embedded cover artwork exceeds the safe size limit")
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: throw IllegalStateException("Could not read the embedded Opus cover artwork")
        if (bytes.isEmpty() || !(isJpeg(bytes) || isPng(bytes))) {
            throw IllegalStateException("The embedded Opus cover artwork is invalid")
        }
        return bytes
    }

    private fun MediaFormat.integerOr(key: String, fallback: Int): Int =
        runCatching { if (containsKey(key)) getInteger(key) else fallback }.getOrDefault(fallback)

    private fun usable(value: String?): String? {
        val normalized = value?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
        return normalized.takeIf {
            it.isNotBlank() && !it.equals("unknown", true) &&
                !it.equals("<unknown>", true) && !it.equals("unknown title", true) &&
                !it.equals("unknown artist", true) && !it.equals("unknown album", true)
        }
    }

    private fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()

    private fun isPng(bytes: ByteArray): Boolean =
        bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
}
