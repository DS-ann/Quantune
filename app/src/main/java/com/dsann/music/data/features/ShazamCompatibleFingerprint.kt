package com.dsann.music.data.features

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin

/**
 * Pure Kotlin audio landmark encoder. It consumes mono, signed 16-bit little-endian
 * PCM at 16 kHz and returns the compact signature URI accepted by the matching API.
 * No native library or ABI-specific binary is required.
 */
internal object ShazamCompatibleFingerprint {
    private const val INPUT_RATE = 16_000
    private const val WINDOW = 2_048
    private const val SPECTRUM_SIZE = WINDOW / 2 + 1
    private const val HOP = 128
    private const val HISTORY = 256
    private const val PEAK_LIMIT = 255
    private const val MAX_SECONDS = 12.0

    private val windowFunction = DoubleArray(WINDOW) { index ->
        0.5 * (1.0 - cos(2.0 * PI * (index + 1) / (WINDOW + 1.0)))
    }
    private val earlierFrameOffsets = intArrayOf(-1, -3, -6)
    private val comparisonOffsets = intArrayOf(
        -53, -45, 165, 172, 179, 186, 193, 200, 214, 221, 228, 235, 242, 249
    )
    private val neighbourBins = intArrayOf(-10, -7, -4, -3, 1, 2, 5, 8)

