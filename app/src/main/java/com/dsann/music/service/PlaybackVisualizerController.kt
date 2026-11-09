package com.dsann.music.service

import android.media.audiofx.Visualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Lazy playback-session FFT capture for the full-screen visualizer.
 *
 * This uses Android's Visualizer effect on Quantune's dedicated ExoPlayer audio session.
 * It does not open the microphone or alter the Media3 audio-renderer chain.
 */
@Suppress("DEPRECATION")
class PlaybackVisualizerController {

    companion object {
        const val BAR_COUNT = 16
        private const val TARGET_CAPTURE_RATE_MILLI_HZ = 30_000
    }

    private val _levels = MutableStateFlow(FloatArray(BAR_COUNT) { 0f })
    val levels: StateFlow<FloatArray> = _levels.asStateFlow()

    @Volatile
    private var running = false
    private var capture: Visualizer? = null

    @Synchronized
    fun start(audioSessionId: Int): Boolean {
        stop()
        if (audioSessionId <= 0) return false

        val candidate = try {
            Visualizer(audioSessionId)
        } catch (_: Exception) {
            return false
        }

        return try {
            val range = Visualizer.getCaptureSizeRange()
            if (range.size < 2 || range[0] <= 0 || range[1] < range[0]) {
                candidate.release()
                return false
            }

            val requestedSize = minOf(1024, range[1])
            val powerOfTwoSize = Integer.highestOneBit(requestedSize)
                .coerceAtLeast(range[0])
            if (candidate.setCaptureSize(powerOfTwoSize) != Visualizer.SUCCESS) {
                candidate.release()
                return false
            }

            candidate.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED)
            val captureRate = minOf(
                TARGET_CAPTURE_RATE_MILLI_HZ,
                Visualizer.getMaxCaptureRate()
            ).coerceAtLeast(1)

            val listener = object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(
                    visualizer: Visualizer?,
                    waveform: ByteArray?,
                    samplingRate: Int
                ) = Unit

                override fun onFftDataCapture(
                    visualizer: Visualizer?,
                    fft: ByteArray?,
                    samplingRate: Int
                ) {
                    if (!running || fft == null || fft.isEmpty()) return
                    publishBands(fft)
                }
            }

            if (candidate.setDataCaptureListener(listener, captureRate, false, true) != Visualizer.SUCCESS) {
                candidate.release()
                return false
            }

            capture = candidate
            running = true
            if (candidate.setEnabled(true) != Visualizer.SUCCESS) {
                running = false
                capture = null
                candidate.release()
                return false
            }
            true
        } catch (_: Exception) {
            running = false
            capture = null
            runCatching { candidate.setEnabled(false) }
            runCatching { candidate.release() }
            _levels.value = FloatArray(BAR_COUNT) { 0f }
            false
        }
    }

    /** Rebind to the new primary player after a crossfade handoff, if capture is active. */
    @Synchronized
    fun switchSession(audioSessionId: Int) {
        if (!running || audioSessionId <= 0) return
        start(audioSessionId)
    }

    @Synchronized
    fun stop() {
        running = false
        val oldCapture = capture
        capture = null
        if (oldCapture != null) {
            runCatching { oldCapture.setEnabled(false) }
            runCatching { oldCapture.setDataCaptureListener(null, 0, false, false) }
            runCatching { oldCapture.release() }
        }
        _levels.value = FloatArray(BAR_COUNT) { 0f }
    }

    private fun publishBands(fft: ByteArray) {
        val pointCount = fft.size / 2
        if (pointCount <= 2) return

        val firstBin = 1
        val lastBin = (pointCount - 1).coerceAtLeast(firstBin + 1)
        val logRange = ln(lastBin.toFloat() / firstBin.toFloat())
        val oldLevels = _levels.value
        val newLevels = FloatArray(BAR_COUNT)

        for (bar in 0 until BAR_COUNT) {
            val startFraction = bar.toFloat() / BAR_COUNT
            val endFraction = (bar + 1).toFloat() / BAR_COUNT
            val startBin = max(
                firstBin,
                (firstBin * kotlin.math.exp(logRange * startFraction)).toInt()
            )
            val endBin = max(
                startBin + 1,
                (firstBin * kotlin.math.exp(logRange * endFraction)).toInt()
            ).coerceAtMost(lastBin + 1)

            var peak = 0f
            for (bin in startBin until endBin) {
                val offset = bin * 2
                if (offset + 1 >= fft.size) break
                val real = fft[offset].toInt()
                val imaginary = fft[offset + 1].toInt()
                val magnitude = sqrt(
                    (real * real + imaginary * imaginary).toFloat()
                )
                if (magnitude > peak) peak = magnitude
            }

            // The platform FFT is signed 8-bit complex data. A mild compression keeps
            // quieter tracks visible without manufacturing motion when the audio is silent.
            val target = ((peak - 2f).coerceAtLeast(0f) / 92f)
                .coerceIn(0f, 1f)
                .pow(0.72f)
            val previous = oldLevels.getOrElse(bar) { 0f }
            newLevels[bar] = if (target >= previous) {
                previous * 0.22f + target * 0.78f
            } else {
                previous * 0.68f + target * 0.32f
            }
        }

        if (running) _levels.value = newLevels
    }
}
