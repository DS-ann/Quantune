package com.dsann.music.util

/**
 * Thin JNI wrapper around the LGPL LAME encoder. Input PCM is signed 16-bit interleaved audio.
 * The native library is loaded only when conversion is requested.
 */
internal class NativeMp3Encoder(
    sampleRate: Int,
    channels: Int,
    bitrateKbps: Int = 192
) : AutoCloseable {
    private var handle: Long

    init {
        handle = nativeCreate(sampleRate, channels, bitrateKbps)
        check(handle != 0L) { "Could not initialize the MP3 encoder" }
    }

    fun encode(interleavedPcm: ShortArray): ByteArray {
        check(handle != 0L) { "MP3 encoder is closed" }
        return nativeEncode(handle, interleavedPcm)
    }

    fun flush(): ByteArray {
        check(handle != 0L) { "MP3 encoder is closed" }
        return nativeFlush(handle)
    }

    override fun close() {
        val current = handle
        if (current != 0L) {
            handle = 0L
            nativeClose(current)
        }
    }

    private external fun nativeCreate(sampleRate: Int, channels: Int, bitrateKbps: Int): Long
    private external fun nativeEncode(handle: Long, pcm: ShortArray): ByteArray
    private external fun nativeFlush(handle: Long): ByteArray
    private external fun nativeClose(handle: Long)

    companion object {
        init {
            System.loadLibrary("dsmusic_mp3")
        }
    }
}