    fun create(pcmBytes: ByteArray): String {
        require(pcmBytes.size >= 2 && pcmBytes.size % 2 == 0) {
            "Fingerprint input must contain complete 16-bit PCM samples."
        }
        val pcm = ShortArray(pcmBytes.size / 2)
        ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm)
        return Encoder().consume(pcm)
    }

    private class Encoder {
        private val sampleHistory = IntArray(WINDOW)
        private var sampleHead = 0
        private val spectra = Array(HISTORY) { DoubleArray(SPECTRUM_SIZE) }
        private var spectrumHead = 0
        private val spreadHistory = Array(HISTORY) { DoubleArray(SPECTRUM_SIZE) }
        private var spreadHead = 0
        private var spreadFrames = 0
        private var samplesProcessed = 0
        private var peaksFound = 0
        private val peaksByBand = Array(4) { ArrayList<Landmark>() }
        private val real = DoubleArray(WINDOW)
        private val imaginary = DoubleArray(WINDOW)
        private val spreadScratch = DoubleArray(SPECTRUM_SIZE)

        fun consume(samples: ShortArray): String {
            var offset = 0
            while (offset + HOP <= samples.size) {
                val elapsed = samplesProcessed.toDouble() / INPUT_RATE
                if (elapsed >= MAX_SECONDS && peaksFound >= PEAK_LIMIT) break

                var next = offset
                repeat(HOP) {
                    sampleHistory[sampleHead] = samples[next++].toInt()
                    sampleHead = (sampleHead + 1) % WINDOW
                }
                samplesProcessed += HOP
                evaluateSpectrum()
                spreadAndCollect()
                offset += HOP
            }
            return encode()
        }

        private fun evaluateSpectrum() {
            for (index in 0 until WINDOW) {
                val sample = sampleHistory[(sampleHead + index) % WINDOW]
                real[index] = sample * windowFunction[index]
                imaginary[index] = 0.0
            }
            transformInPlace()

            val destination = spectra[spectrumHead]
            for (bin in 0 until SPECTRUM_SIZE) {
                val magnitude = (real[bin] * real[bin] + imaginary[bin] * imaginary[bin]) / 131_072.0
                destination[bin] = if (magnitude < 1.0e-10) 1.0e-10 else magnitude
            }
            spectrumHead = (spectrumHead + 1) % HISTORY
        }

        private fun transformInPlace() {
            var reversed = 0
            for (index in 1 until WINDOW) {
                var bit = WINDOW shr 1
                while ((reversed and bit) != 0) {
                    reversed = reversed xor bit
                    bit = bit shr 1
                }
                reversed = reversed xor bit
                if (index < reversed) {
                    var temp = real[index]
                    real[index] = real[reversed]
                    real[reversed] = temp
                    temp = imaginary[index]
                    imaginary[index] = imaginary[reversed]
                    imaginary[reversed] = temp
                }
            }

            var span = 2
            while (span <= WINDOW) {
                val half = span shr 1
                val angle = -PI / half
                val baseReal = cos(angle)
                val baseImaginary = sin(angle)
                var group = 0
                while (group < WINDOW) {
                    var weightReal = 1.0
                    var weightImaginary = 0.0
                    for (step in 0 until half) {
                        val even = group + step
                        val odd = even + half
                        val oddReal = real[odd] * weightReal - imaginary[odd] * weightImaginary
                        val oddImaginary = real[odd] * weightImaginary + imaginary[odd] * weightReal
                        val evenReal = real[even]
                        val evenImaginary = imaginary[even]
                        real[even] = evenReal + oddReal
                        imaginary[even] = evenImaginary + oddImaginary
                        real[odd] = evenReal - oddReal
                        imaginary[odd] = evenImaginary - oddImaginary

                        val nextReal = weightReal * baseReal - weightImaginary * baseImaginary
                        weightImaginary = weightReal * baseImaginary + weightImaginary * baseReal
                        weightReal = nextReal
                    }
                    group += span
                }
                span = span shl 1
            }
        }

        private fun spreadAndCollect() {
            val mostRecent = spectra[(spectrumHead - 1 + HISTORY) % HISTORY]
            mostRecent.copyInto(spreadScratch)
            for (bin in 0 until SPECTRUM_SIZE - 2) {
                spreadScratch[bin] = max(
                    spreadScratch[bin],
                    max(spreadScratch[bin + 1], spreadScratch[bin + 2])
                )
            }

            for (bin in 0 until SPECTRUM_SIZE) {
                var strongest = spreadScratch[bin]
                for (offset in earlierFrameOffsets) {
                    val slot = wrap(spreadHead + offset)
                    val old = spreadHistory[slot][bin]
                    if (old > strongest) strongest = old
                    spreadHistory[slot][bin] = strongest
                }
            }

            spreadScratch.copyInto(spreadHistory[spreadHead])
            spreadHead = (spreadHead + 1) % HISTORY
            spreadFrames++
            if (spreadFrames >= 47) collectPeaks()
        }

        private fun collectPeaks() {
            val frameSpectrum = spectra[wrap(spectrumHead - 46)]
            val frameSpread = spreadHistory[wrap(spreadHead - 49)]

            for (bin in 10 until SPECTRUM_SIZE - 8) {
                val energy = frameSpectrum[bin]
                if (energy < (1.0 / 64.0) || energy < frameSpread[bin]) continue

                var localMaximum = 0.0
                for (offset in neighbourBins) {
                    localMaximum = max(localMaximum, frameSpread[bin + offset])
                }
                if (energy <= localMaximum) continue

                var temporalMaximum = localMaximum
                for (offset in comparisonOffsets) {
                    temporalMaximum = max(
                        temporalMaximum,
                        spreadHistory[wrap(spreadHead + offset)][bin - 1]
                    )
                }
                if (energy <= temporalMaximum) continue

                val magnitude = ln(max(1.0 / 64.0, energy)) * 1477.3 + 6144.0
                val lower = ln(max(1.0 / 64.0, frameSpectrum[bin - 1])) * 1477.3 + 6144.0
                val upper = ln(max(1.0 / 64.0, frameSpectrum[bin + 1])) * 1477.3 + 6144.0
                val curve = magnitude * 2.0 - lower - upper
                val fractionalBin = if (kotlin.math.abs(curve) < 1.0e-9) {
                    0.0
                } else {
                    (upper - lower) * 32.0 / curve
                }
                val preciseBin = bin * 64.0 + fractionalBin
                val frequency = preciseBin * (INPUT_RATE / 2.0 / 1024.0 / 64.0)
                val band = when {
                    frequency < 250.0 -> -1
                    frequency < 520.0 -> 0
                    frequency < 1_450.0 -> 1
                    frequency < 3_500.0 -> 2
                    frequency <= 5_500.0 -> 3
                    else -> -1
                }

                if (band != -1) {
                    peaksByBand[band].add(
                        Landmark(
                            frame = spreadFrames - 46,
                            magnitude = magnitude.toInt(),
                            frequencyBin = preciseBin.toInt()
                        )
                    )
                    peaksFound++
                }
            }
        }

        private fun encode(): String {
            val payload = ByteArrayOutputStream()
            for (band in 0..3) {
                val peaks = peaksByBand[band]
                if (peaks.isEmpty()) continue

                val encoded = ByteArrayOutputStream()
                var previousFrame = 0
                for (peak in peaks) {
                    val gap = peak.frame - previousFrame
                    if (gap >= 255) {
                        encoded.write(0xFF)
                        writeInt(encoded, peak.frame)
                        previousFrame = peak.frame
                    }
                    encoded.write(peak.frame - previousFrame)
                    writeShort(encoded, peak.magnitude)
                    writeShort(encoded, peak.frequencyBin)
                    previousFrame = peak.frame
                }

                val bandData = encoded.toByteArray()
                writeInt(payload, 0x60030040 + band)
                writeInt(payload, bandData.size)
                payload.write(bandData)
                repeat((4 - bandData.size % 4) % 4) { payload.write(0) }
            }

            val sections = payload.toByteArray()
            val bodySize = sections.size + 8
            val sampleCount = samplesProcessed + (INPUT_RATE * 0.24).toInt()
            val header = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(0xCAFE2580.toInt())
                putInt(0)
                putInt(bodySize)
                putInt(0x94119C00.toInt())
                putInt(0)
                putInt(0)
                putInt(0)
                putInt(3 shl 27)
                putInt(0)
                putInt(0)
                putInt(sampleCount)
                putInt((15 shl 19) + 0x40000)
            }.array()

            val full = ByteArrayOutputStream(56 + sections.size)
            full.write(header)
            writeInt(full, 0x40000000)
            writeInt(full, bodySize)
            full.write(sections)

            val bytes = full.toByteArray()
            val crc = CRC32().apply { update(bytes, 8, bytes.size - 8) }.value.toInt()
            for (position in 0..3) {
                bytes[4 + position] = (crc ushr (position * 8)).toByte()
            }

            return "data:audio/vnd.shazam.sig;base64," +
                Base64.encodeToString(bytes, Base64.NO_WRAP)
        }

        private fun wrap(index: Int): Int = ((index % HISTORY) + HISTORY) % HISTORY
    }

    private data class Landmark(
        val frame: Int,
        val magnitude: Int,
        val frequencyBin: Int
    )

    private fun writeInt(output: ByteArrayOutputStream, value: Int) {
        output.write(value and 0xFF)
        output.write((value ushr 8) and 0xFF)
        output.write((value ushr 16) and 0xFF)
        output.write((value ushr 24) and 0xFF)
    }

    private fun writeShort(output: ByteArrayOutputStream, value: Int) {
        output.write(value and 0xFF)
        output.write((value ushr 8) and 0xFF)
    }
}
